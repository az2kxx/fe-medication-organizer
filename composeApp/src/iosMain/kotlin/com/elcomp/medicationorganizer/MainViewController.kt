package com.elcomp.medicationorganizer

import androidx.compose.ui.window.ComposeUIViewController
import com.elcomp.medicationorganizer.app.App
import com.elcomp.medicationorganizer.app.resolveAppLaunchConfig
import platform.UIKit.UIViewController

fun MainViewController(
    mode: String?,
    baseUrl: String?,
    mockScenario: String?,
): UIViewController {
    val launchConfig = resolveAppLaunchConfig(mode, baseUrl, mockScenario)
    return ComposeUIViewController { App(launchConfig) }
}
