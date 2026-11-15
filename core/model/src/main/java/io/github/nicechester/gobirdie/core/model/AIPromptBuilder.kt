package io.github.nicechester.gobirdie.core.model

import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The "Ask AI" prompt for a round. A port of the iOS AIPromptBuilder (GoBirdieCore), so both apps give the same
 * prompt in the same four languages (en, ko, es, ja; any other language falls back to English).
 */
object AIPromptBuilder {

    /**
     * @param course the course the round was played on, for each hole's tee-to-green distance. Without it, that
     *   column shows "-".
     * @param handicap the handicap label, e.g. "Bogey Golfer".
     * @param handedness "right" or "left".
     * @param language "en", "ko", "es" or "ja".
     */
    fun build(
        round: Round,
        course: Course?,
        handicap: String,
        handedness: String,
        language: String,
    ): String {
        val lang = languageOf(language)
        val played = round.holes.filter { it.strokes > 0 }
        if (played.isEmpty()) return "No scorecard data available."

        val totalPar = played.sumOf { it.par }
        val overPar = round.totalStrokes - totalPar
        val frontScore = played.filter { it.number <= 9 }.sumOf { it.strokes }
        val backScore = played.filter { it.number > 9 }.sumOf { it.strokes }
        val dateStr = round.startedAt.take(10)
        val durationMin = round.endedAt?.let { minutesBetween(round.startedAt, it) } ?: 0

        // Heart rate: only when the round has samples (e.g. an Apple Watch was worn, or a Wear OS watch).
        val hr = round.heartRateTimeline
        val hasHeartRate = hr.isNotEmpty()
        val avgHr = if (hasHeartRate) hr.sumOf { it.bpm } / hr.size else 0
        val maxHr = hr.maxOfOrNull { it.bpm } ?: 0

        val left = handedness == "left"
        val weather = weatherLine(round, lang)
        val overParText = (if (overPar >= 0) "+" else "") + overPar

        val summary = StringBuilder()
        when (lang) {
            "ko" -> {
                summary.append("## 라운드 요약\n")
                summary.append("날짜: $dateStr\n")
                summary.append("코스: ${round.courseName}\n")
                summary.append("핸디캡: $handicap\n")
                summary.append("타격 방향: ${if (left) "왼손잡이" else "오른손잡이"}\n")
                summary.append("스코어: ${round.totalStrokes} ($overParText) 파 $totalPar 대비\n")
                if (played.size >= 16) summary.append("전반: $frontScore | 후반: $backScore\n")
                summary.append("홀: ${played.size}, 소요시간: ${durationMin}분\n")
                summary.append(weather)
                if (hasHeartRate) summary.append("평균 심박수: $avgHr bpm, 최대 심박수: $maxHr bpm\n")
                summary.append("\n## 홀별 스코어카드\n")
                summary.append("홀 | 파 | 스코어 | +/- | 스코어 타입 | 퍼팅 | 티→그린\n")
            }
            "es" -> {
                summary.append("## Resumen de la Ronda\n")
                summary.append("Fecha: $dateStr\n")
                summary.append("Campo: ${round.courseName}\n")
                summary.append("Tu Hándicap: $handicap\n")
                summary.append("Mano dominante: ${if (left) "Zurdo" else "Diestro"}\n")
                summary.append("Puntuación: ${round.totalStrokes} ($overParText) sobre par $totalPar\n")
                if (played.size >= 16) summary.append("Ida: $frontScore | Vuelta: $backScore\n")
                summary.append("Hoyos: ${played.size}, Duración: $durationMin min\n")
                summary.append(weather)
                if (hasHeartRate) summary.append("FC Promedio: $avgHr bpm, FC Máx: $maxHr bpm\n")
                summary.append("\n## Tarjeta Hoyo por Hoyo\n")
                summary.append("Hoyo | Par | Golpes | +/- | Tipo | Putts | Tee→Green\n")
            }
            "ja" -> {
                summary.append("## ラウンドサマリー\n")
                summary.append("日付: $dateStr\n")
                summary.append("コース: ${round.courseName}\n")
                summary.append("ハンディキャップ: $handicap\n")
                summary.append("利き手: ${if (left) "左利き" else "右利き"}\n")
                summary.append("スコア: ${round.totalStrokes} ($overParText) パー${totalPar}比\n")
                if (played.size >= 16) summary.append("前半: $frontScore | 後半: $backScore\n")
                summary.append("ホール数: ${played.size}、所要時間: ${durationMin}分\n")
                summary.append(weather)
                if (hasHeartRate) summary.append("平均心拍数: $avgHr bpm、最大心拍数: $maxHr bpm\n")
                summary.append("\n## ホール別スコアカード\n")
                summary.append("ホール | パー | スコア | +/- | スコアタイプ | パット | ティー→グリーン\n")
            }
            else -> {
                summary.append("## Round Summary\n")
                summary.append("Date: $dateStr\n")
                summary.append("Course: ${round.courseName}\n")
                summary.append("Your Handicap: $handicap\n")
                summary.append("Handedness: ${if (left) "Left-handed" else "Right-handed"}\n")
                summary.append("Score: ${round.totalStrokes} ($overParText) over par $totalPar\n")
                if (played.size >= 16) summary.append("Front: $frontScore | Back: $backScore\n")
                summary.append("Holes: ${played.size}, Duration: $durationMin min\n")
                summary.append(weather)
                if (hasHeartRate) summary.append("Avg HR: $avgHr bpm, Max HR: $maxHr bpm\n")
                summary.append("\n## Hole-by-Hole Scorecard\n")
                summary.append("Hole | Par | Score | +/- | Score Type | Putts | Tee→Green\n")
            }
        }
        summary.append("-----|-----|-------|-----|------------|------|-------\n")

        val sortedHoles = played.sortedBy { it.number }
        for (hole in sortedHoles) {
            val diff = hole.strokes - hole.par
            val teeToGreen = teeToGreenYards(hole, course)?.let { "${it}y" } ?: "-"
            val diffText = (if (diff >= 0) "+" else "") + diff
            summary.append("H%02d | ${hole.par} | ${hole.strokes} | $diffText | ${scoreType(diff, lang)} | ${hole.putts} | $teeToGreen\n".format(hole.number))
        }

        // Shots per hole: club, distance to the next shot (or to the green), direction, and where it was hit.
        val shotSection = StringBuilder()
        for (hole in sortedHoles) {
            shotLine(hole, greenPoint(hole, course), lang)?.let { line ->
                shotSection.append("H%02d: $line\n".format(hole.number))
            }
        }
        if (shotSection.isNotEmpty()) {
            summary.append("\n${shotsHeader(lang)}\n")
            summary.append(shotSection)
        }

        val instruction = coachInstruction(lang, includeHeartRate = hasHeartRate)
        return "$instruction\n\n$summary"
    }

