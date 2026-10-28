package dev.drivetrace.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import dev.drivetrace.telemetry.Engine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Owned by Engine; the visible activity starts and stops the subscription. No Play Services required. */
class PhoneLocation(private val app: Application) {
    private val manager = app.getSystemService(LocationManager::class.java)
    private val mutableStatus = MutableStateFlow("GPS выключен")
    val status = mutableStatus.asStateFlow()
    private var latest: GpsFix? = null
    private var provider: String? = null
    private var tracking = false
    private var failure: String? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if(!tracking || !location.hasAccuracy()) return
            val fix = GpsFix(location.latitude, location.longitude, location.accuracy, location.time, location.elapsedRealtimeNanos / 1_000_000)
            if(fix.usable(SystemClock.elapsedRealtime()) && (latest == null || fix.elapsedRealtimeMs >= latest!!.elapsedRealtimeMs)) {
                latest = fix; provider = location.provider
            }
            refresh()
        }
        override fun onProviderEnabled(provider: String) { refresh() }
        override fun onProviderDisabled(provider: String) { refresh() }
        @Deprecated("Required for Android 8–10")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    fun hasPrecisePermission() = app.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun enabled(name: String) = runCatching { manager?.isProviderEnabled(name) == true }.getOrDefault(false)

    @SuppressLint("MissingPermission")
    fun start() {
        stop()
        tracking = true
        if(!hasPrecisePermission()) { refresh(); return }
        val providers = manager?.allProviders.orEmpty().filter { it == LocationManager.GPS_PROVIDER || it == LocationManager.NETWORK_PROVIDER }
        var registered = false
        providers.forEach { name ->
            runCatching { manager!!.requestLocationUpdates(name, 1000L, 0f, listener, Looper.getMainLooper()) }
                .onSuccess { registered = true }
        }
        if(!registered) failure = "Не удалось запустить геолокацию телефона"
        refresh()
    }
    fun currentFix(): GpsFix? {
        if(!tracking || !hasPrecisePermission() || provider?.let(::enabled) != true) return null
        return latest?.takeIf { it.usable(SystemClock.elapsedRealtime()) }
    }
    fun refresh() {
        val current = currentFix()
        mutableStatus.value = when {
            !tracking -> "GPS приостановлен"
            !hasPrecisePermission() -> "Для маршрута разрешите точную геолокацию"
            manager == null -> "Геолокация недоступна на этом устройстве"
            !enabled(LocationManager.GPS_PROVIDER) && !enabled(LocationManager.NETWORK_PROVIDER) -> "Включите геолокацию в настройках телефона"
            failure != null -> failure!!
            current != null -> "GPS · точность ±%.0f м".format(current.accuracyM)
            else -> "Ожидание точного сигнала · маршрут пока не записывается"
        }
    }
    fun stop() {
        tracking = false
        runCatching { manager?.removeUpdates(listener) }
        latest = null; provider = null; failure = null
        mutableStatus.value = "GPS приостановлен"
    }
}
