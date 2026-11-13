package com.elcomp.medicationorganizer.features.organizers.presentation

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.UIKitView
import kotlin.coroutines.resume
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVCaptureConnection
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVCaptureDeviceInput
import platform.AVFoundation.AVCaptureMetadataOutput
import platform.AVFoundation.AVCaptureMetadataOutputObjectsDelegateProtocol
import platform.AVFoundation.AVCaptureOutput
import platform.AVFoundation.AVCaptureSession
import platform.AVFoundation.AVCaptureVideoPreviewLayer
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.AVMetadataMachineReadableCodeObject
import platform.AVFoundation.AVMetadataObjectTypeQRCode
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreGraphics.CGRectZero
import platform.UIKit.UIView
import platform.darwin.DISPATCH_QUEUE_PRIORITY_DEFAULT
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_global_queue
import platform.darwin.dispatch_get_main_queue

@Composable
internal actual fun PlatformQrScanner(
    onEvent: (QrScanEvent) -> Unit,
    contentDescription: String,
    modifier: Modifier,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val device = remember { AVCaptureDevice.defaultDeviceWithMediaType(AVMediaTypeVideo) }
    var authorized by remember { mutableStateOf(false) }
    LaunchedEffect(device) {
        if (device == null) {
            currentOnEvent(QrScanEvent.CameraUnavailable)
            return@LaunchedEffect
        }
        authorized = when (AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo)) {
            AVAuthorizationStatusAuthorized -> true
            AVAuthorizationStatusNotDetermined -> suspendCancellableCoroutine { continuation ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                    dispatch_async(dispatch_get_main_queue()) {
                        if (continuation.isActive) continuation.resume(granted)
                    }
                }
            }
            else -> false
        }
        if (!authorized) currentOnEvent(QrScanEvent.PermissionDenied)
    }

    val previewModifier = modifier.semantics { this.contentDescription = contentDescription }
    if (device != null && authorized) {
        CameraPreview(
            device = device,
            onCode = { currentOnEvent(QrScanEvent.Detected(it)) },
            onFailure = { currentOnEvent(QrScanEvent.CameraUnavailable) },
            modifier = previewModifier,
        )
    } else {
        Box(previewModifier)
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
private fun CameraPreview(
    device: AVCaptureDevice,
    onCode: (String) -> Unit,
    onFailure: () -> Unit,
    modifier: Modifier,
) {
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnFailure by rememberUpdatedState(onFailure)
    val capture = remember(device) { QrCapture(device) }
    DisposableEffect(capture) {
        if (!capture.start { currentOnCode(it) }) currentOnFailure()
        onDispose(capture::stop)
    }
    UIKitView(factory = { capture.view }, modifier = modifier)
}

@OptIn(ExperimentalForeignApi::class)
private class QrCapture(private val device: AVCaptureDevice) {
    private val session = AVCaptureSession()
    private val delegate = MetadataDelegate()
    val view = PreviewView(session)

    /** Returns false when the camera input or metadata output cannot be attached. */
    fun start(onCode: (String) -> Unit): Boolean {
        val input = AVCaptureDeviceInput.deviceInputWithDevice(device, null) ?: return false
        if (!session.canAddInput(input)) return false
        session.addInput(input)
        val output = AVCaptureMetadataOutput()
        if (!session.canAddOutput(output)) return false
        session.addOutput(output)
        delegate.onCode = onCode
        output.setMetadataObjectsDelegate(delegate, dispatch_get_main_queue())
        // Supported types are known only after the output joins the session.
        output.metadataObjectTypes = listOf(AVMetadataObjectTypeQRCode)
        // startRunning blocks until the camera is ready, so it must not run on the main thread.
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            session.startRunning()
        }
        return true
    }

    fun stop() {
        delegate.onCode = null
        dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT.toLong(), 0u)) {
            session.stopRunning()
        }
    }
}

private class MetadataDelegate : NSObject(), AVCaptureMetadataOutputObjectsDelegateProtocol {
    var onCode: ((String) -> Unit)? = null

    override fun captureOutput(
        output: AVCaptureOutput,
        didOutputMetadataObjects: List<*>,
        fromConnection: AVCaptureConnection,
    ) {
        val code = didOutputMetadataObjects.firstNotNullOfOrNull {
            (it as? AVMetadataMachineReadableCodeObject)?.stringValue
        } ?: return
        onCode?.invoke(code)
    }
}

@OptIn(ExperimentalForeignApi::class)
private class PreviewView(session: AVCaptureSession) : UIView(frame = CGRectZero.readValue()) {
    private val previewLayer = AVCaptureVideoPreviewLayer(session = session).apply {
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(previewLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        previewLayer.setFrame(bounds)
    }
}
