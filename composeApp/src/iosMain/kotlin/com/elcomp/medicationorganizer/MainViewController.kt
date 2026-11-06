package com.elcomp.medicationorganizer

import androidx.compose.ui.window.ComposeUIViewController
import com.elcomp.medicationorganizer.app.App
import com.elcomp.medicationorganizer.app.resolveAppLaunchConfig
import com.elcomp.medicationorganizer.core.network.ApiSettings
import com.elcomp.medicationorganizer.core.session.KeychainSessionTokenStore
import platform.UIKit.UIViewController

fun MainViewController(
    mode: String?,
    baseUrl: String?,
    mockScenario: String?,
): UIViewController {
    val launchConfig = resolveAppLaunchConfig(mode, baseUrl, mockScenario)
    val sessionStore = if (launchConfig.settings is ApiSettings.Live) {
        KeychainSessionTokenStore()
    } else {
        null
    }
    return ComposeUIViewController { App(launchConfig, sessionStore) }
}
