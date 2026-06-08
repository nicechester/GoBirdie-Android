package io.github.nicechester.gobirdie.core.model

import kotlin.math.abs
import kotlin.math.sqrt

data class RoundInsight(
    val code: String,
    val severity: Severity,
    val tier: Int,
    val message: String,
) {
    enum class Severity { CRITICAL, WARNING, POSITIVE, INFO }
}

object RoundInsightsEngine {

    fun generate(
        round: Round,
        courseHoles: List<Hole> = emptyList(),
        historicalRounds: List<Round> = emptyList(),
        baseline: SGBaseline = SGBaseline.BOGEY,
    ): List<RoundInsight> {
        val played = round.holes.filter { it.strokes > 0 }
        if (played.size < 4) return emptyList()

        val ctx = buildContext(round, played, historicalRounds, baseline)
        return evaluate(ctx)
    }

    // ── Context ──────────────────────────────────────────────────────────────

    private data class ClubStat(val name: String, val avgSg: Double, val shots: Int, val distStd: Double?)

    private data class Context(
        val holesPlayed: Int,
        val girPct: Int,
        val fir: Int,
        val threePutts: Int,
        val onePutts: Int,
        val scramblingPct: Int,
        val frontScore: Int?,
        val backScore: Int?,
        val par3AvgOverPar: Double,
        val par5AvgOverPar: Double,
        val maxConsecBogeys: Int,
        val totalPutts: Int,
        val sgOffTee: Double,
        val sgApproach: Double,
        val sgShortGame: Double,
        val sgPutting: Double,
        val bestClub: ClubStat?,
        val worstClub: ClubStat?,
        val driverDistStd: Double?,
        val earlyRoundHr: Double?,
        val lateRoundHr: Double?,
        val histDriverDiff: Double?,
        val histApproachDiff: Double?,
        val longestDriveYards: Int?,
        val durationMin: Double?,
    ) {
        val sgTotal get() = sgOffTee + sgApproach + sgShortGame + sgPutting
    }

    private fun buildContext(
        round: Round,
        played: List<HoleScore>,
        historicalRounds: List<Round>,
        baseline: SGBaseline,
    ): Context {
        val holesPlayed = played.size
        val totalPutts = played.sumOf { it.putts }
        val threePutts = played.count { it.putts >= 3 }
        val onePutts = played.count { it.putts == 1 }

        val firHoles = played.filter { it.par >= 4 }
        val firPct = if (firHoles.isEmpty()) 0 else firHoles.count { it.fairwayHit == true } * 100 / firHoles.size
        val girCount = played.count { it.computedGir }
        val girPct = girCount * 100 / holesPlayed

        val missedGir = played.filter { !it.computedGir }
        val scramblingPct = if (missedGir.isEmpty()) 100
            else missedGir.count { it.strokes <= it.par } * 100 / missedGir.size

        val front = played.filter { it.number <= 9 }
        val back = played.filter { it.number > 9 }
        val frontScore = if (front.size >= 8) front.sumOf { it.strokes } else null
        val backScore = if (back.size >= 8) back.sumOf { it.strokes } else null

        val par3s = played.filter { it.par == 3 }
        val par5s = played.filter { it.par == 5 }
        val par3Avg = if (par3s.isEmpty()) 0.0 else par3s.sumOf { it.scoreVsPar }.toDouble() / par3s.size
        val par5Avg = if (par5s.isEmpty()) 0.0 else par5s.sumOf { it.scoreVsPar }.toDouble() / par5s.size

        var maxConsec = 0; var streak = 0
        for (h in played.sortedBy { it.number }) {
            if (h.scoreVsPar > 0) { streak++; maxConsec = maxOf(maxConsec, streak) } else streak = 0
        }

        val exp = baseline.expected
        val sgOffTee   = estimateSGOffTee(firHoles, firPct)   - exp.offTee
        val sgApproach = estimateSGApproach(played, girPct)   - exp.approach
        val sgShortGame = estimateSGShortGame(played, missedGir) - exp.shortGame
        val sgPutting  = estimateSGPutting(played, holesPlayed) - exp.putting

        val clubStats = buildClubStats(played)
        val best  = clubStats.filter { it.shots >= 2 && it.avgSg > 0.2 }.maxByOrNull { it.avgSg }
        val worst = clubStats.filter { it.shots >= 2 && it.avgSg < -0.2 }.minByOrNull { it.avgSg }
        val driverStats = clubStats.firstOrNull { it.name == ClubType.DRIVER.displayName }

        val hrSorted = round.heartRateTimeline.sortedBy { it.timestamp }
        val earlyHr = hrSorted.take(maxOf(1, hrSorted.size / 3)).map { it.bpm.toDouble() }.average().takeIf { hrSorted.isNotEmpty() }
        val lateHr  = hrSorted.takeLast(maxOf(1, hrSorted.size / 3)).map { it.bpm.toDouble() }.average().takeIf { hrSorted.isNotEmpty() }

        val history = historicalRounds.filter { it.id != round.id }.take(10)
        val histDriverDiff = computeDriverDiff(round, history)
        val histApproachDiff = computeApproachDiff(round, history)

        var longestDrive: Int? = null
        for (hole in played) {
            val shots = hole.shots.sortedBy { it.sequence }
            val first = shots.firstOrNull() ?: continue
            if (first.club != ClubType.DRIVER || shots.size < 2) continue
            val yards = first.location.distanceYards(shots[1].location)
            if (yards > 50) longestDrive = maxOf(longestDrive ?: 0, yards)
        }

        val durationMin = try {
            val start = java.time.Instant.parse(round.startedAt)
            val end = round.endedAt?.let { java.time.Instant.parse(it) }
            end?.let { (it.epochSecond - start.epochSecond) / 60.0 }
        } catch (_: Exception) { null }

        return Context(
            holesPlayed = holesPlayed, girPct = girPct, fir = firPct,
            threePutts = threePutts, onePutts = onePutts, scramblingPct = scramblingPct,
            frontScore = frontScore, backScore = backScore,
            par3AvgOverPar = par3Avg, par5AvgOverPar = par5Avg,
            maxConsecBogeys = maxConsec, totalPutts = totalPutts,
            sgOffTee = sgOffTee, sgApproach = sgApproach, sgShortGame = sgShortGame, sgPutting = sgPutting,
            bestClub = best, worstClub = worst, driverDistStd = driverStats?.distStd,
            earlyRoundHr = earlyHr, lateRoundHr = lateHr,
            histDriverDiff = histDriverDiff, histApproachDiff = histApproachDiff,
            longestDriveYards = longestDrive, durationMin = durationMin,
        )
    }

