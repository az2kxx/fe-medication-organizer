package com.elcomp.medicationorganizer

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.elcomp.medicationorganizer.core.ui.AppSubmitButton
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pl-rPL-w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherShowsWelcomeWithoutRequestingAName() {
        composeRule.onNodeWithTag("welcome-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Witaj w EL COMP").assertIsDisplayed()
        composeRule.onNodeWithText("Tryb demo (MOCK)").assertIsDisplayed()
        composeRule.onNodeWithText("Zacznij").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        startFromWelcome()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp")
    fun launcherUsesEnglishResources() {
        composeRule.onNodeWithText("Welcome to EL COMP").assertIsDisplayed()
        composeRule.onNodeWithText("Demo mode (MOCK)").assertIsDisplayed()
        composeRule.onNodeWithText("Get started").assertIsDisplayed()
    }

    @Test
    fun localProfileAnnouncesBlankNameErrorAndSavesValidName() {
        startFromWelcome()
        composeRule.onNodeWithTag("today-open-settings").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-settings").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation-open-profile").performScrollTo().performClick()

        val name = composeRule.onNodeWithTag("profile-name")
        val submit = composeRule.onNodeWithTag("profile-submit")
        name.performScrollTo().performTextReplacement("   ")
        submit.performScrollTo().performClick()

        val error = "Wpisz nazwę wyświetlaną."
        composeRule.onNodeWithTag("profile-screen").assertIsDisplayed()
        composeRule.onNodeWithText(error).assertIsDisplayed()
        name.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, error))

        name.performTextReplacement("  Ola  ")
        submit.performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-settings").assertIsDisplayed()
        composeRule.onNodeWithText("Nazwa wyświetlana: Ola").assertIsDisplayed()
        pressSystemBack()
        composeRule.onNodeWithTag("screen-today").assertIsDisplayed()
    }

    @Test
    fun submitButtonBlocksAnotherTapUntilSavingCompletes() {
        val saveComplete = CompletableDeferred<Unit>()
        var submissions = 0
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                AppSubmitButton(
                    text = "Zapisz formularz",
                    onSubmit = {
                        submissions += 1
                        saveComplete.await()
                    },
                )
            }
        }

        val submit = composeRule.onNodeWithText("Zapisz formularz")
        submit.assertIsEnabled().performClick()
        composeRule.onNodeWithText("Zapisywanie…").assertIsNotEnabled().performTouchInput { click() }
        composeRule.runOnIdle { assertEquals(1, submissions) }

        saveComplete.complete(Unit)
        submit.assertIsEnabled()
    }

    @Test
    fun organizerOpensPreparationAndBothBackActionsReturnToOrganizer() {
        startFromWelcome()
        composeRule.onNodeWithTag("today-open-organizer").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-organizer").assertIsDisplayed()
        openPreparation()
        composeRule.onNodeWithTag("preparation-back-organizer").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-organizer").assertIsDisplayed()

        openPreparation()
        pressSystemBack()
        composeRule.onNodeWithTag("screen-organizer").assertIsDisplayed()
    }

    @Test
    fun bottomTabsOpenSectionsDirectlyAndExposeSelection() {
        startFromWelcome()
        composeRule.onNodeWithTag("tab-today").assertIsSelected()
        for (section in listOf("organizer", "medications", "history", "today")) {
            val tab = composeRule.onNodeWithTag("tab-$section")
            tab.assertIsDisplayed().assertIsNotSelected().performClick()
            composeRule.onNodeWithTag("screen-$section").assertIsDisplayed()
            tab.assertIsSelected()
        }
    }

    @Test
    @Config(qualifiers = "pl-rPL-w320dp-h470dp", fontScale = 2f)
    fun welcomeAndBottomTabsRemainReachableWithLargeTextOnSmallScreen() {
        assertEquals(2f, composeRule.activity.resources.configuration.fontScale, 0f)
        startFromWelcome()
        for (section in listOf("today", "organizer", "medications", "history")) {
            composeRule.onNodeWithTag("tab-$section").assertIsDisplayed()
        }
        composeRule.onNodeWithTag("tab-history").performClick().assertIsSelected()
        composeRule.onNodeWithTag("screen-history").assertIsDisplayed()
    }

    private fun startFromWelcome() {
        composeRule.onNodeWithTag("welcome-start").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("screen-today").assertIsDisplayed()
    }

    private fun openPreparation() {
        composeRule.onNodeWithTag("organizer-open-preparation").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-preparation").assertIsDisplayed()
    }

    private fun pressSystemBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }
}
