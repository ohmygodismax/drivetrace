package dev.drivetrace.data.obd

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import dev.drivetrace.model.Frame
import dev.drivetrace.model.ObdMetric
import dev.drivetrace.model.SensorReading
import java.io.Closeable
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface ElmTransport: Closeable { suspend fun command(command: String, timeoutMs: Long = 4000): String }
@SuppressLint("MissingPermission")
class ClassicTransport(private val socket: BluetoothSocket): ElmTransport {
    private val mutex = Mutex()
    override suspend fun command(command: String, timeoutMs: Long): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            socket.outputStream.write((command + "\r").toByteArray(Charsets.US_ASCII)); socket.outputStream.flush()
            withTimeoutOrNull(timeoutMs) {
                val response = StringBuilder()
                while(true) {
                    if(socket.inputStream.available() == 0) { delay(10); continue }
                    val c = socket.inputStream.read(); check(c >= 0) { "Адаптер закрыл соединение" }
                    if(c == '>'.code) break
                    response.append(c.toChar()); check(response.length < 65536) { "Некорректный ответ адаптера" }
                }
                response.toString()
            } ?: throw java.io.IOException("Таймаут ответа на $command")
        }
    }
    override fun close() { runCatching { socket.close() } }
}
data class ElmIdentity(val vin: String?, val metrics: Set<ObdMetric>, val codes: List<String>, val freeze: String)
class ElmSession(private val transport: ElmTransport, private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }): Closeable {
    private val readings = mutableMapOf<ObdMetric, SensorReading>()
    fun sensorReadings(): Map<ObdMetric, SensorReading> = readings.toMap()
    private suspend fun read(metric: ObdMetric) {
        val value = ObdCodec.value(transport.command("01%02X".format(metric.pid)),metric)
        readings[metric] = SensorReading(value, clock())
    }
    private var slowIndex = 0
    lateinit var identity: ElmIdentity
    suspend fun discover(progress: (String) -> Unit): ElmIdentity {
        progress("Инициализация ELM327")
        transport.command("ATZ",6000)
        listOf("ATE0","ATL0","ATS1","ATH0","ATSP0","ATCAF1").forEach { transport.command(it) }
        progress("Определение параметров ЭБУ")
        val supported = mutableSetOf<Int>()
        for(base in listOf(0x00,0x20,0x40)) {
            if(base != 0 && base !in supported) break
            supported += ObdCodec.supported(transport.command("01%02X".format(base),if(base==0)18000 else 4000),base)
        }
        val metrics = ObdMetric.entries.filter { it.pid in supported }.toSet()
        check(metrics.isNotEmpty()) { "ЭБУ не вернул поддерживаемые OBD-II параметры. Проверьте зажигание и адаптер." }
        metrics.sortedBy { it.pid }.forEachIndexed { index, metric ->
            progress("Проверка датчиков ${index + 1}/${metrics.size} · ${metric.title}")
            read(metric)
        }
        progress("Чтение VIN")
        val vin = ObdCodec.vin(transport.command("0902",8000))
        progress("Чтение ошибок и стоп-кадра")
        val protocol = transport.command("ATDPN").trim().lastOrNull()
        val dtcRaw = transport.command("03",8000)
        val codes = if(ObdCodec.payload(dtcRaw,0x43)==null) listOf("Чтение DTC недоступно") else ObdCodec.dtcs(dtcRaw, protocol in listOf('6','7','8','9'))
        val freeze = if(codes.isEmpty() || codes.any { !it.matches(Regex("[PCBU][0-3][0-9A-F]{3}")) }) "" else listOf(ObdMetric.RPM,ObdMetric.SPEED,ObdMetric.COOLANT,ObdMetric.LOAD).mapNotNull { metric ->
            ObdCodec.value(transport.command("02%02X00".format(metric.pid)),metric,true)?.let { "%s: %.1f %s".format(metric.title,it,metric.unit) }
        }.joinToString("\n")
        return ElmIdentity(vin,metrics,codes,freeze).also { identity = it }
    }
    suspend fun poll(elapsed: Long, started: Long, distance: Double): Frame {
        val fast = listOf(ObdMetric.RPM,ObdMetric.SPEED,ObdMetric.LOAD,ObdMetric.THROTTLE).filter { it in identity.metrics }
        val slow = (identity.metrics - fast.toSet()).sortedBy { it.pid }
        val selected = fast + if(slow.isEmpty()) emptyList() else listOf(slow[slowIndex++ % slow.size])
        for(metric in selected) read(metric)
        val now = clock()
        fun v(m: ObdMetric) = readings[m]?.takeIf { it.isFresh(now) }?.value
        return Frame(elapsed = elapsed,timestamp = started + elapsed, speed = v(ObdMetric.SPEED),rpm = v(ObdMetric.RPM)?.toInt(),coolant = v(ObdMetric.COOLANT),intake = v(ObdMetric.INTAKE),voltage = v(ObdMetric.VOLTAGE),throttle = v(ObdMetric.THROTTLE),distance = distance,latitude = 0.0,longitude = 0.0,simulated = false,hasLocation = false,
            engineLoad = v(ObdMetric.LOAD),oilC = v(ObdMetric.OIL),manifoldKpa = v(ObdMetric.MAP),baroKpa = v(ObdMetric.BARO),fuelLh = v(ObdMetric.FUEL_RATE),stft = v(ObdMetric.STFT),ltft = v(ObdMetric.LTFT),timing = v(ObdMetric.TIMING),maf = v(ObdMetric.MAF))
    }
    override fun close() = transport.close()
}
data class AdapterChoice(val name: String,val address: String)
@SuppressLint("MissingPermission")
fun pairedAdapters(app: Application): List<AdapterChoice> {
    val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: error("Bluetooth недоступен")
    check(adapter.isEnabled) { "Включите Bluetooth в настройках Android" }
    return adapter.bondedDevices.map { AdapterChoice(it.name ?: "Bluetooth-адаптер",it.address) }.sortedBy { it.name }
}
@SuppressLint("MissingPermission")
fun classicSocket(app: Application,address: String): BluetoothSocket {
    val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: error("Bluetooth недоступен")
    return adapter.getRemoteDevice(address).createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"))
}