    // ── SG Estimators ─────────────────────────────────────────────────────────

    private fun estimateSGOffTee(firHoles: List<HoleScore>, firPct: Int): Double {
        if (firHoles.isEmpty()) return 0.0
        return (firPct / 100.0 - 0.60) * 3.0
    }

    private fun estimateSGApproach(played: List<HoleScore>, girPct: Int): Double {
        return (girPct / 100.0 - 0.65) * 5.0
    }

    private fun estimateSGShortGame(played: List<HoleScore>, missedGir: List<HoleScore>): Double {
        if (missedGir.isEmpty()) return 0.5
        val scramblePct = missedGir.count { it.strokes <= it.par }.toDouble() / missedGir.size
        return (scramblePct - 0.58) * 4.0
    }

    private fun estimateSGPutting(played: List<HoleScore>, holesPlayed: Int): Double {
        if (holesPlayed == 0) return 0.0
        val avgPutts = played.sumOf { it.putts }.toDouble() / holesPlayed
        return (1.73 - avgPutts) * 3.0
    }

    // ── Club Analysis ─────────────────────────────────────────────────────────

    private fun buildClubStats(played: List<HoleScore>): List<ClubStat> {
        val data = mutableMapOf<ClubType, MutableList<Pair<Double, Double>>>()
        for (hole in played) {
            val shots = hole.shots.sortedBy { it.sequence }
            for (i in shots.indices) {
                val shot = shots[i]
                if (shot.club == ClubType.UNKNOWN || shot.club == ClubType.PUTTER) continue
                val dist = if (i + 1 < shots.size)
                    shot.location.distanceYards(shots[i + 1].location).toDouble() else 0.0
                val sgProxy = shot.distanceToPinYards?.let {
                    when { it < 20 -> 0.4; it < 40 -> 0.1; else -> -0.1 }
                } ?: if (hole.computedGir) 0.1 else -0.1
                data.getOrPut(shot.club) { mutableListOf() }.add(sgProxy to dist)
            }
        }
        return data.mapNotNull { (club, entries) ->
            if (entries.size < 2) return@mapNotNull null
            val avgSg = entries.map { it.first }.average()
            val dists = entries.map { it.second }.filter { it > 0 }
            val distStd = if (dists.size >= 3) stdDev(dists) else null
            ClubStat(club.displayName, avgSg, entries.size, distStd)
        }
    }