    // MARK: - Distances

    private fun teeToGreenYards(hole: HoleScore, course: Course?): Int? {
        val courseHole = course?.holes?.firstOrNull { it.number == hole.number } ?: return null
        val tee = courseHole.tee ?: return null
        val green = courseHole.greenCenter ?: hole.greenCenter ?: return null
        return yards(tee.distanceMeters(green))
    }

    /** The course's green for a hole, falling back to the scorecard's green. */
    private fun greenPoint(hole: HoleScore, course: Course?): GpsPoint? =
        course?.holes?.firstOrNull { it.number == hole.number }?.greenCenter ?: hole.greenCenter

    private fun yards(meters: Double): Int = (meters * 1.09361).roundToInt()

    /**
     * Each shot on a hole: club, its distance to the next shot (or to the green for the last shot), its direction
     * relative to the green (not for the last shot, which ends at the green), and where it was hit.
     */
    private fun shotLine(hole: HoleScore, green: GpsPoint?, lang: String): String? {
        val shots = hole.shots.sortedBy { it.sequence }
        val items = mutableListOf<String>()
        shots.forEachIndexed { i, shot ->
            val isLast = i + 1 == shots.size
            val target = if (isLast) green else shots[i + 1].location
            if (target == null) return@forEachIndexed
            val distance = yards(shot.location.distanceMeters(target))
            val club = if (shot.club != ClubType.UNKNOWN) "${shot.club.displayName} " else ""
            val suffix = if (isLast) " ${greenLabel(lang)}" else ""
            val direction = if (!isLast && green != null) " " + directionName(shot.location, target, green, lang) else ""
            var place = String.format(Locale.US, "%.6f, %.6f", shot.location.lat, shot.location.lon)
            shot.altitudeMeters?.let { place += ", ${(it * 3.28084).roundToInt()} ft" }
            items.add("#${shot.sequence} $club$distance" + "y$suffix$direction ($place)")
        }
        return if (items.isEmpty()) null else items.joinToString(" | ")
    }

