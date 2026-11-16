package com.elcomp.medicationorganizer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pl-rPL-w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MedicationScheduleFlowTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun aNewFractionalMedicationHasAnEditableScheduleAndAnUpdatedPlanPreview() {
        val medication = "Lek ułamkowy do harmonogramu"
        createMedication(medication)
        openMedication(medication)
        composeRule.onNodeWithTag("medication-details-name").assert(hasText(medication))
        openSchedule()

        replace("schedule-valid-from", "2026-11-02")
        replace("schedule-valid-until", "2026-11-02")
        day(1)
        replace("schedule-time-0", "8:00")
        selectPeriod(0, "MORNING")
        click("schedule-add-time")
        replace("schedule-time-1", "20:15")
        selectPeriod(1, "EVENING")
        saveSchedule()

        waitFor("schedule-preview")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(tagStartingWith("schedule-preview-dose-"))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).size == 2
        }
        composeRule.onAllNodes(tagStartingWith("schedule-preview-dose-")).assertCountEquals(2)
        composeRule.onNodeWithTag("schedule-preview")
            .assert(hasAnyDescendant(hasText("0.25 ml", substring = true)))
        openSchedule()
        assertValue("schedule-valid-from", "2026-11-02")
        assertValue("schedule-valid-until", "2026-11-02")
        composeRule.onNodeWithTag("schedule-day-1").performScrollTo().assertIsSelected()
        assertValue("schedule-time-0", "08:00")
        assertValue("schedule-time-1", "20:15")
        composeRule.onNodeWithTag("schedule-time-of-day-0").performScrollTo().assert(hasText("Rano", substring = true))
        composeRule.onNodeWithTag("schedule-time-of-day-1").performScrollTo().assert(hasText("Wieczorem", substring = true))
    }

    @Test
    fun medicationEditingAndStatusChangesAreVisibleAfterReturningToTheList() {
        val original = "Lek do edycji i statusów"
        val edited = "Zmieniona nazwa leku"
        createMedication(original)
        openMedication(original)
        assertStatus("Aktywny")
        click("medication-edit")
        waitFor("medication-form")
        assertValue("medication-name", original)
        assertValue("medication-dose-amount", "0.25")
        replace("medication-name", edited)
        replace("medication-dose-amount", "0,125")
        click("medication-save")
        waitFor(hasTestTag("medication-details-name") and hasText(edited))

        click("medication-toggle-status")
        assertStatus("Zawieszony")
        click("medication-toggle-status")
        assertStatus("Aktywny")
        click("medication-deactivate")
        waitFor("medication-confirm-deactivate")
        // Opening the confirmation does not change the medication by itself.
        composeRule.onNodeWithTag("medication-status").assert(hasText("Aktywny"))
        composeRule.onNodeWithTag("medication-confirm-deactivate").performClick()
        assertStatus("Nieaktywny")

        click("medication-details-back")
        waitFor("medications-list")
        composeRule.onAllNodes(tagStartingWith("medication-item-") and hasText(original)).assertCountEquals(0)
        openMedication(edited)
        assertStatus("Nieaktywny")
        click("medication-edit")
        waitFor("medication-form")
        assertValue("medication-name", edited)
        assertValue("medication-dose-amount", "0.125")
    }

    @Test
    fun activityRecreationKeepsTheMedicationContextAndEveryUnsavedScheduleRow() {
        val medication = "Lek z odtworzonym formularzem"
        createMedication(medication)
        openMedication(medication)
        openSchedule()
        replace("schedule-valid-from", "2026-11-02")
        replace("schedule-valid-until", "2026-11-09")
        day(1)
        day(3)
        replace("schedule-time-0", "07:15")
        selectPeriod(0, "MORNING")
        click("schedule-add-time")
        replace("schedule-time-1", "17:45")
        selectPeriod(1, "OTHER")

        composeRule.activityRule.scenario.recreate()

        waitFor("screen-medication-schedule")
        waitFor("schedule-valid-from")
        assertValue("schedule-valid-from", "2026-11-02")
        assertValue("schedule-valid-until", "2026-11-09")
        for (weekday in listOf(1, 3)) composeRule.onNodeWithTag("schedule-day-$weekday")
            .performScrollTo().assertIsSelected()
        assertValue("schedule-time-0", "07:15")
        assertValue("schedule-time-1", "17:45")
        composeRule.onNodeWithTag("schedule-time-of-day-1").performScrollTo().assert(hasText("Inna pora", substring = true))
        saveSchedule()
        waitFor(hasTestTag("medication-details-name") and hasText(medication))
        openSchedule()
        assertValue("schedule-time-0", "07:15")
        assertValue("schedule-time-1", "17:45")
        for (weekday in listOf(1, 3)) composeRule.onNodeWithTag("schedule-day-$weekday")
            .performScrollTo().assertIsSelected()
    }

    private fun createMedication(name: String) {
        waitFor("welcome-start")
        click("welcome-start")
        waitFor("screen-today")
        composeRule.onNodeWithTag("tab-medications").performClick()
        waitFor("medications-list")
        click("medications-add")
        waitFor("medication-form")
        replace("medication-name", name)
        replace("medication-dose-amount", "0,25")
        replace("medication-dose-unit", "ml")
        replace("medication-start-date", "2026-11-02")
        click("medication-save")
        waitFor("medications-list")
        waitFor(tagStartingWith("medication-item-") and hasText(name))
    }

    private fun openMedication(name: String) {
        waitFor(tagStartingWith("medication-item-") and hasText(name))
        composeRule.onNode(tagStartingWith("medication-item-") and hasText(name))
            .performScrollTo().performClick()
        waitFor(hasTestTag("medication-details-name") and hasText(name))
    }

    private fun openSchedule() {
        click("medication-open-schedule")
        waitFor("screen-medication-schedule")
        waitFor("schedule-valid-from")
    }

    private fun saveSchedule() {
        click("schedule-save")
        waitFor("medication-details-name")
    }

    private fun assertStatus(text: String) {
        waitFor(hasTestTag("medication-status") and hasText(text))
        composeRule.onNodeWithTag("medication-status").assert(hasText(text))
    }

    private fun click(tag: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    private fun replace(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(text)
    }

    private fun day(value: Int) = click("schedule-day-$value")

    private fun selectPeriod(row: Int, period: String) {
        click("schedule-time-of-day-$row")
        composeRule.onNodeWithTag("schedule-period-$row-$period").performClick()
    }

    private fun assertValue(tag: String, text: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(text)),
        )
    }

    private fun waitFor(tag: String) = waitFor(hasTestTag(tag))

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun tagStartingWith(prefix: String) = SemanticsMatcher("test tag starts with $prefix") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }
}