    // ── Historical helpers ────────────────────────────────────────────────────

    private fun computeDriverDiff(round: Round, history: List<Round>): Double? {
        if (history.size < 3) return null
        val curr = trimmedMean(driverDistances(listOf(round))) ?: return null
        val hist = trimmedMean(driverDistances(history)) ?: return null
        return curr - hist
    }

    private fun computeApproachDiff(round: Round, history: List<Round>): Double? {
        if (history.size < 3) return null
        val curr = trimmedMean(approachProximities(listOf(round))) ?: return null
        val hist = trimmedMean(approachProximities(history)) ?: return null
        return curr - hist
    }

    private fun driverDistances(rounds: List<Round>): List<Double> = rounds.flatMap { r ->
        r.holes.mapNotNull { hole ->
            val shots = hole.shots.sortedBy { it.sequence }
            if (shots.size >= 2 && shots[0].club == ClubType.DRIVER) {
                val yards = shots[0].location.distanceYards(shots[1].location).toDouble()
                if (yards > 50) yards else null
            } else null
        }
    }

    private fun approachProximities(rounds: List<Round>): List<Double> = rounds.flatMap { r ->
        r.holes.mapNotNull { hole ->
            val shots = hole.shots.sortedBy { it.sequence }
            if (shots.size < 2) return@mapNotNull null
            val nonPutts = shots.filter { it.club != ClubType.PUTTER && it.club != ClubType.UNKNOWN }
            val approach = nonPutts.lastOrNull() ?: return@mapNotNull null
            val next = shots.firstOrNull { it.sequence == approach.sequence + 1 } ?: return@mapNotNull null
            val prox = next.distanceToPinYards ?: return@mapNotNull null
            if (prox < 100) prox.toDouble() else null
        }
    }

    private fun trimmedMean(values: List<Double>, trimFraction: Double = 0.2): Double? {
        if (values.isEmpty()) return null
        if (values.size < 3) return values.average()
        val sorted = values.sorted()
        val drop = maxOf(1, (sorted.size * trimFraction).toInt())
        val trimmed = sorted.drop(drop).dropLast(drop)
        return if (trimmed.isEmpty()) null else trimmed.average()
    }

    private fun stdDev(values: List<Double>): Double {
        val mean = values.average()
        return sqrt(values.map { (it - mean) * (it - mean) }.average())
    }

    // ── Template evaluation ───────────────────────────────────────────────────

