package io.github.nicechester.gobirdie.ui

import android.content.Context
import androidx.annotation.StringRes
import io.github.nicechester.gobirdie.R
import io.github.nicechester.gobirdie.core.model.ClubType
import io.github.nicechester.gobirdie.core.model.RoundInsight
import io.github.nicechester.gobirdie.core.model.SGBaseline

/** Localized display name for a club. ClubType.displayName stays English (it is a data key). */
@StringRes
fun clubNameRes(club: ClubType): Int = when (club) {
    ClubType.UNKNOWN -> R.string.club_unknown
    ClubType.DRIVER -> R.string.club_driver
    ClubType.MINI_DRIVER -> R.string.club_md
    ClubType.WOOD_3 -> R.string.club_3w
    ClubType.WOOD_5 -> R.string.club_5w
    ClubType.WOOD_7 -> R.string.club_7w
    ClubType.WOOD_9 -> R.string.club_9w
    ClubType.HYBRID_3 -> R.string.club_3h
    ClubType.HYBRID_4 -> R.string.club_4h
    ClubType.HYBRID_5 -> R.string.club_5h
    ClubType.IRON_4 -> R.string.club_4i
    ClubType.IRON_5 -> R.string.club_5i
    ClubType.IRON_6 -> R.string.club_6i
    ClubType.IRON_7 -> R.string.club_7i
    ClubType.IRON_8 -> R.string.club_8i
    ClubType.IRON_9 -> R.string.club_9i
    ClubType.PITCHING_WEDGE -> R.string.club_pw
    ClubType.GAP_WEDGE -> R.string.club_gw
    ClubType.SAND_WEDGE -> R.string.club_sw
    ClubType.LOB_WEDGE -> R.string.club_lw
    ClubType.PUTTER -> R.string.club_putter
}

/** Tee colors are persisted as English keys ("Black", …); this maps a key to its label, or null if unknown. */
@StringRes
fun teeNameRes(key: String): Int? = when (key) {
    "Black" -> R.string.tee_black
    "Blue" -> R.string.tee_blue
    "White" -> R.string.tee_white
    "Yellow" -> R.string.tee_yellow
    "Red" -> R.string.tee_red
    else -> null
}

@StringRes
fun baselineNameRes(baseline: SGBaseline): Int = when (baseline) {
    SGBaseline.SCRATCH -> R.string.baseline_scratch
    SGBaseline.SINGLE -> R.string.baseline_single
    SGBaseline.BOGEY -> R.string.baseline_bogey
    SGBaseline.HIGH_CAP -> R.string.baseline_high_cap
}

/**
 * Localized text for a round insight. The engine emits a code plus formatted args;
 * ClubStat names arrive as the English ClubType.displayName, so they are mapped back here.
 */
fun insightMessage(context: Context, insight: RoundInsight): String {
    val a = insight.args
    fun clubLabel(englishName: String): String =
        ClubType.entries.firstOrNull { it.displayName == englishName }
            ?.let { context.getString(clubNameRes(it)) }
            ?: englishName

    @StringRes
    val resId: Int = when (insight.code) {
        "SG_PUTTING_CRITICAL" -> R.string.insight_sg_putting_critical
        "SG_APPROACH_CRITICAL" -> R.string.insight_sg_approach_critical
        "SG_OFF_TEE_CRITICAL" -> R.string.insight_sg_off_tee_critical
        "SG_SHORT_GAME_CRITICAL" -> R.string.insight_sg_short_game_critical
        "SG_PUTTING_STRONG" -> R.string.insight_sg_putting_strong
        "SG_APPROACH_STRONG" -> R.string.insight_sg_approach_strong
        "SG_TOTAL_POSITIVE" -> R.string.insight_sg_total_positive
        "HIGH_FIR_LOW_GIR" -> R.string.insight_fir_low_gir
        "LOW_FIR_HIGH_GIR" -> R.string.insight_low_fir_high_gir
        "THREE_PUTTS" -> if (insight.severity == RoundInsight.Severity.CRITICAL)
            R.string.insight_three_putts_critical else R.string.insight_three_putts_warning
        "GOOD_SCRAMBLING" -> R.string.insight_good_scrambling
        "POOR_SCRAMBLING" -> R.string.insight_poor_scrambling
        "FRONT_BACK_WORSE" -> R.string.insight_front_back_worse
        "FRONT_BACK_BETTER" -> R.string.insight_front_back_better
        "PAR3_STRUGGLES" -> R.string.insight_par3_struggles
        "PAR5_SCORING" -> R.string.insight_par5_scoring
        "PAR5_BIRDIE_MACHINE" -> R.string.insight_par5_birdie_machine
        "CONSECUTIVE_BOGEYS" -> R.string.insight_consecutive_bogeys
        "HIGH_HR_LATE_ROUND" -> R.string.insight_hr_late_round
        "WORST_CLUB_SG" -> return context.getString(R.string.insight_worst_club, clubLabel(a[0]), a[1], a[2])
        "BEST_CLUB_SG" -> return context.getString(R.string.insight_best_club, clubLabel(a[0]), a[1], a[2])
        "DRIVER_INCONSISTENT" -> R.string.insight_driver_inconsistent
        "ONE_PUTTS_HIGH" -> R.string.insight_one_putts_high
        "ZERO_THREE_PUTTS" -> R.string.insight_zero_three_putts
        "LONGEST_DRIVE" -> R.string.insight_longest_drive
        "DRIVER_DOWN_VS_HISTORY" -> R.string.insight_driver_down_vs_history
        "DRIVER_UP_VS_HISTORY" -> R.string.insight_driver_up_vs_history
        "APPROACH_WORSE_VS_HISTORY" -> R.string.insight_approach_worse_vs_history
        "APPROACH_BETTER_VS_HISTORY" -> R.string.insight_approach_better_vs_history
        "ROUND_DURATION_LONG" -> R.string.insight_round_duration_long
        else -> return insight.code
    }
    return context.getString(resId, *a.toTypedArray())
}
