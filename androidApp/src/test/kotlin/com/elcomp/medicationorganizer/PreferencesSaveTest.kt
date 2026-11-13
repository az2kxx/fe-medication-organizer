package com.elcomp.medicationorganizer

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.elcomp.medicationorganizer.app.App
import com.elcomp.medicationorganizer.core.network.AppServices
import com.elcomp.medicationorganizer.features.profile.data.DemoPreferencesStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "pl-rPL-w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PreferencesSaveTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun leavingSettingsDuringSaveStillUpdatesTheVisiblePreferences() = withDelayedStore { services, store ->
        startContrastSave(store)
        backToToday()
        store.finishWrite.complete(Unit)

        // Confirm that persistence finished before checking the separately held UI state.
        runBlocking {
            withTimeout(5_000) {
                assertTrue(requireNotNull(services.profile).preferences().highContrast)
            }
        }
        openSettings()
        composeRule.onNodeWithTag("preferences-high-contrast").performScrollTo().assertIsOn()
    }

    @Test
    fun returningDuringSaveBlocksAnotherWriteAndPreservesAConcurrentProfileChange() =
        withDelayedStore { _, store ->
            startContrastSave(store)
            backToToday()
            openSettings()
            composeRule.onNodeWithTag("preferences-high-contrast").performScrollTo().assertIsNotEnabled()
            composeRule.onNodeWithTag("preferences-save").performScrollTo().assertIsNotEnabled()

            composeRule.onNodeWithTag("navigation-open-profile").performScrollTo().performClick()
            waitFor("profile-name")
            composeRule.onNodeWithTag("profile-name").performTextReplacement("Nowa nazwa")
            composeRule.onNodeWithTag("profile-submit").performScrollTo().performClick()
            store.finishWrite.complete(Unit)

            waitFor("screen-settings")
            composeRule.onNodeWithText("Nazwa wyświetlana: Nowa nazwa").performScrollTo().assertExists()
            composeRule.onNodeWithTag("preferences-high-contrast").performScrollTo().assertIsOn().assertIsEnabled()
        }

    private fun withDelayedStore(test: (AppServices, DelayedPreferencesStore) -> Unit) {
        val store = DelayedPreferencesStore()
        val services = AppServices.create(demoPreferencesStore = store)
        try {
            composeRule.activityRule.scenario.onActivity { it.setContent { App(services, false) } }
            waitFor("welcome-start")
            composeRule.onNodeWithTag("welcome-start").performScrollTo().performClick()
            waitFor("today-open-settings")
            test(services, store)
        } finally {
            store.finishWrite.complete(Unit)
            composeRule.activityRule.scenario.onActivity { it.setContent {} }
            services.close()
        }
    }

    private fun startContrastSave(store: DelayedPreferencesStore) {
        openSettings()
        composeRule.onNodeWithTag("preferences-high-contrast").performScrollTo().performClick()
        composeRule.onNodeWithTag("preferences-save").performScrollTo().performClick()
        composeRule.waitUntil(5_000) { store.writeStarted.isCompleted }
    }

    private fun backToToday() {
        composeRule.runOnIdle { composeRule.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("today-open-settings")
    }

    private fun openSettings() {
        composeRule.onNodeWithTag("today-open-settings").performScrollTo().performClick()
        waitFor("preferences-high-contrast")
    }

    private fun waitFor(tag: String) {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag(tag))
                .fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
        }
    }
}

private class DelayedPreferencesStore : DemoPreferencesStore {
    val writeStarted = CompletableDeferred<Unit>()
    val finishWrite = CompletableDeferred<Unit>()
    private var record: String? = null

    override suspend fun read(): String? = record

    override suspend fun write(record: String) {
        writeStarted.complete(Unit)
        finishWrite.await()
        this.record = record
    }

    override suspend fun clear() {
        record = null
    }
}
