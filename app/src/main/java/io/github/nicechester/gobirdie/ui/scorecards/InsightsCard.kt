package io.github.nicechester.gobirdie.ui.scorecards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nicechester.gobirdie.core.model.*

@Composable
fun InsightsCard(round: Round, courseHoles: List<Hole> = emptyList()) {
    val insights = RoundInsightsEngine.generate(round, courseHoles)
    if (insights.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "💡 Key Insights",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            insights.forEach { insight ->
                Row {
                    Text(icon(insight.severity), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Text(insight.message, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun icon(severity: RoundInsight.Severity): String = when (severity) {
    RoundInsight.Severity.CRITICAL -> "🔴"
    RoundInsight.Severity.WARNING -> "🟡"
    RoundInsight.Severity.POSITIVE -> "🟢"
    RoundInsight.Severity.INFO -> "🔵"
}
