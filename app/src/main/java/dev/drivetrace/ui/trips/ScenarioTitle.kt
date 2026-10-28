package dev.drivetrace.ui.trips

import dev.drivetrace.model.Scenario

fun scenarioTitle(value: String): String = Scenario.entries.find { it.name == value }?.title ?: if(value == "REAL") "Поездка ELM327" else value
