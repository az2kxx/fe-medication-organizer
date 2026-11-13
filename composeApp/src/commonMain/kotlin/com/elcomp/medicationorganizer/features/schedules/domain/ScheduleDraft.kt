package com.elcomp.medicationorganizer.features.schedules.domain

import com.elcomp.medicationorganizer.core.demo.ReplaceSchedule
import com.elcomp.medicationorganizer.core.demo.ScheduleTime
import com.elcomp.medicationorganizer.core.demo.TimeOfDay

data class ScheduleTimeDraft(val time: String = "", val timeOfDay: TimeOfDay? = null)

enum class ScheduleField { DAYS, TIMES, VALID_FROM, VALID_UNTIL }

enum class ScheduleFieldError { REQUIRED, INVALID_DATE, END_BEFORE_START, INVALID_TIME, DUPLICATE_TIME, INVALID_WEEKDAY }

data class ScheduleValidation(
    val request: ReplaceSchedule?,
    val errors: Map<ScheduleField, ScheduleFieldError>,
    val timeErrors: Map<Int, ScheduleFieldError>,
)

data class ScheduleDraft(
    val validFrom: String = "",
    val validUntil: String = "",
    val days: Set<Int> = emptySet(),
    val times: List<ScheduleTimeDraft> = listOf(ScheduleTimeDraft()),
) {
    fun validate(): ScheduleValidation {
        val errors = mutableMapOf<ScheduleField, ScheduleFieldError>()
        val timeErrors = mutableMapOf<Int, ScheduleFieldError>()
        val start = validFrom.trim()
        val end = validUntil.trim().takeIf(String::isNotEmpty)

        if (start.isEmpty()) {
            errors[ScheduleField.VALID_FROM] = ScheduleFieldError.REQUIRED
        } else if (!isCalendarDate(start)) {
            errors[ScheduleField.VALID_FROM] = ScheduleFieldError.INVALID_DATE
        }
        if (end != null) {
            if (!isCalendarDate(end)) {
                errors[ScheduleField.VALID_UNTIL] = ScheduleFieldError.INVALID_DATE
            } else if (ScheduleField.VALID_FROM !in errors && end < start) {
                errors[ScheduleField.VALID_UNTIL] = ScheduleFieldError.END_BEFORE_START
            }
        }
        if (days.isEmpty()) {
            errors[ScheduleField.DAYS] = ScheduleFieldError.REQUIRED
        } else if (days.any { it !in 1..7 }) {
            errors[ScheduleField.DAYS] = ScheduleFieldError.INVALID_WEEKDAY
        }
        if (times.isEmpty()) errors[ScheduleField.TIMES] = ScheduleFieldError.REQUIRED

        val normalizedTimes = times.mapIndexed { index, entry ->
            val value = normalizeTime(entry.time)
            if (value == null) {
                timeErrors[index] = if (entry.time.isBlank()) ScheduleFieldError.REQUIRED else ScheduleFieldError.INVALID_TIME
            }
            value
        }
        normalizedTimes.withIndex().filter { it.value != null }.groupBy { it.value }.values
            .filter { it.size > 1 }.forEach { duplicates ->
                for (entry in duplicates) timeErrors[entry.index] = ScheduleFieldError.DUPLICATE_TIME
            }

        val request = if (errors.isEmpty() && timeErrors.isEmpty()) {
            ReplaceSchedule(
                validFrom = start,
                validUntil = end,
                days = days.sorted().toCollection(linkedSetOf()),
                times = times.mapIndexed { index, entry -> ScheduleTime(checkNotNull(normalizedTimes[index]), entry.timeOfDay) },
            )
        } else {
            null
        }
        return ScheduleValidation(request, errors, timeErrors)
    }

    companion object {
        fun from(request: ReplaceSchedule): ScheduleDraft = ScheduleDraft(
            validFrom = request.validFrom,
            validUntil = request.validUntil.orEmpty(),
            days = request.days.toSet(),
            times = request.times.map { ScheduleTimeDraft(it.time, it.timeOfDay) },
        )
    }
}

private val dateFormat = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val timeFormat = Regex("([0-9]{1,2}):([0-9]{2})")

private fun isCalendarDate(value: String): Boolean {
    if (!dateFormat.matches(value)) return false
    val year = value.substring(0, 4).toInt()
    val month = value.substring(5, 7).toInt()
    val day = value.substring(8, 10).toInt()
    val leapYear = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
    val daysInMonth = when (month) {
        2 -> if (leapYear) 29 else 28
        4, 6, 9, 11 -> 30
        1, 3, 5, 7, 8, 10, 12 -> 31
        else -> return false
    }
    return day in 1..daysInMonth
}

private fun normalizeTime(value: String): String? {
    val match = timeFormat.matchEntire(value.trim()) ?: return null
    val hour = match.groupValues[1].toInt()
    val minute = match.groupValues[2].toInt()
    if (hour !in 0..23 || minute !in 0..59) return null
    return "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
}
