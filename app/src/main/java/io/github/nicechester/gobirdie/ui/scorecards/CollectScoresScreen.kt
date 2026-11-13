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
import androidx.compose.ui.res.stringResource
import io.github.nicechester.gobirdie.R
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
    playerName: String = "",
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
    var cameraError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Name-entry dialog shown after a successful scan (only when no name was pre-supplied)
    scannedPayload?.let { payload ->
        if (playerName.isNotBlank()) {
            LaunchedEffect(payload) {
                onScanned(payload, playerName)
                onDismiss()
            }
        } else {
            PlayerNameDialog(
                payload = payload,
                onConfirm = { name ->
                    onScanned(payload, name)
                    onDismiss()
                },
                onDismiss = { scannedPayload = null },
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.scan_title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, stringResource(R.string.common_close)) }
                },
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (cameraError != null) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.scan_camera_unavailable), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(cameraError ?: stringResource(R.string.common_unknown_error), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.common_back))
                    }
                }
            } else if (hasCamera) {
                CameraPreview(
                    onQrDecoded = { json ->
                        if (scannedPayload == null) {
                            runCatching { Json.decodeFromString<QrRoundPayload>(json) }
                                .getOrNull()
                                ?.let { scannedPayload = it }
                        }
                    },
                    onError = { error -> cameraError = error },
                    modifier = Modifier.fillMaxSize(),
                )
                Text(
                    stringResource(R.string.scan_hint),
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
                    Text(stringResource(R.string.scan_permission_required))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.scan_grant))
                    }
                }
            }
        }
    }
}

// ─── Camera preview with ZXing analyzer ─────────────────────────────

@Composable
private fun CameraPreview(
    onQrDecoded: (String) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = try {
                    cameraProviderFuture.get()
                } catch (e: Exception) {
                    onError(ctx.getString(R.string.scan_error_unavailable, e.message))
                    return@addListener
                }
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
                }.onFailure { e ->
                    onError(ctx.getString(R.string.scan_error_start, e.message))
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
        title = { Text(stringResource(R.string.player_name_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${payload.c}  ·  ${payload.d}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.strokes_count, payload.h.sumOf { it.firstOrNull() ?: 0 }),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.player_name_label)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.player_add), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.scan_rescan)) }
        },
    )
}
