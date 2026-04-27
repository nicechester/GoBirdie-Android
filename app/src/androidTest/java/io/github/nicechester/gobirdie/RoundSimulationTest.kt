package io.github.nicechester.gobirdie

import android.location.Location
import android.os.SystemClock
import com.google.android.gms.location.LocationServices
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    @get:Rule(order = 2)
    val composeRule = createEmptyComposeRule()

    private lateinit var device: UiDevice
    private lateinit var shots: List<ShotCoord>

    @Before
    fun setUp() {
        hiltRule.inject()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // Allow this test package to provide mock locations
        device.executeShellCommand("appops set io.github.nicechester.gobirdie.test android:mock_location allow")
        shots = loadCsv()
    }

    @Test
    fun testFullRoundSimulation() {
        // Launch activity inside the test so ComposeTestRule is already running
        ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()

        // 1. Navigate to Round tab and start a round
        composeRule.onNodeWithText("Round").performClick()
        composeRule.onNodeWithTag("startRoundButton").performClick()

        // 2. Search for Roosevelt
        composeRule.onNodeWithTag("searchField").performTextInput("Roosevelt")
        composeRule.onNodeWithTag("searchField").performImeAction()

        // DISMISS KEYBOARD: This ensures the UI isn't obscured
        device.pressBack()

        // WAIT: Instead of Thread.sleep, wait specifically for the list item
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Roosevelt Golf Course").fetchSemanticsNodes().isNotEmpty()
        }

        // CLICK SPECIFICALLY: Target the list item, not the search bar text
        composeRule.onNodeWithText("Roosevelt Golf Course").performClick()

        // WAIT FOR NEXT SCREEN: Ensure the button exists before clicking
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("startOnHoleButton").fetchSemanticsNodes().isNotEmpty()
        }

        // 3. Start on hole 1
        composeRule.onNodeWithTag("startOnHoleButton").performClick()

        // 3. Play each hole
        val holeGroups = shots.groupBy { it.hole }.toSortedMap()
        for ((holeNumber, holeShots) in holeGroups) {
            playHole(holeNumber, holeShots)
        }

        // 4. Verify scorecard was saved
        // Give the app a moment to finish the round and return to the main dashboard
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("Scorecards").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Scorecards").performClick()
    }

    private fun playHole(holeNumber: Int, holeShots: List<ShotCoord>) {
        // Wait for the hole screen to settle
        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("holeLabel").fetchSemanticsNodes().isNotEmpty()
        }

        for ((index, shot) in holeShots.withIndex()) {
            injectLocation(shot.lat, shot.lon)
            waitForFlagDistanceChange()
            composeRule.onNodeWithTag("markShotButton").performClick()
            selectRecommendedClub()
            Thread.sleep(500)
        }

        // Putts and navigation
        repeat(2) {
            composeRule.onNodeWithTag("puttPlus").performClick()
        }
        composeRule.onNodeWithTag("nextHoleButton").performClick()
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

    private fun waitForFlagDistanceChange() {
        val before = composeRule.onAllNodesWithTag("flagDistance")
            .fetchSemanticsNodes().firstOrNull()
            ?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
            ?.firstOrNull()?.text
        composeRule.waitUntil(timeoutMillis = 5_000) {
            val current = composeRule.onAllNodesWithTag("flagDistance")
                .fetchSemanticsNodes().firstOrNull()
                ?.config?.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)
                ?.firstOrNull()?.text
            current != null && current != before && current != "—"
        }
    }

    private fun injectLocation(lat: Double, lon: Double) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)

        // Use a slightly future timestamp to ensure the system treats this as the 'latest' fix
        val timeBuffer = 1000L
        val mockLocation = Location("fused").apply {
            latitude = lat
            longitude = lon
            altitude = 100.0
            accuracy = 1.0f // Setting high accuracy triggers faster updates in many apps
            time = System.currentTimeMillis() + timeBuffer
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos() + (timeBuffer * 1_000_000)
        }

        fusedClient.setMockMode(true)
        fusedClient.setMockLocation(mockLocation)
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
