package dev.drivetrace.core.format



fun clockText(ms: Long): String { val s = ms / 1000; return "%02d:%02d".format(s / 60, s % 60) }
