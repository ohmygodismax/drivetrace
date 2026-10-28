package dev.drivetrace.data.export

import dev.drivetrace.model.Frame

fun csv(frames: List<Frame>, vin: String? = null): String {
    val header = "timestamp_utc,elapsed_ms,source,simulated,vin,speed_kmh,rpm,coolant_c,intake_c,voltage_v,throttle_pct,distance_km,latitude,longitude,engine_load_pct,oil_c,manifold_kpa,baro_kpa,fuel_lh,stft_pct,ltft_pct,timing_deg,maf_gs,location_accuracy_m,location_timestamp_utc"
    return header + "\n" + frames.joinToString("\n") { f -> listOf(java.time.Instant.ofEpochMilli(f.timestamp), f.elapsed, if(f.simulated) "SIMULATOR" else "ELM327", f.simulated, vin ?: "", f.speed, f.rpm, f.coolant, f.intake, f.voltage, f.throttle, f.distance, f.latitude.takeIf { f.hasLocation }, f.longitude.takeIf { f.hasLocation }, f.engineLoad, f.oilC, f.manifoldKpa, f.baroKpa, f.fuelLh, f.stft, f.ltft, f.timing, f.maf, f.locationAccuracyM.takeIf { f.hasLocation }, f.locationTimestamp?.takeIf { f.hasLocation }?.let(java.time.Instant::ofEpochMilli)).joinToString(",") { it?.toString() ?: "" } }
}
