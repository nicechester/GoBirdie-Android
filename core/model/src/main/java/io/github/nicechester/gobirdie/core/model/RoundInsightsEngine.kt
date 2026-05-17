package io.github.nicechester.gobirdie.core.model

data class RoundInsight(
    val severity: Severity,
    val message: String,
) {
    enum class Severity { CRITICAL, WARNING, POSITIVE, INFO }
}

object RoundInsightsEngine {

    fun generate(round: Round, courseHoles: List<Hole> = emptyList()): List<RoundInsight> {
        val played = round.holes.filter { it.strokes > 0 }
        if (played.size < 4) return emptyList()

        val holesPlayed = played.size
        val totalPutts = played.sumOf { it.putts }

        val threePutts = played.count { it.putts >= 3 }
        val onePutts = played.count { it.putts == 1 }

        // FIR (par 4+ only)
        val firHoles = played.filter { it.par >= 4 }
        val firCount = firHoles.count { it.fairwayHit == true }
        val firPct = if (firHoles.isEmpty()) 0 else firCount * 100 / firHoles.size

        // GIR
        val girCount = played.count { it.computedGir }
        val girPct = girCount * 100 / holesPlayed

        // Scrambling
        val missedGir = played.filter { !it.computedGir }
        val scrambled = missedGir.count { it.strokes <= it.par }
        val scramblingPct = if (missedGir.isEmpty()) 100 else scrambled * 100 / missedGir.size

        // Front/back split
        val front = played.filter { it.number <= 9 }
        val back = played.filter { it.number > 9 }
        val frontScore = if (front.size >= 8) front.sumOf { it.strokes } else null
        val backScore = if (back.size >= 8) back.sumOf { it.strokes } else null

        // Par 3 / Par 5 averages
        val par3s = played.filter { it.par == 3 }
        val par5s = played.filter { it.par == 5 }
        val par3Avg = if (par3s.isEmpty()) 0.0 else par3s.sumOf { it.scoreVsPar }.toDouble() / par3s.size
        val par5Avg = if (par5s.isEmpty()) 0.0 else par5s.sumOf { it.scoreVsPar }.toDouble() / par5s.size

        // Consecutive bogeys
        var maxConsecBogeys = 0
        var streak = 0
        for (h in played.sortedBy { it.number }) {
            if (h.scoreVsPar > 0) { streak++; maxConsecBogeys = maxOf(maxConsecBogeys, streak) }
            else { streak = 0 }
        }

        // Longest drive
        var longestDriveYards: Int? = null
        for (hole in played) {
            val sorted = hole.shots.sortedBy { it.sequence }
            val first = sorted.firstOrNull() ?: continue
            if (first.club != ClubType.DRIVER || sorted.size < 2) continue
            val yards = first.location.distanceYards(sorted[1].location)
            if (yards > (longestDriveYards ?: 0)) longestDriveYards = yards
        }

        // --- Evaluate insights ---
        val insights = mutableListOf<RoundInsight>()

        if (threePutts >= 3) {
            insights += RoundInsight(RoundInsight.Severity.CRITICAL, "$threePutts three-putts — that's $threePutts strokes given away. Lag putting needs work.")
        } else if (threePutts == 2) {
            insights += RoundInsight(RoundInsight.Severity.WARNING, "$threePutts three-putts today. Getting the first putt within 3 feet would save strokes.")
        }

        if (firPct >= 55 && girPct < 35) {
            insights += RoundInsight(RoundInsight.Severity.CRITICAL, "Hit $firPct% fairways but only $girPct% greens — irons aren't converting good drives.")
        }

        if (scramblingPct < 25 && girPct < 45) {
            insights += RoundInsight(RoundInsight.Severity.CRITICAL, "Only $scramblingPct% scrambling on missed greens. Short game practice would have a big impact.")
        } else if (scramblingPct >= 50 && girPct < 45) {
            insights += RoundInsight(RoundInsight.Severity.POSITIVE, "Only $girPct% GIR but scrambled $scramblingPct% — short game saved the round.")
        }

        if (frontScore != null && backScore != null) {
            if (backScore > frontScore + 4) {
                insights += RoundInsight(RoundInsight.Severity.WARNING, "Front $frontScore, back $backScore — a ${backScore - frontScore} shot drop-off. Fatigue may be a factor.")
            } else if (frontScore > backScore + 3) {
                insights += RoundInsight(RoundInsight.Severity.POSITIVE, "Strong finish — improved ${frontScore - backScore} shots from front ($frontScore) to back ($backScore).")
            }
        }

        if (par3s.size >= 2 && par3Avg > 0.8) {
            insights += RoundInsight(RoundInsight.Severity.WARNING, "Par 3s averaged +${"%.1f".format(par3Avg)} — iron accuracy from the tee needs attention.")
        }

        if (par5s.size >= 2 && par5Avg > 0.5) {
            insights += RoundInsight(RoundInsight.Severity.WARNING, "Par 5s averaged +${"%.1f".format(par5Avg)} — not capitalizing on scoring holes.")
        } else if (par5s.size >= 2 && par5Avg < -0.3) {
            insights += RoundInsight(RoundInsight.Severity.POSITIVE, "Par 5s averaged ${"%.1f".format(par5Avg)} — great scoring on the long holes.")
        }

        if (maxConsecBogeys >= 3) {
            insights += RoundInsight(RoundInsight.Severity.WARNING, "$maxConsecBogeys bogeys in a row — breaking bad streaks early is key.")
        }

        if (onePutts >= 4) {
            insights += RoundInsight(RoundInsight.Severity.POSITIVE, "$onePutts one-putts today — excellent close-range putting.")
        }

        if (threePutts == 0 && totalPutts <= (holesPlayed * 1.8).toInt()) {
            insights += RoundInsight(RoundInsight.Severity.POSITIVE, "Zero three-putts — lag putting distance control was excellent.")
        }

        if (longestDriveYards != null && longestDriveYards >= 270) {
            insights += RoundInsight(RoundInsight.Severity.POSITIVE, "Longest drive: $longestDriveYards yards — great power off the tee.")
        }

        // Rank: critical first, then warning, then positive/info. Return top 3.
        insights.sortBy { it.severity.ordinal }

        val negatives = insights.filter { it.severity == RoundInsight.Severity.CRITICAL || it.severity == RoundInsight.Severity.WARNING }
        val positives = insights.filter { it.severity == RoundInsight.Severity.POSITIVE || it.severity == RoundInsight.Severity.INFO }
        return if (negatives.isEmpty()) {
            positives.take(3)
        } else {
            (negatives.take(2) + positives.take(1)).take(3)
        }
    }
}
