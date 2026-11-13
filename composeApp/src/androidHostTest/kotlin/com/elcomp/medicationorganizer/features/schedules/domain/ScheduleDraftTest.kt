package com.elcomp.medicationorganizer.features.schedules.domain

import com.elcomp.medicationorganizer.core.demo.ReplaceSchedule
import com.elcomp.medicationorganizer.core.demo.ScheduleTime
import com.elcomp.medicationorganizer.core.demo.TimeOfDay
import com.elcomp.medicationorganizer.core.network.apiJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleDraftTest {
    @Test
    fun emptyDraftReportsRequiredFieldsWithoutCreatingARequest() {
        val validation = ScheduleDraft().validate()
        assertNull(validation.request)
        assertEquals(
            mapOf(ScheduleField.VALID_FROM to ScheduleFieldError.REQUIRED, ScheduleField.DAYS to ScheduleFieldError.REQUIRED),
            validation.errors,
        )
        assertEquals(mapOf(0 to ScheduleFieldError.REQUIRED), validation.timeErrors)

        val noTimes = validDraft.copy(times = emptyList()).validate()
        assertNull(noTimes.request)
        assertEquals(mapOf(ScheduleField.TIMES to ScheduleFieldError.REQUIRED), noTimes.errors)
    }

    @Test
    fun weekdaysMustBeIsoDaysAndAreSentInMondayToSundayOrder() {
        for (days in listOf(setOf(0, 1), setOf(1, 8), setOf(-1))) {
            val validation = validDraft.copy(days = days).validate()
            assertNull(validation.request)
            assertEquals(mapOf(ScheduleField.DAYS to ScheduleFieldError.INVALID_WEEKDAY), validation.errors)
        }
        assertEquals(listOf(1, 3, 7), assertNotNull(validDraft.validate().request).days.toList())
    }

    @Test
    fun validHoursNormalizeWithoutReorderingOrInferringTimeOfDay() {
        val request = assertNotNull(
            validDraft.copy(
                times = listOf(
                    ScheduleTimeDraft(" 23:59 ", TimeOfDay.OTHER),
                    ScheduleTimeDraft("8:05"),
                    ScheduleTimeDraft("00:00", TimeOfDay.NIGHT),
                ),
            ).validate().request,
        )
        assertEquals(
            listOf(ScheduleTime("23:59", TimeOfDay.OTHER), ScheduleTime("08:05"), ScheduleTime("00:00", TimeOfDay.NIGHT)),
            request.times,
        )
    }

    @Test
    fun invalidHoursAndMissingMinutesAreRejectedOnTheirRows() {
        for (input in listOf("24:00", "23:60", "8:5", "008:00", "-1:00", "08:00:00", "8.00", "8", "08:0a")) {
            val validation = validDraft.copy(times = listOf(ScheduleTimeDraft(input))).validate()
            assertNull(validation.request, input)
            assertEquals(mapOf(0 to ScheduleFieldError.INVALID_TIME), validation.timeErrors, input)
        }
        assertEquals(
            mapOf(0 to ScheduleFieldError.REQUIRED),
            validDraft.copy(times = listOf(ScheduleTimeDraft("  "))).validate().timeErrors,
        )
    }

    @Test
    fun duplicateHoursAreRejectedAfterNormalizationEvenWithDifferentTimeOfDay() {
        val validation = validDraft.copy(
            times = listOf(
                ScheduleTimeDraft("8:00", TimeOfDay.MORNING),
                ScheduleTimeDraft("20:00", TimeOfDay.EVENING),
                ScheduleTimeDraft(" 08:00 ", TimeOfDay.OTHER),
            ),
        ).validate()
        assertNull(validation.request)
        assertEquals(mapOf(0 to ScheduleFieldError.DUPLICATE_TIME, 2 to ScheduleFieldError.DUPLICATE_TIME), validation.timeErrors)
    }

    @Test
    fun datesMustBeRealCalendarDaysAndTheEndMayEqualTheStart() {
        for (input in listOf("2026-2-01", "2026-02-30", "2026-04-31", "2026-13-01", "2026-00-01", "2026-01-00", "1900-02-29", "2026-01-01T00:00:00Z")) {
            val validation = validDraft.copy(validFrom = input, validUntil = input).validate()
            assertNull(validation.request, input)
            assertEquals(
                mapOf(ScheduleField.VALID_FROM to ScheduleFieldError.INVALID_DATE, ScheduleField.VALID_UNTIL to ScheduleFieldError.INVALID_DATE),
                validation.errors,
                input,
            )
        }
        val leapDay = validDraft.copy(validFrom = " 2000-02-29 ", validUntil = "2000-02-29 ").validate()
        assertTrue(leapDay.errors.isEmpty())
        val leapDayRequest = assertNotNull(leapDay.request)
        assertEquals("2000-02-29", leapDayRequest.validFrom)
        assertEquals("2000-02-29", leapDayRequest.validUntil)
        val reversed = validDraft.copy(validFrom = "2026-11-02", validUntil = "2026-11-01").validate()
        assertNull(reversed.request)
        assertEquals(mapOf(ScheduleField.VALID_UNTIL to ScheduleFieldError.END_BEFORE_START), reversed.errors)
        assertNull(assertNotNull(validDraft.copy(validUntil = " ").validate().request).validUntil)
    }

    @Test
    fun editingAnExistingScheduleKeepsEveryRuleAndMatchesTheCompleteRequestFixture() {
        val existing = apiJson.decodeFromString<ReplaceSchedule>(fixture("fe12-replace-schedule-request.json"))
        val draft = ScheduleDraft.from(existing)
        assertEquals(3, draft.times.size)
        assertEquals(existing.days, draft.days)
        val validation = draft.validate()
        assertTrue(validation.errors.isEmpty())
        assertTrue(validation.timeErrors.isEmpty())
        assertEquals(existing, validation.request)
        assertEquals(
            apiJson.parseToJsonElement(fixture("fe12-replace-schedule-request.json")),
            apiJson.parseToJsonElement(apiJson.encodeToString(assertNotNull(validation.request))),
        )

        val changed = assertNotNull(draft.copy(validUntil = "2026-12-01").validate().request)
        assertEquals(existing.days, changed.days)
        assertEquals(existing.times, changed.times)
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.getResource("/fixtures/schedules/$name")).readText()
}

private val validDraft = ScheduleDraft(
    validFrom = "2026-11-02",
    validUntil = "2026-11-30",
    days = setOf(7, 1, 3),
    times = listOf(ScheduleTimeDraft("08:00", TimeOfDay.MORNING)),
)
