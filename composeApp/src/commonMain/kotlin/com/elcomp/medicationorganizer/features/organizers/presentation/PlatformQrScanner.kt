package com.elcomp.medicationorganizer.features.organizers.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

sealed interface QrScanEvent {
    /** Raw text of a QR code seen by the camera. Platforms may report the same code on many frames. */
    data class Detected(val value: String) : QrScanEvent

    data object PermissionDenied : QrScanEvent

    /** The device has no usable camera or the camera could not be started. */
    data object CameraUnavailable : QrScanEvent
}

/**
 * Native camera preview that scans QR codes. It asks for camera permission when needed and reports the outcome
 * through [onEvent]. The camera stops when the composable leaves the composition.
 */
@Composable
internal expect fun PlatformQrScanner(
    onEvent: (QrScanEvent) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
)
