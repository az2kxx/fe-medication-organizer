package com.elcomp.medicationorganizer.features.organizers.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable
internal actual fun PlatformQrScanner(
    onEvent: (QrScanEvent) -> Unit,
    contentDescription: String,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val currentOnEvent by rememberUpdatedState(onEvent)
    val hasCamera = remember(context) { context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY) }
    var granted by remember(context) { mutableStateOf(context.hasCameraPermission()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        granted = allowed
        if (!allowed) currentOnEvent(QrScanEvent.PermissionDenied)
    }
    LaunchedEffect(hasCamera) {
        when {
            !hasCamera -> currentOnEvent(QrScanEvent.CameraUnavailable)
            !granted -> permission.launch(Manifest.permission.CAMERA)
        }
    }

    val previewModifier = modifier.semantics { this.contentDescription = contentDescription }
    if (hasCamera && granted) {
        CameraPreview(
            onCode = { currentOnEvent(QrScanEvent.Detected(it)) },
            onFailure = { currentOnEvent(QrScanEvent.CameraUnavailable) },
            modifier = previewModifier,
        )
    } else {
        Box(previewModifier)
    }
}

private fun Context.hasCameraPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraPreview(onCode: (String) -> Unit, onFailure: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnFailure by rememberUpdatedState(onFailure)
    val previewView = remember(context) {
        PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build(),
        )
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
        analysis.setAnalyzer(analysisExecutor) { frame ->
            val image = frame.image
            if (image == null) {
                frame.close()
                return@setAnalyzer
            }
            scanner.process(InputImage.fromMediaImage(image, frame.imageInfo.rotationDegrees))
                .addOnSuccessListener(mainExecutor) { codes ->
                    codes.firstNotNullOfOrNull(Barcode::getRawValue)?.let(currentOnCode)
                }
                .addOnCompleteListener { frame.close() }
        }

        // Disposal runs on the main thread, as does the listener, so this flag stops a late bind.
        var disposed = false
        var provider: ProcessCameraProvider? = null
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get()
                val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis)
                provider = cameraProvider
            } catch (_: Exception) {
                // getInstance and bindToLifecycle report missing or busy cameras with several exception types.
                currentOnFailure()
            }
        }, mainExecutor)

        onDispose {
            disposed = true
            provider?.unbind(preview, analysis)
            analysis.clearAnalyzer()
            scanner.close()
            analysisExecutor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
