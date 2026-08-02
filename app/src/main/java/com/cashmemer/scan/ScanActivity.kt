package com.cashmemer.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.UseCase
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cashmemer.core.ui.theme.CashMemerTheme
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.util.concurrent.Executors

/** What the camera screen is being opened for. */
enum class ScanMode { CAPTURE_RECEIPT, SCAN_BARCODE }

/**
 * One camera screen serving both jobs: photographing a paper receipt for the
 * OCR parser, and reading a product barcode into the receipt form.
 */
class ScanActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = runCatching {
            ScanMode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
        }.getOrDefault(ScanMode.CAPTURE_RECEIPT)

        setContent {
            CashMemerTheme {
                ScanScreen(
                    mode = mode,
                    onCaptured = { uri ->
                        setResult(Activity.RESULT_OK, Intent().setData(uri))
                        finish()
                    },
                    onBarcode = { value ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putExtra(EXTRA_BARCODE, value),
                        )
                        finish()
                    },
                    onCancel = { finish() },
                )
            }
        }
    }

    companion object {
        const val EXTRA_MODE = "scan_mode"
        const val EXTRA_BARCODE = "barcode"
    }
}

@Composable
private fun ScanScreen(
    mode: ScanMode,
    onCaptured: (Uri) -> Unit,
    onBarcode: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) error = "Camera permission is needed to scan"
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = error ?: "Waiting for camera permission…",
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) {
                Text("Back")
            }
        }
        return
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }
    // Guards against the analyser firing twice before the activity finishes.
    var barcodeHandled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    val previewView = remember { PreviewView(context) }

    // getInstance returns a Guava ListenableFuture; concurrent-futures-ktx both
    // supplies that class and gives it a suspend await(), so the binding reads
    // as straight-line code instead of a listener callback.
    LaunchedEffect(mode) {
        runCatching {
            val provider = ProcessCameraProvider.getInstance(context).await()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val useCases: Array<UseCase> = if (mode == ScanMode.CAPTURE_RECEIPT) {
                arrayOf(preview, imageCapture)
            } else {
                arrayOf(
                    preview,
                    buildBarcodeAnalysis(executor) { value ->
                        if (!barcodeHandled) {
                            barcodeHandled = true
                            onBarcode(value)
                        }
                    },
                )
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                *useCases,
            )
        }.onFailure { error = it.message ?: "Could not start the camera" }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { previewView },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error)
            }

            if (mode == ScanMode.CAPTURE_RECEIPT) {
                Button(
                    onClick = {
                        captureTo(context, imageCapture, executor, onCaptured) {
                            error = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Camera, contentDescription = null)
                    Text("  Capture")
                }
            } else {
                Text(
                    text = "Point the camera at a barcode",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }

            Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }
        }
    }
}

// ImageProxy.getImage() is opt-in; the analyser cannot reach the frame without it.
@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun buildBarcodeAnalysis(
    executor: java.util.concurrent.Executor,
    onBarcode: (String) -> Unit,
): ImageAnalysis {
    val scanner = BarcodeScanning.getClient()

    return ImageAnalysis.Builder()
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()
        .apply {
            setAnalyzer(executor) { proxy: ImageProxy ->
                val mediaImage = proxy.image
                if (mediaImage == null) {
                    proxy.close()
                    return@setAnalyzer
                }

                val image = InputImage.fromMediaImage(
                    mediaImage,
                    proxy.imageInfo.rotationDegrees,
                )

                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        barcodes.firstNotNullOfOrNull { it.rawValue }?.let(onBarcode)
                    }
                    .addOnCompleteListener { proxy.close() }
            }
        }
}

private fun captureTo(
    context: Context,
    imageCapture: ImageCapture,
    executor: java.util.concurrent.Executor,
    onCaptured: (Uri) -> Unit,
    onError: (String) -> Unit,
) {
    val dir = File(context.cacheDir, "receipts").apply { mkdirs() }
    val file = File(dir, "scan-${System.currentTimeMillis()}.jpg")

    imageCapture.takePicture(
        ImageCapture.OutputFileOptions.Builder(file).build(),
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                ContextCompat.getMainExecutor(context).execute { onCaptured(uri) }
            }

            override fun onError(exception: ImageCaptureException) {
                ContextCompat.getMainExecutor(context).execute {
                    onError(exception.message ?: "Capture failed")
                }
            }
        },
    )
}

/** Launches the camera for a receipt photo and returns where it was saved. */
class CaptureReceiptContract : ActivityResultContract<Unit, Uri?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, ScanActivity::class.java)
            .putExtra(ScanActivity.EXTRA_MODE, ScanMode.CAPTURE_RECEIPT.name)

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? =
        if (resultCode == Activity.RESULT_OK) intent?.data else null
}

/** Launches the camera in barcode mode and returns the first code read. */
class ScanBarcodeContract : ActivityResultContract<Unit, String?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, ScanActivity::class.java)
            .putExtra(ScanActivity.EXTRA_MODE, ScanMode.SCAN_BARCODE.name)

    override fun parseResult(resultCode: Int, intent: Intent?): String? =
        if (resultCode == Activity.RESULT_OK) {
            intent?.getStringExtra(ScanActivity.EXTRA_BARCODE)
        } else {
            null
        }
}
