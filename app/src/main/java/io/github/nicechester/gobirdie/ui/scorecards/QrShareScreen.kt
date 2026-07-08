package io.github.nicechester.gobirdie.ui.scorecards

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.github.nicechester.gobirdie.core.model.Round
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ─── Compact QR payload ──────────────────────────────────────────────
// {"c":"Hansen Dam","d":"2026-06-25","h":[[4,2],[3,1],...]}
// ~130-180 bytes for 18 holes — fits in QR version 10

@Serializable
data class QrRoundPayload(
    val c: String,           // course name
    val d: String,           // date yyyy-MM-dd
    val h: List<List<Int>>,  // [[strokes, putts], ...] per hole
)

private val dateOnlyFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

fun Round.toQrPayload(): String {
    val date = runCatching {
        Instant.parse(startedAt).atZone(ZoneId.systemDefault()).format(dateOnlyFormatter)
    }.getOrDefault(startedAt.take(10))
    val payload = QrRoundPayload(
        c = courseName,
        d = date,
        h = holes.map { listOf(it.strokes, it.putts) },
    )
    return Json.encodeToString(payload)
}

fun generateQrBitmap(content: String, sizePx: Int): Bitmap {
    val hints = mapOf(EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
    for (x in 0 until sizePx) {
        for (y in 0 until sizePx) {
            bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }
    return bmp
}

// ─── Screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareScreen(round: Round, onDismiss: () -> Unit) {
    val qrContent = remember(round.id) { round.toQrPayload() }
    val qrBitmap = remember(qrContent) { generateQrBitmap(qrContent, 512) }
    val date = remember(round.startedAt) {
        runCatching {
            Instant.parse(round.startedAt).atZone(ZoneId.systemDefault()).format(dateOnlyFormatter)
        }.getOrDefault(round.startedAt.take(10))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Score") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                },
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
            ) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code for ${round.courseName}",
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                round.courseName,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                date,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "${round.totalStrokes} strokes",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
            )

            Spacer(Modifier.height(32.dp))

            Text(
                "Have the tournament host scan this code",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
