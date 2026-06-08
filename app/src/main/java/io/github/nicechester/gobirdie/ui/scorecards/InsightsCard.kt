package io.github.nicechester.gobirdie.ui.scorecards

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.nicechester.gobirdie.core.model.*

@Composable
fun InsightsCard(
    round: Round,
    courseHoles: List<Hole> = emptyList(),
    historicalRounds: List<Round> = emptyList(),
    baseline: SGBaseline = SGBaseline.BOGEY,
) {
    val insights = RoundInsightsEngine.generate(round, courseHoles, historicalRounds, baseline)
    if (insights.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "💡 Key Insights",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "vs ${baseline.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
            Spacer(Modifier.height(8.dp))
            insights.forEachIndexed { index, insight ->
                Row {
                    Text(icon(insight.severity), style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Text(insight.message, style = MaterialTheme.typography.bodySmall)
                }
                if (index < insights.lastIndex) {
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), thickness = 0.5.dp)
                }
            }
        }
    }
}

private fun icon(severity: RoundInsight.Severity): String = when (severity) {
    RoundInsight.Severity.CRITICAL -> "🔴"
    RoundInsight.Severity.WARNING  -> "🟡"
    RoundInsight.Severity.POSITIVE -> "🟢"
    RoundInsight.Severity.INFO     -> "🔵"
}