    /**
     * Direction of a shot relative to the green, as the desktop app computes it: the shot's bearing minus the
     * bearing from the shot to the green, wrapped to ±180°. Within ±10° is straight; beyond ±30° is "far".
     */
    private fun directionName(from: GpsPoint, to: GpsPoint, green: GpsPoint, lang: String): String {
        var deviation = bearing(from, to) - bearing(from, green)
        if (deviation > 180) deviation -= 360
        if (deviation < -180) deviation += 360
        val magnitude = abs(deviation)
        val right = deviation > 0
        val far = magnitude > 30
        return when (lang) {
            "ko" -> if (magnitude < 10) "직진" else (if (far) "많이 " else "") + (if (right) "우측" else "좌측")
            "es" -> if (magnitude < 10) "recto" else (if (far) "mucho a la " else "a la ") + (if (right) "derecha" else "izquierda")
            "ja" -> if (magnitude < 10) "直進" else (if (far) "大きく" else "") + (if (right) "右" else "左")
            else -> if (magnitude < 10) "straight" else (if (far) "far " else "") + (if (right) "right" else "left")
        }
    }

    /** Initial bearing in degrees [0, 360) from one point to another. */
    private fun bearing(from: GpsPoint, to: GpsPoint): Double {
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val dLon = Math.toRadians(to.lon - from.lon)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    // MARK: - Labels

    private fun shotsHeader(lang: String): String = when (lang) {
        "ko" -> "## 샷별 거리"
        "es" -> "## Distancias por golpe"
        "ja" -> "## ショット別の距離"
        else -> "## Shot Distances"
    }

    private fun greenLabel(lang: String): String = when (lang) {
        "ko" -> "그린까지"
        "es" -> "al green"
        "ja" -> "グリーンまで"
        else -> "to green"
    }

    /** Weather line for the summary, or "" when the round has no weather data. */
    private fun weatherLine(round: Round, lang: String): String {
        val parts = mutableListOf<String>()
        round.weatherCondition?.let { parts.add(it) }
        val minF = round.temperatureMinF
        val maxF = round.temperatureMaxF
        if (minF != null && maxF != null) {
            parts.add("${minF.roundToInt()}–${maxF.roundToInt()}°F")
        } else (minF ?: maxF)?.let { parts.add("${it.roundToInt()}°F") }
        if (parts.isEmpty()) return ""
        val value = parts.joinToString(", ")
        return when (lang) {
            "ko" -> "날씨: $value\n"
            "es" -> "Clima: $value\n"
            "ja" -> "天気: $value\n"
            else -> "Weather: $value\n"
        }
    }

    private fun scoreType(diff: Int, lang: String): String = when (lang) {
        "ko" -> when {
            diff <= -3 -> "알바트로스"
            diff == -2 -> "이글"
            diff == -1 -> "버디"
            diff == 0 -> "파"
            diff == 1 -> "보기"
            diff == 2 -> "더블보기"
            diff == 3 -> "트리플보기"
            else -> "+4 오버파"
        }
        "ja" -> when {
            diff <= -3 -> "アルバトロス"
            diff == -2 -> "イーグル"
            diff == -1 -> "バーディー"
            diff == 0 -> "パー"
            diff == 1 -> "ボギー"
            diff == 2 -> "ダブルボギー"
            diff == 3 -> "トリプルボギー"
            else -> "+4 オーバーパー"
        }
        // Golf terms stay in English for Spanish
        else -> when {
            diff <= -3 -> "Albatross"
            diff == -2 -> "Eagle"
            diff == -1 -> "Birdie"
            diff == 0 -> "Par"
            diff == 1 -> "Bogey"
            diff == 2 -> "Double Bogey"
            diff == 3 -> "Triple Bogey"
            else -> "+4 Over Par"
        }
    }

    // MARK: - Instructions

    /**
     * The coaching instructions. Without heart-rate data, the heart-rate item is removed and the rest renumbered,
     * so the prompt never asks about heart rate when the round has none.
     */
    private fun coachInstruction(lang: String, includeHeartRate: Boolean): String {
        val text = when (lang) {
            "ko" -> KOREAN_INSTRUCTION
            "es" -> SPANISH_INSTRUCTION
            "ja" -> JAPANESE_INSTRUCTION
            else -> ENGLISH_INSTRUCTION
        }
        if (includeHeartRate) return text
        val heartRateMarkers = listOf("Heart rate", "심박수", "frecuencia cardíaca", "心拍数")
        var number = 0
        return text.lines()
            .filter { line -> heartRateMarkers.none { line.contains(it) } }
            .joinToString("\n") { line ->
                if (line.length > 2 && line[0].isDigit() && line[1] == '.') {
                    number += 1
                    "$number." + line.substring(2)
                } else line
            }
    }

    private fun languageOf(code: String): String = if (code == "ko" || code == "es" || code == "ja") code else "en"

    private fun minutesBetween(start: String, end: String): Int =
        runCatching { Duration.between(Instant.parse(start), Instant.parse(end)).toMinutes().toInt() }.getOrDefault(0)

    private val ENGLISH_INSTRUCTION = """
        You are a golf coach. Analyze the round and provide 250-350 word feedback.

        1. Overall performance vs handicap (Consider Course Difficulty/Slope and weather)
        2. Key strengths and areas to improve (cite specific holes/metrics)
        3. Performance by shot type: tee, approach, chip, and putt (use the shot distances, directions, and the distance to the green)
        4. Club summary: average distance and direction tendency for each club used
        5. Terrain and lie: using each shot's latitude/longitude and altitude, infer the slope and lie (uphill, downhill, uneven) between consecutive shots, and explain how they affected ball flight and dispersion, accounting for handedness. Cite the best shots and key misses.
        6. Heart rate patterns and correlation to score
        7. One actionable practice focus

        Be direct. Cite numbers. No generic advice.
    """.trimIndent()

    private val KOREAN_INSTRUCTION = """
        당신은 골프 코치입니다. 라운드를 분석해서 600~900자 내외의 피드백을 제공해주세요.

        1. 전체 성과 vs 핸디캡 (코스 난이도/슬로프와 날씨 고려 필수)
        2. 잘한 점과 개선 필요 부분 (구체적 홀/수치 인용)
        3. 샷 유형별 성과: 티샷, 어프로치, 칩, 퍼팅 (샷 거리, 방향, 그린까지 거리 활용)
        4. 클럽 요약: 사용한 클럽별 평균 거리와 방향 경향
        5. 지형과 라이: 각 샷의 위도/경도와 고도로 연속된 샷 사이의 경사와 라이(오르막, 내리막, 불안정한 라이)를 추정하고, 구질과 방향성에 미친 영향을 설명해주세요. 타격 방향을 고려하고, 가장 좋았던 샷과 주요 미스를 짚어주세요.
        6. 심박수 패턴과 스코어 연관성
        7. 한 가지 실천 포커스

        구체적으로 수치를 인용해주세요. 일반적인 조언은 피하세요.
    """.trimIndent()

    private val SPANISH_INSTRUCTION = """
        Eres un entrenador de golf. Analiza la ronda y proporciona feedback de 250-350 palabras.

        1. Rendimiento general vs hándicap (Considera Dificultad del Campo/Slope y el clima)
        2. Fortalezas clave y áreas a mejorar (cita hoyos/métricas específicas)
        3. Rendimiento por tipo de golpe: salida, approach, chip y putt (usa las distancias, direcciones y la distancia al green)
        4. Resumen por palo: distancia media y tendencia de dirección de cada palo usado
        5. Terreno y lie: usando la latitud/longitud y la altitud de cada golpe, infiere la pendiente y el lie (cuesta arriba, cuesta abajo, irregular) entre golpes consecutivos y explica cómo afectaron al vuelo de la bola y a la dispersión, teniendo en cuenta la mano dominante. Cita los mejores golpes y los fallos clave.
        6. Patrones de frecuencia cardíaca y correlación con el score
        7. Un enfoque de práctica accionable

        Sé directo. Cita números. Sin consejos genéricos.
    """.trimIndent()

    private val JAPANESE_INSTRUCTION = """
        あなたはゴルフコーチです。ラウンドを分析し、600〜900文字程度のフィードバックを提供してください。

        1. 全体的なパフォーマンス vs ハンディキャップ(コースの難易度/スロープと天候を必ず考慮)
        2. 良かった点と改善が必要な点(具体的なホール/数値を引用)
        3. ショットタイプ別の成績: ティーショット、アプローチ、チップ、パット(ショット距離・方向・グリーンまでの距離を活用)
        4. クラブ別の概要: 使用した各クラブの平均距離と方向の傾向
        5. 地形とライ: 各ショットの緯度経度と高度から、連続するショット間の傾斜とライ(上り、下り、不安定なライ)を推定し、弾道と方向性への影響を説明してください。利き手を考慮し、最も良かったショットと主なミスを挙げてください。
        6. 心拍数のパターンとスコアとの相関
        7. 実践すべき練習ポイントを1つ

        具体的に数値を引用してください。一般的なアドバイスは避けてください。
    """.trimIndent()
}
