package com.elcomp.medicationorganizer

import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
    fun launcherShowsLocalSetupAndMockModeInPolish() {
        composeRule.onNodeWithTag("setup-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Konfiguracja lokalna").assertIsDisplayed()
        composeRule.onNodeWithText("Tryb demo (MOCK)").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp")
    fun launcherUsesEnglishResources() {
        composeRule.onNodeWithText("Local setup").assertIsDisplayed()
        composeRule.onNodeWithText("Demo mode (MOCK)").assertIsDisplayed()
        composeRule.onNodeWithText("Go to Today").assertIsDisplayed()
    }

    @Test
    fun setupAnnouncesBlankNameErrorAndAcceptsValidName() {
        val name = composeRule.onNodeWithTag("setup-name")
        val submit = composeRule.onNodeWithTag("setup-submit")
        name.performTextInput("   ")
        submit.performScrollTo().performClick()

        val error = "Wpisz nazwę wyświetlaną."
        composeRule.onNodeWithTag("setup-screen").assertIsDisplayed()
        composeRule.onNodeWithText(error).assertIsDisplayed()
        name.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, error))

        name.performTextReplacement("  Ola  ")
        submit.performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-today").assertIsDisplayed()
        composeRule.onNodeWithText("Witaj, Ola").assertIsDisplayed()
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
    fun menuBackRestoresSectionAndSectionBackReturnsToToday() {
        completeSetup()
        openMenu()
        composeRule.onNodeWithTag("navigation-menu").assertIsDisplayed()
        listOf("today", "organizer", "medications", "preparation", "history", "settings").forEach { section ->
            composeRule.onNodeWithTag("navigation-$section-destination").assertExists()
        }
        composeRule.onNodeWithTag("navigation-history-destination").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-history").assertIsDisplayed()

        openMenu()
        pressSystemBack()
        composeRule.onNodeWithTag("navigation-menu").assertDoesNotExist()
        composeRule.onNodeWithTag("screen-history").assertIsDisplayed()

        pressSystemBack()
        composeRule.onNodeWithTag("screen-today").assertIsDisplayed()

        openMenu()
        composeRule.onNodeWithTag("navigation-medications-destination").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-medications").assertIsDisplayed()
        composeRule.onNodeWithTag("navigation-today").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-today").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pl-rPL-w320dp-h470dp", fontScale = 2f)
    fun setupActionRemainsReachableWithLargeTextOnSmallScreen() {
        assertEquals(2f, composeRule.activity.resources.configuration.fontScale, 0f)
        completeSetup()
        composeRule.onNodeWithTag("screen-today").assertIsDisplayed()
    }

    private fun completeSetup() {
        composeRule.onNodeWithTag("setup-name").performScrollTo().performTextInput("Ola")
        composeRule.onNodeWithTag("setup-submit").performScrollTo().assertIsDisplayed().performClick()
    }

    private fun openMenu() {
        composeRule.onNodeWithTag("navigation-open-menu").performScrollTo().performClick()
    }

    private fun pressSystemBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }
}