    private fun evaluate(ctx: Context): List<RoundInsight> {
        val fired = mutableListOf<RoundInsight>()

        fun add(code: String, severity: RoundInsight.Severity, tier: Int, msg: String) {
            fired += RoundInsight(code, severity, tier, msg)
        }

        fun sgFmt(v: Double) = (if (v >= 0) "+" else "") + "%.2f".format(v)

        // ── TIER 1: Critical SG ───────────────────────────────────────────────

        if (ctx.sgPutting < -1.5)
            add("SG_PUTTING_CRITICAL", RoundInsight.Severity.CRITICAL, 1,
                "Putting was the biggest hole in your scorecard — you lost ${sgFmt(ctx.sgPutting)} strokes on the greens. Speed control on long putts is the quickest fix.")
        if (ctx.sgApproach < -1.5)
            add("SG_APPROACH_CRITICAL", RoundInsight.Severity.CRITICAL, 1,
                "Approach play was your Achilles heel — ${sgFmt(ctx.sgApproach)} strokes lost into the green. Distance control and iron accuracy need work.")
        if (ctx.sgOffTee < -1.5)
            add("SG_OFF_TEE_CRITICAL", RoundInsight.Severity.CRITICAL, 1,
                "Tee shots were a major problem — ${sgFmt(ctx.sgOffTee)} strokes lost off the tee. Consider trading distance for accuracy on tight holes.")
        if (ctx.sgShortGame < -1.0)
            add("SG_SHORT_GAME_CRITICAL", RoundInsight.Severity.CRITICAL, 1,
                "Your short game lost you ${sgFmt(ctx.sgShortGame)} strokes. Getting up-and-down more consistently is the fastest way to lower your score.")

        // ── TIER 1: Strong positives ──────────────────────────────────────────

        if (ctx.sgPutting > 1.0)
            add("SG_PUTTING_STRONG", RoundInsight.Severity.POSITIVE, 1,
                "The putter was on fire — ${sgFmt(ctx.sgPutting)} strokes gained on the greens. Your pace control and green reading were excellent.")
        if (ctx.sgApproach > 1.0)
            add("SG_APPROACH_STRONG", RoundInsight.Severity.POSITIVE, 1,
                "Ball striking was excellent — ${sgFmt(ctx.sgApproach)} strokes gained on approach shots. That kind of iron play creates birdie looks.")
        if (ctx.sgTotal > 2.0)
            add("SG_TOTAL_POSITIVE", RoundInsight.Severity.POSITIVE, 1,
                "Overall you gained ${sgFmt(ctx.sgTotal)} strokes vs your baseline — a genuinely strong all-around performance.")

        // ── TIER 2: Correlations & patterns ──────────────────────────────────

        if (ctx.fir >= 55 && ctx.girPct < 35)
            add("HIGH_FIR_LOW_GIR", RoundInsight.Severity.CRITICAL, 2,
                "Hit ${ctx.fir}% of fairways but only ${ctx.girPct}% of greens — your driving sets up good positions but the irons aren't converting.")
        if (ctx.fir < 35 && ctx.girPct >= 50)
            add("LOW_FIR_HIGH_GIR", RoundInsight.Severity.INFO, 2,
                "Only ${ctx.fir}% fairways but ${ctx.girPct}% GIR — impressive recovery iron play. Cleaning up tee shots would make you even more dangerous.")
        if (ctx.threePutts >= 3)
            add("THREE_PUTTS", RoundInsight.Severity.CRITICAL, 2,
                "${ctx.threePutts} three-putts — each one is a stroke wasted. Getting the first putt within 3 feet eliminates the damage.")
        else if (ctx.threePutts == 2)
            add("THREE_PUTTS", RoundInsight.Severity.WARNING, 2,
                "${ctx.threePutts} three-putts today. Lag putting distance control from long range needs attention.")
        if (ctx.scramblingPct >= 50 && ctx.girPct < 45)
            add("GOOD_SCRAMBLING", RoundInsight.Severity.POSITIVE, 2,
                "Only ${ctx.girPct}% GIR but scrambled ${ctx.scramblingPct}% — your short game saved several strokes and kept the score respectable.")
        if (ctx.scramblingPct < 25 && ctx.girPct < 45)
            add("POOR_SCRAMBLING", RoundInsight.Severity.CRITICAL, 2,
                "Missed ${100 - ctx.girPct}% of greens and only scrambled ${ctx.scramblingPct}% — short game is costing you 4-5 strokes per round.")
        if (ctx.frontScore != null && ctx.backScore != null) {
            if (ctx.backScore > ctx.frontScore + 4)
                add("FRONT_BACK_WORSE", RoundInsight.Severity.WARNING, 2,
                    "Front ${ctx.frontScore}, back ${ctx.backScore} — a ${ctx.backScore - ctx.frontScore} shot drop-off. Fatigue or concentration may be fading late.")
            else if (ctx.frontScore > ctx.backScore + 3)
                add("FRONT_BACK_BETTER", RoundInsight.Severity.POSITIVE, 2,
                    "Strong finish — improved ${ctx.frontScore - ctx.backScore} shots from front (${ctx.frontScore}) to back (${ctx.backScore}).")
        }
        if (ctx.par3AvgOverPar > 0.8)
            add("PAR3_STRUGGLES", RoundInsight.Severity.WARNING, 2,
                "Par 3s averaged +${"%.1f".format(ctx.par3AvgOverPar)} over par — iron accuracy from the tee needs attention on short holes.")
        if (ctx.par5AvgOverPar > 0.5)
            add("PAR5_SCORING", RoundInsight.Severity.WARNING, 2,
                "Par 5s averaged +${"%.1f".format(ctx.par5AvgOverPar)} — not capitalizing on scoring holes. Better course management could unlock birdies.")
        else if (ctx.par5AvgOverPar < -0.3)
            add("PAR5_BIRDIE_MACHINE", RoundInsight.Severity.POSITIVE, 2,
                "Par 5s are your scoring holes — averaging ${"%.1f".format(ctx.par5AvgOverPar)} today. Your length and course management there is a real strength.")
        if (ctx.maxConsecBogeys >= 3)
            add("CONSECUTIVE_BOGEYS", RoundInsight.Severity.WARNING, 2,
                "${ctx.maxConsecBogeys} bogeys in a row — bad streaks often snowball from one poor shot. A reset routine between holes is key.")
        val earlyHr = ctx.earlyRoundHr; val lateHr = ctx.lateRoundHr
        if (earlyHr != null && lateHr != null && lateHr > earlyHr + 8)
            add("HIGH_HR_LATE_ROUND", RoundInsight.Severity.INFO, 2,
                "HR climbed ${(lateHr - earlyHr).toInt()} bpm from front to back nine — fatigue or pressure may have been a factor in the later holes.")

        // ── TIER 3: Club-specific ─────────────────────────────────────────────

        ctx.worstClub?.let {
            add("WORST_CLUB_SG", RoundInsight.Severity.WARNING, 3,
                "Your ${it.name} was your weakest club — avg SG ${sgFmt(it.avgSg)} over ${it.shots} shots. Targeted practice with this club would pay dividends.")
        }
        ctx.bestClub?.let {
            add("BEST_CLUB_SG", RoundInsight.Severity.POSITIVE, 3,
                "Your ${it.name} was dialed in — ${sgFmt(it.avgSg)} avg SG over ${it.shots} shots. That's a club you can trust under pressure.")
        }
        ctx.driverDistStd?.let { std ->
            if (std > 30)
                add("DRIVER_INCONSISTENT", RoundInsight.Severity.WARNING, 3,
                    "Driver distance was all over the place (±${std.toInt()} yds) — focus on center contact over maximum distance for more consistency.")
        }

        // ── TIER 4: Minor observations ────────────────────────────────────────

        if (ctx.onePutts >= 4)
            add("ONE_PUTTS_HIGH", RoundInsight.Severity.POSITIVE, 4,
                "${ctx.onePutts} one-putts today — you were holing out from close range consistently. That's a real scoring asset.")
        if (ctx.threePutts == 0 && ctx.totalPutts <= (ctx.holesPlayed * 1.8).toInt())
            add("ZERO_THREE_PUTTS", RoundInsight.Severity.POSITIVE, 4,
                "Zero three-putts — lag putting distance control was excellent. Great green reading and pace judgment.")
        ctx.longestDriveYards?.let { if (it >= 270) add("LONGEST_DRIVE", RoundInsight.Severity.POSITIVE, 4, "Longest drive: $it yards — great power off the tee.") }
        ctx.histDriverDiff?.let { diff ->
            if (diff <= -15) add("DRIVER_DOWN_VS_HISTORY", RoundInsight.Severity.WARNING, 4,
                "Avg driver distance was ~${abs(diff).toInt()} yards shorter than your recent average. Check if fatigue or swing changes are affecting your power.")
            else if (diff >= 10) add("DRIVER_UP_VS_HISTORY", RoundInsight.Severity.POSITIVE, 4,
                "Avg driver distance was ~${diff.toInt()} yards longer than your recent average — you were swinging well today.")
        }
        ctx.histApproachDiff?.let { diff ->
            if (diff >= 8) add("APPROACH_WORSE_VS_HISTORY", RoundInsight.Severity.WARNING, 4,
                "Approaches averaged ~${diff.toInt()} yards farther from the pin than your recent average — irons weren't as sharp today.")
            else if (diff <= -6) add("APPROACH_BETTER_VS_HISTORY", RoundInsight.Severity.POSITIVE, 4,
                "Approaches averaged ~${abs(diff).toInt()} yards closer to the pin than your recent average — irons were dialed in today.")
        }
        ctx.durationMin?.let { mins ->
            if (mins > 270) add("ROUND_DURATION_LONG", RoundInsight.Severity.INFO, 4,
                "The round took ${mins.toInt()} minutes — mental fatigue over 4+ hours can affect decision-making on the back nine.")
        }

        // ── Rank: tier → severity, cap positives ─────────────────────────────

        fired.sortWith(compareBy({ it.tier }, { it.severity.ordinal }))
        val negatives = fired.filter { it.severity == RoundInsight.Severity.CRITICAL || it.severity == RoundInsight.Severity.WARNING }
        val positives = fired.filter { it.severity == RoundInsight.Severity.POSITIVE || it.severity == RoundInsight.Severity.INFO }
        val maxPositives = if (negatives.isEmpty()) 6 else 2
        return (negatives.take(4) + positives.take(maxPositives)).take(6)
    }
}
