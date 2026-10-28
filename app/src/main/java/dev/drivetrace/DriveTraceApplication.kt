package dev.drivetrace

import android.app.Application
import dev.drivetrace.telemetry.Engine

class DriveTraceApplication: Application() {
    lateinit var engine: Engine
    override fun onCreate() { super.onCreate(); engine = Engine(this) }
}
