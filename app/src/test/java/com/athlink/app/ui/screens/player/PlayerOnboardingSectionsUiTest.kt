package com.athlink.app.ui.screens.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.athlink.app.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Taps through every onboarding section the way a player would and checks the form state the
 * ViewModel would receive. The harness updates the form exactly like PlayerProfileViewModel.update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerOnboardingSectionsUiTest {

    @get:Rule val rule = createComposeRule()

    private var form by mutableStateOf(PlayerProfileForm())
    private var errors by mutableStateOf<Map<PlayerField, String>>(emptyMap())

    private fun show(content: @Composable (PlayerFormContext) -> Unit) {
        rule.setContent {
            val ctx = PlayerFormContext(form, errors, enabled = true) { field, transform ->
                form = transform(form)
                if (field != null) errors = errors - field
            }
            Column(Modifier.verticalScroll(rememberScrollState())) { content(ctx) }
        }
    }

    @Test
    fun mainSportCanBeSelectedAndChanged() {
        show { SportSection(it) }
        rule.onNodeWithText("Cricket").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("Cricket", form.primarySport)
        rule.onNodeWithText("Cricket").assertIsSelected()

        // Once a main sport is chosen, "Football" also appears in the "other sports" row; the
        // first match is the main-sport chip.
        rule.onAllNodesWithText("Football")[0].performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("Football", form.primarySport)
        rule.onAllNodesWithText("Cricket")[0].assertIsNotSelected()
    }

    @Test
    fun secondarySportsAppearAfterMainSportAndToggle() {
        form = form.withPrimarySport("Cricket")
        show { SportSection(it) }
        // Secondary list excludes the main sport, so "Tennis" appears exactly once (secondary row
        // is shown below the main row; both contain Tennis -> pick the second via the helper).
        assertEquals("Cricket", form.primarySport)
        val tennis = rule.onAllNodesWithTextSafe("Tennis")
        tennis.last().performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf("Tennis"), form.secondarySports)
    }

    @Test
    fun sportSpecificAnswersSelectAndClear() {
        form = form.withPrimarySport("Cricket")
        show { GameSection(it) }
        rule.onNodeWithText("Bowler").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals("Bowler", form.sportProfile["playerRole"])
        rule.onNodeWithText("Bowler").performScrollTo().performClick() // tap again clears
        rule.waitForIdle()
        assertFalse("playerRole" in form.sportProfile)
    }

    @Test
    fun experienceFieldsAcceptInput() {
        form = form.withPrimarySport("Cricket")
        show { GameSection(it) }
        rule.onNodeWithText("Current team / club (optional)").performScrollTo().performTextInput("Surat XI")
        rule.waitForIdle()
        assertEquals("Surat XI", form.currentTeam)
    }

    @Test
    fun skillLevelSelects() {
        form = form.withPrimarySport("Cricket")
        show { LevelSection(it) }
        rule.onNodeWithText("Intermediate").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(SkillLevel.INTERMEDIATE, form.skillLevel)
    }

    @Test
    fun goalsMultiSelect() {
        show { GoalsSection(it) }
        rule.onNodeWithText("Find a coach").performScrollTo().performClick()
        rule.onNodeWithText("Improve fitness").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf(PlayerGoal.FIND_COACH, PlayerGoal.IMPROVE_FITNESS), form.goals)
        rule.onNodeWithText("Find a coach").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(listOf(PlayerGoal.IMPROVE_FITNESS), form.goals)
    }

    @Test
    fun trainingChoicesSelect() {
        show { TrainingSection(it) }
        rule.onNodeWithText("Group").performScrollTo().performClick()
        rule.onNodeWithText("Evening").performScrollTo().performClick()
        rule.onNodeWithText("10 km").performScrollTo().performClick()
        rule.waitForIdle()
        assertEquals(CoachingFormat.GROUP, form.coachingFormat)
        assertEquals(TrainingTime.EVENING, form.trainingTime)
        assertEquals(TravelDistance.KM_10, form.travelDistance)
    }

    @Test
    fun aboutYouTextFieldsAcceptInput() {
        show { AboutYouSection(it, isPreparingPhoto = false, onPickPhoto = {}, onRemovePhoto = {}) }
        rule.onNodeWithText("Full name *").performScrollTo().performTextInput("Asha Patel")
        rule.onNodeWithText("City / town *").performScrollTo().performTextInput("Surat")
        rule.waitForIdle()
        assertEquals("Asha Patel", form.fullName)
        assertEquals("Surat", form.city)
    }

    @Test
    fun consentCheckboxesToggleForOlderAccounts() {
        form = form.copy(consentAlreadyRecorded = false, fullName = "Asha Patel")
        show { ReadySection(it, onEdit = {}) }
        rule.onNodeWithText("I agree to the Athlink Terms of Service").performScrollTo().performClick()
        rule.onNodeWithText("I have read the Athlink Privacy Policy").performScrollTo().performClick()
        rule.waitForIdle()
        assertTrue(form.termsAccepted)
        assertTrue(form.privacyAccepted)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithTextSafe(text: String) =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes().indices.map { onAllNodes(androidx.compose.ui.test.hasText(text))[it] }
}
