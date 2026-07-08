package io.github.nicechester.gobirdie.ui.scorecards

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.util.concurrent.Executors

// ─── Screen ──────────────────────────────────────────────────────────

/**
 * Full-screen QR scanner. On successful decode, shows a name-entry dialog.
 * Calls [onScanned] with the decoded payload and the host-entered player name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectScoresScreen(
    onScanned: (payload: QrRoundPayload, playerName: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    var scannedPayload by remember { mutableStateOf<QrRoundPayload?>(null) }

    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Name-entry dialog shown after a successful scan
    scannedPayload?.let { payload ->
        PlayerNameDialog(
            payload = payload,
            onConfirm = { name ->
                onScanned(payload, name)
                onDismiss()
            },
            onDismiss = { scannedPayload = null }, // allow re-scan
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Score") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (hasCamera) {
                CameraPreview(
                    onQrDecoded = { json ->
                        if (scannedPayload == null) {
                            runCatching { Json.decodeFromString<QrRoundPayload>(json) }
                                .getOrNull()
                                ?.let { scannedPayload = it }
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    "Point camera at a GoBirdie QR code",
                    style = MaterialTheme.typography.bodySmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp),
                )
            } else {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Camera permission required")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

// ─── Camera preview with ZXing analyzer ─────────────────────────────

@Composable
private fun CameraPreview(onQrDecoded: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, QrAnalyzer(onQrDecoded)) }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis,
                    )
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = modifier,
    )
}

// ─── ZXing image analyzer ────────────────────────────────────────────

private class QrAnalyzer(private val onDecoded: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(mapOf(DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)))
    }

    override fun analyze(image: ImageProxy) {
        val bytes = image.planes[0].buffer.toByteArray()
        val source = PlanarYUVLuminanceSource(
            bytes,
            image.width, image.height,
            0, 0, image.width, image.height,
            false,
        )
        runCatching {
            reader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
        }.onSuccess { result ->
            onDecoded(result.text)
        }
        reader.reset()
        image.close()
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()
        return ByteArray(remaining()).also { get(it) }
    }
}

// ─── Name-entry dialog ───────────────────────────────────────────────

@Composable
private fun PlayerNameDialog(
    payload: QrRoundPayload,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player Name") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${payload.c}  ·  ${payload.d}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${payload.h.sumOf { it.firstOrNull() ?: 0 }} strokes",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Player name") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text("Add Player", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Re-scan") }
        },
    )
}
