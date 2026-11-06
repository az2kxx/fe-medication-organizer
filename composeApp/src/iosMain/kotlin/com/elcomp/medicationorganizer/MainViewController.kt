package com.elcomp.medicationorganizer

import androidx.compose.ui.window.ComposeUIViewController
import com.elcomp.medicationorganizer.app.App
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController { App() }
