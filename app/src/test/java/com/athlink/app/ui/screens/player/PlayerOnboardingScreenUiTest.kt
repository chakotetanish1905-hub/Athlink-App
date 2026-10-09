package com.athlink.app.ui.screens.player

import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.athlink.app.data.model.*
import com.athlink.app.data.remote.PlayerSnapshot
import com.athlink.app.data.remote.PlayerStore
import com.athlink.app.data.repository.PlayerRepository
import com.athlink.app.viewmodel.PlayerProfileViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

/** In-memory stand-in for Firestore that records every save. */
private class FakePlayerStore(var user: User, var player: PlayerProfile? = null) : PlayerStore {
    val saves = mutableListOf<Pair<PlayerProfileForm, PlayerProfileStatus?>>()
    override suspend fun loadOwn() = Result.success(PlayerSnapshot(user, player))
    override suspend fun getPlayer(uid: String) = Result.success(player)
    override suspend fun save(form: PlayerProfileForm, playerExists: Boolean, status: PlayerProfileStatus?, recordConsent: Boolean): Result<Unit> {
        saves += form to status
        return Result.success(Unit)
    }
    override suspend fun preparePhoto(file: PickedFile) = Result.success("data:image/jpeg;base64,AAAA")
}

/**
 * Drives the real PlayerOnboardingScreen + PlayerProfileViewModel (Firestore replaced by a fake),
 * the way a player does on a phone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayerOnboardingScreenUiTest {

    @get:Rule val rule = createComposeRule()

    /** A player who already finished step 1, so onboarding resumes at "What's your sport?". */
    private val user = User(
        uid = "p1", name = "Rishabh Shah", email = "r@x.in", role = UserRole.PLAYER,
        dateOfBirth = "2005-04-10", profileStatus = PlayerProfileStatus.INCOMPLETE.name,
        termsAcceptedAt = Date(), privacyAcceptedAt = Date()
    )
    private val step1Done = PlayerProfile(uid = "p1", displayName = "Rishabh Shah", country = "India", state = "Gujarat", city = "Surat", region = "West India")

    private fun launch(store: FakePlayerStore): PlayerProfileViewModel {
        val vm = PlayerProfileViewModel(PlayerRepository(store))
        rule.setContent { PlayerOnboardingScreen(user = user, onCompleted = {}, onLogout = {}, viewModel = vm) }
        rule.waitForIdle()
        return vm
    }

    private fun tap(text: String) {
        rule.onAllNodesWithText(text)[0].performScrollTo().performClick()
        rule.waitForIdle()
    }

    /** Opens the main-sport dropdown and picks [sport] (menu items are the last matches). */
    private fun chooseMainSport(sport: String) {
        rule.onNodeWithText("Main sport *").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onAllNodesWithText(sport).let { it[it.fetchSemanticsNodes().size - 1] }.performClick()
        rule.waitForIdle()
    }

    /** Bottom-bar buttons are outside the scrolling content, so they are clicked without scrolling. */
    private fun press(text: String) {
        rule.onAllNodesWithText(text)[0].performClick()
        rule.waitForIdle()
    }

    @Test
    fun resumesAtSportStepAndMainSportCanBeSelected() {
        val vm = launch(FakePlayerStore(user, step1Done))
        assertEquals(PlayerOnboardingStep.SPORT, vm.state.value.step)
        chooseMainSport("Cricket")
        assertEquals("Cricket", vm.state.value.form.primarySport)
        // The chosen sport is shown in the field, and it is not offered again as an "other sport".
        chooseMainSport("Tennis")
        assertEquals("Tennis", vm.state.value.form.primarySport)
    }

    @Test
    fun fullOnboardingFromSportStepCompletesProfile() {
        val store = FakePlayerStore(user, step1Done)
        val vm = launch(store)
        chooseMainSport("Cricket")
        press("Next")
        assertEquals(PlayerOnboardingStep.GAME, vm.state.value.step)
        tap("Bowler")
        press("Next")
        assertEquals(PlayerOnboardingStep.LEVEL, vm.state.value.step)
        tap("Intermediate")
        press("Next")
        assertEquals(PlayerOnboardingStep.GOALS, vm.state.value.step)
        tap("Find a coach")
        press("Next")
        assertEquals(PlayerOnboardingStep.TRAINING, vm.state.value.step)
        tap("Group"); tap("Evening"); tap("10 km")
        press("Next")
        assertEquals(PlayerOnboardingStep.READY, vm.state.value.step)
        press("Continue to Athlink")

        val (finalForm, status) = store.saves.last()
        assertEquals(PlayerProfileStatus.COMPLETE, status)
        assertEquals("Cricket", finalForm.primarySport)
        assertEquals(mapOf("playerRole" to "Bowler"), finalForm.sportProfile)
        assertEquals(SkillLevel.INTERMEDIATE, finalForm.skillLevel)
        assertTrue(finalForm.validateAll().isEmpty())
    }

    @Test
    fun nextWithoutMainSportShowsError() {
        val vm = launch(FakePlayerStore(user, step1Done))
        press("Next")
        assertEquals(PlayerOnboardingStep.SPORT, vm.state.value.step)
        assertNotNull(vm.state.value.errors[PlayerField.PRIMARY_SPORT])
        rule.onNodeWithText("Choose your main sport").assertExists()
    }

    @Test
    fun backKeepsAnswers() {
        val vm = launch(FakePlayerStore(user, step1Done))
        chooseMainSport("Hockey")
        assertEquals("after choosing", "Hockey", vm.state.value.form.primarySport)
        press("Next")
        assertEquals("after Next", PlayerOnboardingStep.GAME, vm.state.value.step)
        press("Back")
        assertEquals("after Back", PlayerOnboardingStep.SPORT, vm.state.value.step)
        assertEquals("Hockey", vm.state.value.form.primarySport)
    }
}
