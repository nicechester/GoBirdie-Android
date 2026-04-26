package io.github.nicechester.gobirdie

import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

data class ShotCoord(val hole: Int, val lat: Double, val lon: Double)

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class RoundSimulationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private lateinit var device: UiDevice
    private lateinit var shots: List<ShotCoord>

    @Before
    fun setUp() {
        hiltRule.inject()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        shots = loadCsv()
        // Launch activity after Hilt injection
        ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()
    }

    @Test
    fun testFullRoundSimulation() {
        // 1. Navigate to Round tab and start a round
        composeRule.onNodeWithText("Round").performClick()
        composeRule.onNodeWithTag("startRoundButton").performClick()

        // 2. Search for Roosevelt
        composeRule.onNodeWithTag("searchField").performTextInput("Roosevelt")
        composeRule.onNodeWithTag("searchField").performImeAction()
        Thread.sleep(3000)

        // Tap first course result
        composeRule.onAllNodesWithText("Roosevelt", substring = true)[0].performClick()
        Thread.sleep(1000)

        // Start on hole 1
        composeRule.onNodeWithTag("startOnHoleButton").performClick()
        Thread.sleep(1000)

        // 3. Play each hole
        val holeGroups = shots.groupBy { it.hole }.toSortedMap()
        for ((holeNumber, holeShots) in holeGroups) {
            playHole(holeNumber, holeShots)
        }

        // 4. Verify scorecard was saved
        composeRule.onNodeWithText("Scorecards").performClick()
        composeRule.onNodeWithText("Roosevelt", substring = true).assertExists()
    }

    private fun playHole(holeNumber: Int, holeShots: List<ShotCoord>) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("holeLabel").fetchSemanticsNodes().isNotEmpty()
        }

        for (shot in holeShots) {
            injectLocation(shot.lat, shot.lon)
            Thread.sleep(1000)
            composeRule.onNodeWithTag("markShotButton").performClick()
            Thread.sleep(500)
            selectRecommendedClub()
        }

        repeat(2) {
            composeRule.onNodeWithTag("puttPlus").performClick()
            Thread.sleep(200)
        }

        composeRule.onNodeWithTag("nextHoleButton").performClick()
        Thread.sleep(500)
    }

    private fun selectRecommendedClub() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Select Club").fetchSemanticsNodes().isNotEmpty()
        }

        val recommended = composeRule.onAllNodes(
            hasAnyDescendant(hasContentDescription("Check"))
        ).fetchSemanticsNodes()

        if (recommended.isNotEmpty()) {
            composeRule.onAllNodes(
                hasAnyDescendant(hasContentDescription("Check"))
            ).onFirst().performClick()
        } else {
            composeRule.onAllNodes(
                SemanticsMatcher("club tag") {
                    it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)
                        ?.startsWith("club_") == true
                }
            ).onFirst().performClick()
        }
        Thread.sleep(300)
    }

    private fun injectLocation(lat: Double, lon: Double) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
            runCatching {
                lm.addTestProvider(
                    provider, false, false, false, false, true, true, true,
                    android.location.Criteria.POWER_LOW, android.location.Criteria.ACCURACY_FINE
                )
                lm.setTestProviderEnabled(provider, true)
            }
            val loc = Location(provider).apply {
                latitude = lat
                longitude = lon
                altitude = 100.0
                accuracy = 5f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }
            runCatching { lm.setTestProviderLocation(provider, loc) }
        }
    }

    private fun loadCsv(): List<ShotCoord> {
        val context = InstrumentationRegistry.getInstrumentation().context
        return context.assets.open("roosevelt-coords-simul.csv").bufferedReader()
            .lineSequence()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { line ->
                val parts = line.split(",").map { it.trim() }
                ShotCoord(parts[0].toInt(), parts[1].toDouble(), parts[2].toDouble())
            }
            .toList()
    }
}
