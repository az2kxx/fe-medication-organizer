package com.elcomp.medicationorganizer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launcherShowsSharedStartScreen() {
        composeRule.onNodeWithText("EL COMP").assertIsDisplayed()
        composeRule.onNodeWithText("Cyfrowa kasetka").assertIsDisplayed()
        composeRule.onNodeWithText("Tryb demo (MOCK)").assertIsDisplayed()
    }
}
