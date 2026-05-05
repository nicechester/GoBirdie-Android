package io.github.nicechester.gobirdie

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
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
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        hiltRule.inject()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        shots = loadCsv()
    }

    @Test
    fun testFullRoundSimulation() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Round").performClick()
        composeRule.onNodeWithTag("startRoundButton").performClick()

        composeRule.onNodeWithTag("searchField").performTextInput("Roosevelt")
        composeRule.onNodeWithTag("searchField").performImeAction()
        device.pressBack()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithText("Roosevelt Golf Course").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Roosevelt Golf Course").performClick()

        composeRule.waitUntil(30_000) {
            composeRule.onAllNodesWithTag("startOnHoleButton").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("startOnHoleButton").performClick()

        val holeGroups = shots.groupBy { it.hole }.toSortedMap()
        for ((holeNumber, holeShots) in holeGroups) {
            playHole(holeNumber, holeShots)
        }

        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("Scorecards").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Scorecards").performClick()

        composeRule.waitUntil(10_000) {
            composeRule.onAllNodesWithTag("scorecardItem_0").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("scorecardItem_0").performClick()

        Thread.sleep(10_000)
    }

    private fun injectLocation(lat: Double, lon: Double) {
        scenario.onActivity { activity ->
            val appState = ViewModelProvider(activity)[AppState::class.java]
            appState.locationService.setTestLocation(lat, lon)
        }
        composeRule.waitForIdle()
    }

    private fun playHole(holeNumber: Int, holeShots: List<ShotCoord>) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag("holeLabel").fetchSemanticsNodes().isNotEmpty()
        }

        for (shot in holeShots) {
            injectLocation(shot.lat, shot.lon)
            composeRule.onNodeWithTag("markShotButton").performClick()
            selectRecommendedClub()
        }

        repeat(2) {
            composeRule.onNodeWithTag("puttPlus").performClick()
        }
        composeRule.onNodeWithTag("nextHoleButton").performClick()
    }

    private fun selectRecommendedClub() {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Select Club").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Confirm").performClick()
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
