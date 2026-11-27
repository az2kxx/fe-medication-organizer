package com.elcomp.medicationorganizer

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.elcomp.medicationorganizer.app.App
import com.elcomp.medicationorganizer.core.demo.CreateMedication
import com.elcomp.medicationorganizer.core.demo.ReplaceSchedule
import com.elcomp.medicationorganizer.core.demo.ScheduleTime
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.core.network.MockScenario
import com.elcomp.medicationorganizer.core.time.currentLocalDate
import com.elcomp.medicationorganizer.features.auth.data.dto.LoginRequest
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pl-rPL-w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MedicationPlanPreviewTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun anOngoingScheduleShowsTodayAndTheNextSixDaysInsteadOfItsHistoricalFirstWeek() {
        val today = LocalDate.parse(currentLocalDate())
        withSchedule(validFrom = today.minusDays(30)) {
            assertRange(today)
            assertSevenDailyDoses(today)
        }
    }

    @Test
    fun aFutureScheduleStartsItsPreviewOnItsFutureValidFrom() {
        val today = LocalDate.parse(currentLocalDate())
        val futureStart = today.plusDays(14)
        withSchedule(validFrom = futureStart) {
            assertRange(futureStart)
            assertSevenDailyDoses(futureStart)
        }
    }

    @Test
    fun anEndedScheduleShowsAnEmptyCurrentRangeInsteadOfHistoricalDoses() {
        val today = LocalDate.parse(currentLocalDate())
        withSchedule(validFrom = today.minusDays(30), validUntil = today.minusDays(1)) {
            assertRange(today)
            composeRule.onAllNodes(previewDose()).assertCountEquals(0)
            composeRule.onNodeWithTag("schedule-preview").assert(
                hasAnyDescendant(hasText("Brak dawek w tym okresie.", substring = true)),
            )
        }
    }

    private fun withSchedule(
        validFrom: LocalDate,
        validUntil: LocalDate? = null,
        test: () -> Unit,
    ) {
        val services = AppServices.create(ApiSettings.Mock(MockScenario.EMPTY))
        try {
            val medicationId = runBlocking {
                services.signIn(LoginRequest("alex@example.com", "example-password"))
                val demo = requireNotNull(services.demo)
                val medication = demo.createMedication(
                    CreateMedication("Lek do podglądu planu", JsonPrimitive(1), "tabletka", validFrom.toString()),
                )
                demo.replaceSchedule(
                    medication.id,
                    ReplaceSchedule(
                        validFrom = validFrom.toString(),
                        days = (1..7).toSet(),
                        times = listOf(ScheduleTime("08:00")),
                        validUntil = validUntil?.toString(),
                        userOrganizerId = 1,
                    ),
                )
                medication.id
            }
            composeRule.activityRule.scenario.onActivity { it.setContent { App(services, false) } }
            waitFor(hasTestTag("screen-today"))
            composeRule.onNodeWithTag("tab-medications").performClick()
            waitFor(hasTestTag("medication-item-$medicationId"))
            composeRule.onNodeWithTag("medication-item-$medicationId").performScrollTo().performClick()
            // The range and dose cards are emitted together after the asynchronous preview load.
            // Waiting for either range lets the old implementation fail at the date assertion.
            waitFor(
                hasTestTag("schedule-preview") and hasAnyDescendant(hasText(" – ", substring = true)),
            )
            test()
        } finally {
            composeRule.activityRule.scenario.onActivity { it.setContent {} }
            services.close()
        }
    }

    private fun assertRange(start: LocalDate) {
        composeRule.onNodeWithTag("schedule-preview").assert(
            hasAnyDescendant(hasText("$start – ${start.plusDays(6)}", substring = true)),
        )
    }

    private fun assertSevenDailyDoses(start: LocalDate) {
        composeRule.onAllNodes(previewDose()).assertCountEquals(7)
        for (offset in 0L..6L) {
            composeRule.onNodeWithText("${start.plusDays(offset)} · 08:00").assertExists()
        }
    }

    private fun previewDose() = SemanticsMatcher("plan preview dose card") {
        it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("schedule-preview-dose-") == true
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }
}
