package com.elcomp.medicationorganizer.core.time

import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** A Gregorian date in the device's local timezone, without converting midnight through UTC. */
expect fun currentLocalDate(): String

fun shiftDate(date: String, days: Int): String =
    (Instant.parse("${date}T00:00:00Z") + days.days).toString().substringBefore('T')
