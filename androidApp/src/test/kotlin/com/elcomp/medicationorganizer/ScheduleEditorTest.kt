package com.elcomp.medicationorganizer

import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.elcomp.medicationorganizer.core.demo.ReplaceSchedule
import com.elcomp.medicationorganizer.core.demo.ScheduleTime
import com.elcomp.medicationorganizer.core.demo.TimeOfDay
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import com.elcomp.medicationorganizer.features.schedules.presentation.ScheduleEditor
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pl-rPL-w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScheduleEditorTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun validationPreventsInvalidSubmissionAndSavesTheCompleteNormalizedRequest() {
        var saved: Pair<Long, ReplaceSchedule>? = null
        mount(onSave = { id, request -> saved = id to request })
        submit()
        assertEquals(null, saved)
        assertError("schedule-days", "Wybierz co najmniej jeden dzień.")
        assertError("schedule-valid-from", "Uzupełnij to pole.")

        day(1)
        day(5)
        replace("schedule-valid-from", "2026-02-30")
        replace("schedule-time-0", "24:00")
        submit()
        assertError("schedule-valid-from", "Wpisz istniejącą datę w formacie RRRR-MM-DD.")
        assertError("schedule-time-0", "Wpisz godzinę w formacie GG:MM (00:00–23:59).")

        replace("schedule-valid-from", "2026-11-02")
        replace("schedule-valid-until", "2026-11-01")
        replace("schedule-time-0", "8:00")
        composeRule.onNodeWithTag("schedule-add-time").performScrollTo().performClick()
        replace("schedule-time-1", "08:00")
        submit()
        assertEquals(null, saved)
        assertError("schedule-valid-until", "Data końca nie może być wcześniejsza niż data początku.")
        for (row in 0..1) assertError("schedule-time-$row", "Każda godzina może wystąpić tylko raz.")

        replace("schedule-valid-until", "2026-11-09")
        replace("schedule-time-1", "20:15")
        selectPeriod(1, TimeOfDay.EVENING)
        submit()
        composeRule.waitUntil(5_000) { saved != null }
        assertEquals(
            12L to ReplaceSchedule(
                "2026-11-02", setOf(1, 5),
                listOf(ScheduleTime("08:00"), ScheduleTime("20:15", TimeOfDay.EVENING)), "2026-11-09",
            ),
            saved,
        )
    }

    @Test
    fun editingExistingSchedulePreservesOtherRowsAndTheirTimeOfDay() {
        var saved: ReplaceSchedule? = null
        val initial = ReplaceSchedule(
            "2026-11-02", setOf(2, 4, 7),
            listOf(
                ScheduleTime("08:15", TimeOfDay.MORNING),
                ScheduleTime("12:30", TimeOfDay.NOON),
                ScheduleTime("21:45", TimeOfDay.NIGHT),
            ),
        )
        mount(initial) { _, request -> saved = request }
        for (weekday in initial.days) composeRule.onNodeWithTag("schedule-day-$weekday")
            .performScrollTo().assertIsSelected()
        composeRule.onNodeWithTag("schedule-day-1").performScrollTo().assertIsNotSelected()
        assertValue("schedule-time-1", "12:30")

        composeRule.onNodeWithTag("schedule-remove-time-1").performScrollTo().performClick()
        assertValue("schedule-time-1", "21:45")
        selectPeriod(0, null)
        composeRule.onNodeWithTag("schedule-add-time").performScrollTo().performClick()
        replace("schedule-time-2", "17:00")
        selectPeriod(2, TimeOfDay.OTHER)
        submit()
        composeRule.waitUntil(5_000) { saved != null }
        assertEquals(
            initial.copy(times = listOf(
                ScheduleTime("08:15"),
                ScheduleTime("21:45", TimeOfDay.NIGHT),
                ScheduleTime("17:00", TimeOfDay.OTHER),
            )),
            saved,
        )
    }

    @Test
    fun pendingSaveBlocksDuplicateSubmissionAndFailureKeepsTheDraftForRetry() {
        val release = CompletableDeferred<Unit>()
        val services = AppServices.create()
        var attempts = 0
        var successfulSaves = 0
        var saved: ReplaceSchedule? = null
        val initial = ReplaceSchedule("2026-11-02", setOf(3), listOf(ScheduleTime("07:30", TimeOfDay.MORNING)))
        try {
            mount(initial, onSaved = { successfulSaves++ }) { _, request ->
                attempts++
                if (attempts == 1) {
                    release.await()
                    // Obtain the public API failure through the real transport rather than constructing an internal exception.
                    services.auth.login(LoginRequest("alex@example.com", "wrong-password"))
                }
                saved = request
            }
            replace("schedule-valid-until", "2026-12-31")
            submit()
            composeRule.waitUntil(5_000) { attempts == 1 }
            composeRule.onNodeWithTag("schedule-save").performScrollTo().assertIsNotEnabled()
                .performTouchInput { click(); click() }
            composeRule.onNodeWithTag("schedule-add-time").performScrollTo().assertIsNotEnabled()
            composeRule.onNodeWithTag("schedule-time-0").performScrollTo().assertIsNotEnabled()
            assertEquals(1, attempts)

            release.complete(Unit)
            waitFor("schedule-save-error")
            assertValue("schedule-valid-until", "2026-12-31")
            assertValue("schedule-time-0", "07:30")
            composeRule.onNodeWithTag("schedule-save").performScrollTo().assertIsEnabled().performClick()
            composeRule.waitUntil(5_000) { successfulSaves == 1 }
            assertEquals(2, attempts)
            assertEquals(initial.copy(validUntil = "2026-12-31"), saved)
        } finally {
            release.complete(Unit)
            composeRule.activityRule.scenario.onActivity { it.setContent {} }
            services.close()
        }
    }

    @Test
    fun theHostCanRestoreAnUnsavedDraftWithAllDaysAndTimeRows() {
        var showEditor by mutableStateOf(true)
        var saved: ReplaceSchedule? = null
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                val holder = rememberSaveableStateHolder()
                if (showEditor) holder.SaveableStateProvider("schedule") {
                    ScheduleEditor(
                        medicationId = 12L,
                        onSave = { _, request -> saved = request },
                        onCancel = {},
                    )
                }
            }
        }
        waitFor("schedule-valid-from")
        replace("schedule-valid-from", "2026-11-02")
        replace("schedule-valid-until", "2026-11-08")
        day(1)
        day(7)
        replace("schedule-time-0", "09:15")
        selectPeriod(0, TimeOfDay.NOON)
        composeRule.onNodeWithTag("schedule-add-time").performScrollTo().performClick()
        replace("schedule-time-1", "23:10")
        selectPeriod(1, TimeOfDay.NIGHT)

        // Recreate the editor's composition through its host's saveable-state registry.
        composeRule.runOnIdle { showEditor = false }
        composeRule.runOnIdle { showEditor = true }
        waitFor("schedule-valid-from")
        assertValue("schedule-valid-from", "2026-11-02")
        assertValue("schedule-valid-until", "2026-11-08")
        assertValue("schedule-time-0", "09:15")
        assertValue("schedule-time-1", "23:10")
        for (weekday in listOf(1, 7)) composeRule.onNodeWithTag("schedule-day-$weekday")
            .performScrollTo().assertIsSelected()
        submit()
        composeRule.waitUntil(5_000) { saved != null }
        assertNotNull(saved)
        assertEquals(
            ReplaceSchedule(
                "2026-11-02", setOf(1, 7),
                listOf(ScheduleTime("09:15", TimeOfDay.NOON), ScheduleTime("23:10", TimeOfDay.NIGHT)),
                "2026-11-08",
            ),
            saved,
        )
    }

    private fun mount(
        initial: ReplaceSchedule? = null,
        onSaved: () -> Unit = {},
        onSave: suspend (Long, ReplaceSchedule) -> Unit,
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                ScheduleEditor(12L, initial, onSave, onSaved, onCancel = {})
            }
        }
        waitFor("schedule-valid-from")
    }

    private fun replace(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    private fun day(weekday: Int) {
        composeRule.onNodeWithTag("schedule-day-$weekday").performScrollTo().performClick()
    }

    private fun selectPeriod(row: Int, value: TimeOfDay?) {
        composeRule.onNodeWithTag("schedule-time-of-day-$row").performScrollTo().performClick()
        composeRule.onNodeWithTag("schedule-period-$row-${value?.name ?: "NONE"}").performClick()
    }

    private fun submit() {
        composeRule.onNodeWithTag("schedule-save").performScrollTo().performClick()
    }

    private fun assertValue(tag: String, expected: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(expected)),
        )
    }

    private fun assertError(tag: String, expected: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.Error, expected),
        )
    }

    private fun waitFor(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag(tag))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }
}
