package com.cashmemer.scan

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.cashmemer.R
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
                    onBarcodes = { codes ->
                        setResult(
                            Activity.RESULT_OK,
                            Intent().putStringArrayListExtra(
                                EXTRA_BARCODES,
                                ArrayList(codes),
                            ),
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
        const val EXTRA_BARCODES = "barcodes"
    }
}

@Composable
private fun ScanScreen(
    mode: ScanMode,
    onCaptured: (Uri) -> Unit,
    onBarcodes: (List<String>) -> Unit,
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
        if (!granted) error = context.getString(R.string.camera_permission_needed)
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
                text = error ?: stringResource(R.string.waiting_for_camera),
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onCancel, modifier = Modifier.padding(top = 16.dp)) {
                Text(stringResource(R.string.action_back))
            }
        }
        return
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    val executor = remember { Executors.newSingleThreadExecutor() }

    // Every code accepted this session. Repeats are kept on purpose: scanning
    // the same pack of crisps three times means quantity three.
    val scanned = remember { mutableStateListOf<String>() }
    val haptics = remember { Haptics(context) }

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
                    // The camera stays bound for the whole session. Closing it
                    // after one code meant reopening it by hand for every item,
                    // which is not scanning, it is typing with extra steps.
                    buildBarcodeAnalysis(executor) { value ->
                        scanned += value
                        haptics.tick()
                    },
                )
            }

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                *useCases,
            )
        }.onFailure { error = it.message ?: context.getString(R.string.camera_failed) }
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
                    Text(stringResource(R.string.capture))
                }

                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
                }
            } else {
                ScanTally(scanned = scanned, onUndo = { scanned.removeLastOrNull() })

                Button(
                    onClick = { onBarcodes(scanned.toList()) },
                    enabled = scanned.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(stringResource(R.string.done_adding, scanned.size))
                }

                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

/**
 * The running tally over the viewfinder.
 *
 * Without it there is no way to tell a scan that registered from one that did
 * not, and the only recovery is to start the whole basket again.
 */
@Composable
private fun ScanTally(scanned: SnapshotStateList<String>, onUndo: () -> Unit) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (scanned.isEmpty()) {
                    stringResource(R.string.point_at_barcode)
                } else {
                    stringResource(R.string.scanned_so_far, scanned.size)
                },
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
            )

            if (scanned.isNotEmpty()) {
                // Grouped, so three of the same thing reads as one line ×3 —
                // the same shape it will take on the receipt.
                val counts = scanned.groupingBy { it }.eachCount()
                counts.entries.takeLast(4).forEach { (code, count) ->
                    Text(
                        text = "$code  ×$count",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                OutlinedButton(
                    onClick = onUndo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) { Text(stringResource(R.string.undo_last_scan)) }
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

    // The analyser sees the same barcode in every frame while it is in shot —
    // perhaps thirty times a second. Without a quiet period, holding one packet
    // steady would add it dozens of times. Two seconds is long enough to move
    // the next item into frame and short enough to scan a second identical
    // packet deliberately.
    var lastCode: String? = null
    var lastAt = 0L

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
                        val value = barcodes.firstNotNullOfOrNull { it.rawValue }
                            ?: return@addOnSuccessListener
                        val now = System.currentTimeMillis()
                        if (value == lastCode && now - lastAt < REPEAT_QUIET_MILLIS) {
                            return@addOnSuccessListener
                        }
                        lastCode = value
                        lastAt = now
                        onBarcode(value)
                    }
                    .addOnCompleteListener { proxy.close() }
            }
        }
}

/** How long the same code is ignored for after being counted. */
private const val REPEAT_QUIET_MILLIS = 2_000L

/** A short buzz on each accepted scan, so eyes can stay on the goods. */
private class Haptics(context: Context) {

    private val vibrator = ContextCompat.getSystemService(context, Vibrator::class.java)

    fun tick() {
        val effect = VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)
        runCatching { vibrator?.vibrate(effect) }
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
                    onError(exception.message ?: context.getString(R.string.capture_failed))
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

/**
 * Launches the camera in barcode mode and returns every code scanned before
 * Done was tapped. Repeats are meaningful — three of the same code is three of
 * that item.
 */
class ScanBarcodeContract : ActivityResultContract<Unit, List<String>>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(context, ScanActivity::class.java)
            .putExtra(ScanActivity.EXTRA_MODE, ScanMode.SCAN_BARCODE.name)

    override fun parseResult(resultCode: Int, intent: Intent?): List<String> =
        if (resultCode == Activity.RESULT_OK) {
            intent?.getStringArrayListExtra(ScanActivity.EXTRA_BARCODES).orEmpty()
        } else {
            emptyList()
        }
}
