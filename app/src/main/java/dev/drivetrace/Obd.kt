package dev.drivetrace

/** Pure protocol parsing: no Bluetooth or Android dependencies. */
object ObdCodec {
    fun messages(raw: String): List<List<Int>> {
        val groups = linkedMapOf<String, MutableList<Int>>()
        var anonymous = 0
        raw.uppercase().replace("SEARCHING...", "").split('\r', '\n', '>').forEach { original ->
            var line = original.trim()
            var key = "default-$anonymous"
            val header = Regex("^([0-9A-F]{3}|[0-9A-F]{8})\\s+").find(line)
            if(header != null) { key = header.groupValues[1]; line = line.substring(header.value.length) }
            line = line.replace(Regex("^[0-9A-F]+:\\s*"), "")
            val compact = line.replace(" ", "")
            if(compact.length < 2 || compact.length % 2 != 0 || !Regex("[0-9A-F]+").matches(compact)) return@forEach
            var bytes = compact.chunked(2).map { it.toInt(16) }
            if(bytes.first() == 0x10 && bytes.size > 2) { groups[key] = mutableListOf(); bytes = bytes.drop(2) }
            else if(bytes.first() in 0x20..0x2F && groups.containsKey(key)) bytes = bytes.drop(1)
            else if(bytes.first() in 1..8 && bytes.getOrNull(1) in listOf(0x41,0x42,0x43,0x49)) bytes = bytes.drop(1)
            if(header == null && bytes.take(2) == listOf(0x49,0x02) && !groups[key].isNullOrEmpty()) { anonymous++; key = "default-$anonymous" }
            groups.getOrPut(key) { mutableListOf() }.addAll(bytes)
        }
        return groups.values.toList()
    }
    fun payload(raw: String, service: Int, pid: Int? = null): List<Int>? = messages(raw).firstNotNullOfOrNull { bytes ->
        val i = bytes.indices.firstOrNull { bytes[it] == service && (pid == null || bytes.getOrNull(it + 1) == pid) }
        i?.let { bytes.drop(it + if(pid == null) 1 else 2) }
    }
    fun vin(raw: String): String? = messages(raw).firstNotNullOfOrNull { bytes ->
        val i = bytes.indices.firstOrNull { bytes[it] == 0x49 && bytes.getOrNull(it + 1) == 0x02 } ?: return@firstNotNullOfOrNull null
        val text = bytes.drop(i + 3).filter { it != 0 }.take(17).map { it.toChar() }.joinToString("")
        text.takeIf(::validVin)
    }
    fun supported(raw: String, base: Int): Set<Int> {
        val b = payload(raw,0x41,base)?.take(4) ?: return emptySet()
        if(b.size != 4) return emptySet()
        val mask = b.fold(0L) { a,v -> (a shl 8) or v.toLong() }
        return (1..32).filter { mask and (1L shl (32-it)) != 0L }.map { base + it }.toSet()
    }
    fun value(raw: String, metric: ObdMetric, freeze: Boolean = false): Double? {
        var bytes = payload(raw, if(freeze) 0x42 else 0x41,metric.pid) ?: return null
        if(freeze) bytes = bytes.drop(1)
        val a = bytes.getOrNull(0) ?: return null; val b = bytes.getOrNull(1)
        return when(metric) {
            ObdMetric.RPM -> b?.let { (a*256+it)/4.0 }
            ObdMetric.VOLTAGE -> b?.let { (a*256+it)/1000.0 }
            ObdMetric.MAF -> b?.let { (a*256+it)/100.0 }
            ObdMetric.FUEL_RATE -> b?.let { (a*256+it)*0.05 }
            ObdMetric.LOAD, ObdMetric.THROTTLE -> a*100.0/255
            ObdMetric.COOLANT, ObdMetric.INTAKE, ObdMetric.OIL -> a-40.0
            ObdMetric.STFT, ObdMetric.LTFT -> (a-128)*100.0/128
            ObdMetric.TIMING -> a/2.0-64
            else -> a.toDouble()
        }
    }
    fun dtcs(raw: String, can: Boolean = false): List<String> = messages(raw).flatMap { bytes ->
        val start = bytes.indexOf(0x43); if(start < 0) return@flatMap emptyList()
        val body = bytes.drop(start + 1)
        val data = if(can) body.drop(1).take((body.firstOrNull() ?: 0)*2) else body
        data.chunked(2).mapNotNull { pair ->
            if(pair.size != 2 || pair.all { it == 0 }) null else "%s%d%X%02X".format("PCBU"[pair[0] shr 6], (pair[0] shr 4) and 3, pair[0] and 15, pair[1])
        }
    }.distinct()
}
