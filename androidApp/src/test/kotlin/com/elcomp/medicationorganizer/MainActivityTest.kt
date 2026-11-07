package com.elcomp.medicationorganizer

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.setContent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
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
import org.robolectric.Shadows.shadowOf
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
        assertEventuallyDisplayed(hasTestTag("welcome-screen"))
        composeRule.onNodeWithText("Witaj w EL COMP").assertIsDisplayed()
        composeRule.onNodeWithText("Tryb demo (MOCK)").assertIsDisplayed()
        composeRule.onNodeWithText("Wypróbuj demo").assertIsDisplayed()
        composeRule.onAllNodes(hasSetTextAction()).assertCountEquals(0)
        startFromWelcome()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp")
    fun launcherUsesEnglishResources() {
        assertEventuallyDisplayed(hasText("Welcome to EL COMP"))
        composeRule.onNodeWithText("Demo mode (MOCK)").assertIsDisplayed()
        composeRule.onNodeWithText("Try the demo").assertIsDisplayed()
    }

    @Test
    fun demoRegistrationRequiresLoginAndProfileStaysWithItsAccount() {
        val email = "ola@example.org"
        val password = "test-password"
        openAuthRegistration()
        replaceField("auth-email", email)
        replaceField("auth-password", password)
        replaceField("auth-display-name", "Ola")
        submitAuth()

        assertEventuallyDisplayed(hasTestTag("auth-login"))
        composeRule.onNodeWithText("Konto zostało utworzone. Zaloguj się.").assertIsDisplayed()
        assertFieldValue("auth-email", email)
        assertFieldValue("auth-password", "")
        composeRule.onNodeWithTag("tab-today").assertDoesNotExist()

        replaceField("auth-password", password)
        submitAuth()
        assertEventuallyDisplayed(hasTestTag("screen-today"))

        openSettings()
        composeRule.onNodeWithTag("navigation-open-profile").performScrollTo().performClick()
        waitForNode(hasTestTag("profile-name"))
        assertEventuallyDisplayed(hasTestTag("profile-email") and hasText(email, substring = true))
        replaceField("profile-name", "Tymczasowa nazwa")
        composeRule.onNodeWithTag("profile-submit").performScrollTo().performClick()
        assertEventuallyDisplayed(hasText("Nazwa wyświetlana: Tymczasowa nazwa"))
        signOut()

        assertEventuallyDisplayed(hasTestTag("auth-login"))
        composeRule.onNodeWithTag("tab-today").assertDoesNotExist()
        assertFieldValue("auth-password", "")

        replaceField("auth-email", email)
        replaceField("auth-password", password)
        submitAuth()
        assertEventuallyDisplayed(hasTestTag("screen-today"))
        openSettings()
        assertEventuallyDisplayed(hasText("Nazwa wyświetlana: Tymczasowa nazwa"))

        signOut()
        replaceField("auth-email", "alex@example.com")
        replaceField("auth-password", "example-password")
        submitAuth()
        assertEventuallyDisplayed(hasTestTag("screen-today"))
        openSettings()
        assertEventuallyDisplayed(hasTestTag("profile-email") and hasText("alex@example.com", substring = true))
        composeRule.onNodeWithText("Nazwa wyświetlana: Tymczasowa nazwa").assertDoesNotExist()
    }

    @Test
    fun loginRejectsWrongPasswordAndAllowsRetry() {
        openAuthLogin()
        replaceField("auth-email", "alex@example.com")
        replaceField("auth-password", "wrong-password")
        submitAuth()

        assertEventuallyDisplayed(hasText("Sprawdź adres e-mail i hasło."))
        composeRule.onNodeWithTag("auth-login").assertIsDisplayed()
        composeRule.onNodeWithTag("tab-today").assertDoesNotExist()

        replaceField("auth-password", "example-password")
        submitAuth()
        assertEventuallyDisplayed(hasTestTag("screen-today"))
    }

    @Test
    fun registrationReportsConflictOnEmailField() {
        openAuthRegistration()
        replaceField("auth-email", "alex@example.com")
        replaceField("auth-password", "example-password")
        submitAuth()

        val error = "Konto z tym adresem e-mail już istnieje."
        assertEventuallyDisplayed(hasText(error))
        composeRule.onNodeWithTag("auth-register").assertIsDisplayed()
        composeRule.onNodeWithTag("auth-email")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, error))
        composeRule.onNodeWithTag("tab-today").assertDoesNotExist()
    }

    @Test
    fun profileDisplaysAccountEmailAnnouncesBlankNameErrorAndSavesValidName() {
        startFromWelcome()
        openSettings()
        assertEventuallyDisplayed(hasTestTag("profile-email") and hasText("alex@example.com", substring = true))
        composeRule.onNodeWithTag("navigation-open-profile").performScrollTo().performClick()
        waitForNode(hasTestTag("profile-name"))
        assertEventuallyDisplayed(hasTestTag("profile-email") and hasText("alex@example.com", substring = true))
        composeRule.onNodeWithTag("profile-email").assert(!hasSetTextAction())

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
        assertEventuallyDisplayed(hasTestTag("screen-settings"))
        assertEventuallyDisplayed(hasText("Nazwa wyświetlana: Ola"))
        pressSystemBack()
        composeRule.onNodeWithTag("screen-today").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pl-rPL-w320dp-h470dp", fontScale = 2f)
    fun demoPreferencesSaveAndRestoreWithLargeSystemText() {
        startFromWelcome()
        openSettings()
        for (tag in listOf("preferences-simplified", "preferences-high-contrast", "preferences-text-200")) {
            waitForNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        composeRule.onNodeWithTag("preferences-save").performScrollTo().performClick()
        waitForNode(hasTestTag("preferences-saved"))
        composeRule.onNodeWithTag("preferences-saved").performScrollTo().assertIsDisplayed()
        pressSystemBack()
        assertSimplifiedToday()

        composeRule.activityRule.scenario.recreate()
        startFromWelcome()
        assertSimplifiedToday()
        composeRule.onNodeWithTag("tab-history").assertIsDisplayed()
        openSettings()
        waitForNode(hasTestTag("preferences-simplified"))
        composeRule.onNodeWithTag("preferences-simplified").performScrollTo().assertIsOn()
        composeRule.onNodeWithTag("preferences-high-contrast").performScrollTo().assertIsOn()
        composeRule.onNode(hasTestTag("preferences-text-200") and hasText("200%"))
            .performScrollTo()
            .assertIsSelected()
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
    fun organizerCatalogSearchesByCodeShowsLayoutAndBlocksInactiveModel() {
        startFromWelcome()
        composeRule.onNodeWithTag("tab-organizer").performClick()
        openManualCatalog()
        assertEventuallyDisplayed(hasTestTag("screen-organizer-catalog"))
        waitForNode(hasTestTag("organizer-model-4"))

        composeRule.onNodeWithTag("organizer-search").performScrollTo().performClick()
        composeRule.onNodeWithText("Wpisz kod produktu.").assertExists()

        replaceField("organizer-code", "BRAK-123")
        composeRule.onNodeWithTag("organizer-search").performScrollTo().performClick()
        waitForNode(hasTestTag("organizer-search-empty"))
        composeRule.onNodeWithText("Brak modelu o kodzie BRAK-123", substring = true).assertExists()

        replaceField("organizer-code", " elc-7x3 ")
        composeRule.onNodeWithTag("organizer-search").performScrollTo().performClick()
        waitForNode(hasTestTag("organizer-model-2"))
        composeRule.onNodeWithTag("organizer-model-1").assertDoesNotExist()
        composeRule.onNodeWithTag("organizer-model-2").performScrollTo().performClick()

        waitForNode(hasTestTag("organizer-details"))
        composeRule.onNodeWithText("Kasetka tygodniowa 7×3").assertExists()
        composeRule.onAllNodes(hasTestTag("organizer-layout-row")).assertCountEquals(7)
        composeRule.onNodeWithTag("organizer-compartment-102")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("Pon. południe, Poniedziałek, Południe")))
        composeRule.onNodeWithTag("organizer-select").performScrollTo().assertIsEnabled().performClick()
        composeRule.onNodeWithTag("organizer-confirm").assertExists()
        composeRule.onNodeWithTag("organizer-assign-unavailable").performScrollTo().assertIsDisplayed()

        pressSystemBack()
        composeRule.onNodeWithTag("organizer-details").assertExists()
        pressSystemBack()
        waitForNode(hasTestTag("organizer-model-2"))
        composeRule.onNodeWithTag("organizer-show-all").performScrollTo().performClick()
        waitForNode(hasTestTag("organizer-model-4"))
        composeRule.onNodeWithTag("organizer-model-4").performScrollTo().performClick()

        waitForNode(hasTestTag("organizer-details"))
        composeRule.onNodeWithTag("organizer-inactive").assertExists()
        composeRule.onNodeWithTag("organizer-select").performScrollTo().assertIsNotEnabled()

        pressSystemBack()
        waitForNode(hasTestTag("organizer-model-4"))
        pressSystemBack()
        waitForNode(hasTestTag("organizer-scan-intro"))
        pressSystemBack()
        composeRule.onNodeWithTag("screen-organizer").assertIsDisplayed()
    }

    @Test
    fun addOrganizerWithoutCameraExplainsAndFallsBackToManualSelection() {
        shadowOf(composeRule.activity.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, false)
        openAddOrganizer()
        waitForNode(hasTestTag("organizer-scan-unavailable"))
        composeRule.onNodeWithText("Aparat jest niedostępny").assertExists()

        composeRule.onNodeWithTag("organizer-choose-manually").performScrollTo().performClick()
        waitForNode(hasTestTag("organizer-model-1"))
        pressSystemBack()
        waitForNode(hasTestTag("organizer-scan-intro"))
        pressSystemBack()
        composeRule.onNodeWithTag("screen-organizer").assertIsDisplayed()
    }

    @Test
    fun deniedCameraPermissionExplainsAndKeepsManualSelection() {
        shadowOf(composeRule.activity.packageManager).setSystemFeature(PackageManager.FEATURE_CAMERA_ANY, true)
        openAddOrganizer()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            shadowOf(composeRule.activity).lastRequestedPermission != null
        }
        val request = shadowOf(composeRule.activity).lastRequestedPermission
        assertEquals(listOf(Manifest.permission.CAMERA), request.requestedPermissions.toList())
        composeRule.runOnIdle {
            composeRule.activity.onRequestPermissionsResult(
                request.requestCode,
                request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
        }

        waitForNode(hasTestTag("organizer-scan-permission"))
        composeRule.onNodeWithText("Brak dostępu do aparatu").assertExists()
        composeRule.onNodeWithTag("organizer-choose-manually").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "pl-rPL-w320dp-h470dp", fontScale = 2f)
    fun organizerCatalogStepsOpenWithTheirHeadingVisibleWithLargeText() {
        startFromWelcome()
        composeRule.onNodeWithTag("tab-organizer").performClick()
        openManualCatalog()
        waitForNode(hasTestTag("organizer-model-4"))
        composeRule.onNodeWithTag("organizer-model-2").performScrollTo().performClick()

        waitForNode(hasTestTag("organizer-details"))
        composeRule.onNodeWithTag("organizer-model-name").assertIsDisplayed()
        composeRule.onNodeWithTag("organizer-select").performScrollTo().performClick()

        composeRule.onNodeWithTag("organizer-confirm-title").assertIsDisplayed()

        pressSystemBack()
        composeRule.onNodeWithTag("organizer-model-name").assertIsDisplayed()
        composeRule.onNodeWithTag("organizer-back-catalog").performScrollTo().performClick()
        waitForNode(hasTestTag("organizer-catalog-intro"))
        composeRule.onNodeWithTag("organizer-catalog-intro").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "en-rUS-w360dp-h640dp")
    fun organizerCatalogUsesEnglishDayAndTimeNames() {
        startFromWelcome()
        composeRule.onNodeWithTag("tab-organizer").performClick()
        openManualCatalog()
        waitForNode(hasTestTag("organizer-model-3"))
        composeRule.onNodeWithTag("organizer-model-3").performScrollTo().performClick()
        waitForNode(hasTestTag("organizer-details"))
        composeRule.onNodeWithText("Compartment layout").assertExists()
        composeRule.onNodeWithTag("organizer-compartment-204")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("noc, Night")))
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
        assertEventuallyDisplayed(hasTestTag("welcome-screen"))
        composeRule.onNodeWithTag("welcome-start").performScrollTo().assertIsDisplayed().performClick()
        assertEventuallyDisplayed(hasTestTag("screen-today"))
    }

    private fun openAuthLogin() {
        assertEventuallyDisplayed(hasTestTag("welcome-screen"))
        composeRule.onNodeWithTag("welcome-login").performScrollTo().performClick()
        composeRule.onNodeWithTag("auth-login").assertIsDisplayed()
    }

    private fun openAuthRegistration() {
        openAuthLogin()
        composeRule.onNodeWithTag("auth-open-register").performScrollTo().performClick()
        composeRule.onNodeWithTag("auth-register").assertIsDisplayed()
    }

    private fun replaceField(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
    }

    private fun assertFieldValue(tag: String, value: String) {
        composeRule.onNodeWithTag(tag)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(value)))
    }

    private fun submitAuth() {
        composeRule.onNodeWithTag("auth-submit").performScrollTo().performClick()
    }

    private fun waitForNode(matcher: SemanticsMatcher) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }

    private fun assertEventuallyDisplayed(matcher: SemanticsMatcher) {
        waitForNode(matcher)
        composeRule.onNode(matcher).assertIsDisplayed()
    }

    private fun openSettings() {
        composeRule.onNodeWithTag("today-open-settings").performScrollTo().performClick()
        assertEventuallyDisplayed(hasTestTag("screen-settings"))
    }

    private fun signOut() {
        composeRule.onNodeWithTag("settings-sign-out").performScrollTo().performClick()
        assertEventuallyDisplayed(hasTestTag("auth-login"))
    }

    private fun assertSimplifiedToday() {
        waitForNode(hasTestTag("simplified-dose"))
        composeRule.onNodeWithTag("simplified-dose").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("08:00", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("today-open-organizer").performScrollTo().assertIsDisplayed()
    }

    private fun openPreparation() {
        composeRule.onNodeWithTag("organizer-open-preparation").performScrollTo().performClick()
        composeRule.onNodeWithTag("screen-preparation").assertIsDisplayed()
    }

    private fun openAddOrganizer() {
        startFromWelcome()
        composeRule.onNodeWithTag("tab-organizer").performClick()
        composeRule.onNodeWithTag("organizer-add").performScrollTo().performClick()
        waitForNode(hasTestTag("organizer-scan-intro"))
    }

    /** The manual choice is offered in every scanner state, with or without a camera. */
    private fun openManualCatalog() {
        composeRule.onNodeWithTag("organizer-add").performScrollTo().performClick()
        waitForNode(hasTestTag("organizer-choose-manually"))
        composeRule.onNodeWithTag("organizer-choose-manually").performScrollTo().performClick()
    }

    private fun pressSystemBack() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
    }
}
