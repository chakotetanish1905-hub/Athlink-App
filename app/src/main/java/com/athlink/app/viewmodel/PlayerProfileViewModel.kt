package com.athlink.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.athlink.app.data.model.PickedFile
import com.athlink.app.data.model.PlayerField
import com.athlink.app.data.model.PlayerLanding
import com.athlink.app.data.model.PlayerOnboardingStep
import com.athlink.app.data.model.PlayerProfile
import com.athlink.app.data.model.PlayerProfileForm
import com.athlink.app.data.model.PlayerProfileStatus
import com.athlink.app.data.model.User
import com.athlink.app.data.remote.PlayerSnapshot
import com.athlink.app.data.repository.InvalidFileException
import com.athlink.app.data.repository.InvalidProfileException
import com.athlink.app.data.repository.PlayerRepository
import com.athlink.app.utils.ErrorMessages
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

/** ONBOARDING = step by step after signup; EDIT = all sections on one page from the profile screen. */
enum class PlayerFormMode { ONBOARDING, EDIT }

data class PlayerProfileUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val mode: PlayerFormMode = PlayerFormMode.ONBOARDING,
    val snapshot: PlayerSnapshot? = null,
    val step: PlayerOnboardingStep = PlayerOnboardingStep.ABOUT,
    /** Furthest step reached; earlier steps are tappable in the progress bar. */
    val furthestStep: PlayerOnboardingStep = PlayerOnboardingStep.ABOUT,
    val form: PlayerProfileForm = PlayerProfileForm(),
    val errors: Map<PlayerField, String> = emptyMap(),
    val isSaving: Boolean = false,
    val isPreparingPhoto: Boolean = false,
    val message: String? = null,
    /** Set by [PlayerProfileViewModel.resolveLanding] for the player gate. */
    val landing: PlayerLanding? = null,
    /** Set after onboarding completes or Edit Profile saves; the screen hands it to AuthViewModel and leaves. */
    val savedUser: User? = null,
    /** Set after "Save & finish later" has saved; the screen then signs out. */
    val exitAfterSave: Boolean = false
) {
    val busy: Boolean get() = isSaving || isPreparingPhoto
    val player: PlayerProfile? get() = snapshot?.player
    val user: User? get() = snapshot?.user
}

/**
 * Player onboarding, Edit Profile, the profile screen and the player gate.
 * One structured state; Firestore is reached only through [PlayerRepository].
 */
@HiltViewModel
class PlayerProfileViewModel @Inject constructor(
    private val repository: PlayerRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlayerProfileUiState())
    val state: StateFlow<PlayerProfileUiState> = _state.asStateFlow()

    private var loadedFor: String? = null

    /** Loads the signed-in player's documents. Repeated calls for the same user are ignored. */
    fun load(uid: String, mode: PlayerFormMode, force: Boolean = false) {
        if (!force && loadedFor == uid && _state.value.loadError == null && !_state.value.isLoading) return
        loadedFor = uid
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, loadError = null, mode = mode) }
            repository.loadOwn()
                .onSuccess { snapshot ->
                    val form = PlayerProfileForm.fromStored(snapshot.user, snapshot.player)
                    // Resume at the first step that still needs work (a returning, unfinished player).
                    val resumeAt = form.firstInvalidStep() ?: PlayerOnboardingStep.READY
                    _state.update {
                        it.copy(
                            isLoading = false, snapshot = snapshot, form = form, errors = emptyMap(),
                            step = if (mode == PlayerFormMode.ONBOARDING) resumeAt else PlayerOnboardingStep.ABOUT,
                            furthestStep = resumeAt,
                            landing = repository.landingFor(snapshot)
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, loadError = ErrorMessages.from(e, "We couldn't load your profile. Please try again.", "loadPlayer"))
                    }
                }
        }
    }

    /** Used by the player gate: same load, the screen reads [PlayerProfileUiState.landing]. */
    fun resolveLanding(uid: String) = load(uid, PlayerFormMode.ONBOARDING, force = true)

    fun retry() { loadedFor?.let { load(it, _state.value.mode, force = true) } }

    /** Applies an edit and clears the error of the edited field. */
    fun update(field: PlayerField? = null, transform: (PlayerProfileForm) -> PlayerProfileForm) {
        _state.update { s -> s.copy(form = transform(s.form), errors = if (field != null) s.errors - field else s.errors) }
    }

    /** Validates the current step, saves progress (so the player can resume later), then moves on. */
    fun next() {
        val s = _state.value
        if (s.busy) return
        val errors = s.form.validate(s.step)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, message = "Please fix the highlighted fields.") }
            return
        }
        val nextStep = PlayerOnboardingStep.entries.getOrNull(s.step.ordinal + 1) ?: return
        saveProgress { saved ->
            _state.update {
                it.copy(
                    step = nextStep,
                    furthestStep = maxOf(it.furthestStep, nextStep),
                    errors = emptyMap(),
                    message = if (saved) null else "You're offline: your answers are kept here and will be saved when you continue."
                )
            }
        }
    }

    fun back() {
        val prev = PlayerOnboardingStep.entries.getOrNull(_state.value.step.ordinal - 1) ?: return
        _state.update { it.copy(step = prev, errors = emptyMap()) }
    }

    fun goTo(step: PlayerOnboardingStep) {
        if (step.ordinal <= _state.value.furthestStep.ordinal) _state.update { it.copy(step = step, errors = emptyMap()) }
    }

    /** "Save & finish later": saves what is valid so far, then signals the screen to sign out. */
    fun saveAndExit() {
        if (_state.value.busy) return
        saveProgress { _state.update { it.copy(exitAfterSave = true) } }
    }

    /** Last onboarding step: full validation, then the profile is written and marked COMPLETE atomically. */
    fun finishOnboarding() = submit(complete = true)

    /** Edit Profile: same validation and write, the status stays COMPLETE. */
    fun saveEdits() = submit(complete = false)

    fun pickPhoto(file: PickedFile) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(isPreparingPhoto = true) }
            repository.preparePhoto(file)
                .onSuccess { dataUri ->
                    _state.update {
                        it.copy(isPreparingPhoto = false, form = it.form.copy(photoUrl = dataUri),
                            message = "Photo added. It's saved with your profile.")
                    }
                }
                .onFailure { e ->
                    val msg = if (e is InvalidFileException) e.message.orEmpty()
                    else "We couldn't use that photo. Try a different one, or skip it for now."
                    _state.update { it.copy(isPreparingPhoto = false, message = msg) }
                }
        }
    }

    fun removePhoto() = update { it.copy(photoUrl = "") }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun consumeSavedUser() = _state.update { it.copy(savedUser = null) }

    // ── Internals ───────────────────────────────────────────────────────

    private fun saveProgress(onDone: (saved: Boolean) -> Unit) {
        val snapshot = _state.value.snapshot ?: return onDone(false)
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val form = _state.value.form
            val result = repository.saveProgress(form, snapshot)
            _state.update { s ->
                s.copy(
                    isSaving = false,
                    snapshot = if (result.isSuccess) afterWrite(snapshot, form, keepStatus = true) else s.snapshot
                )
            }
            onDone(result.isSuccess)
        }
    }

    private fun submit(complete: Boolean) {
        val s = _state.value
        if (s.busy) return
        val snapshot = s.snapshot ?: return
        val errors = s.form.validateAll()
        if (errors.isNotEmpty()) {
            val firstStep = s.form.firstInvalidStep() ?: s.step
            _state.update {
                it.copy(
                    errors = errors,
                    step = if (it.mode == PlayerFormMode.ONBOARDING) firstStep else it.step,
                    message = "Please fix the highlighted fields."
                )
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            val form = _state.value.form
            val result = if (complete) repository.completeProfile(form, snapshot) else repository.updateProfile(form, snapshot)
            result
                .onSuccess {
                    val updated = afterWrite(snapshot, form, keepStatus = false)
                    _state.update {
                        it.copy(
                            isSaving = false, snapshot = updated,
                            form = it.form.copy(consentAlreadyRecorded = true),
                            savedUser = updated.user,
                            message = if (complete) null else "Profile saved."
                        )
                    }
                }
                .onFailure { e ->
                    if (e is InvalidProfileException) {
                        _state.update { it.copy(isSaving = false, errors = e.errors, message = "Please fix the highlighted fields.") }
                    } else {
                        _state.update {
                            it.copy(isSaving = false, message = ErrorMessages.from(e, "We couldn't save your profile. Please try again.", "savePlayer"))
                        }
                    }
                }
        }
    }

    /** Local copy of what was just written, so screens update without another read. */
    private fun afterWrite(snapshot: PlayerSnapshot, form: PlayerProfileForm, keepStatus: Boolean): PlayerSnapshot {
        val user = snapshot.user
        val now = Date()
        val status = when {
            !keepStatus -> PlayerProfileStatus.COMPLETE.name
            user.profileStatus == PlayerProfileStatus.COMPLETE.name -> user.profileStatus
            else -> PlayerProfileStatus.INCOMPLETE.name
        }
        val consentNow = !keepStatus && !form.consentAlreadyRecorded
        val userFields = form.toUserFields()
        val updatedUser = user.copy(
            name = userFields["name"] as String,
            dateOfBirth = form.dateOfBirth.trim(),
            phoneNumber = form.phoneNumber.trim(),
            gender = form.gender?.name.orEmpty(),
            notificationPreferences = form.notifications,
            profileStatus = status,
            termsAcceptedAt = if (consentNow) now else user.termsAcceptedAt,
            privacyAcceptedAt = if (consentNow) now else user.privacyAcceptedAt,
            updatedAt = now
        )
        val existing = snapshot.player
        val stored = PlayerProfile(
            uid = user.uid,
            displayName = updatedUser.name,
            photoUrl = form.photoUrl,
            country = form.country.trim(),
            state = form.state.trim(),
            city = form.city.trim(),
            region = form.region,
            areaType = form.areaType?.name.orEmpty(),
            primarySport = form.primarySport,
            secondarySports = form.secondarySports,
            sportProfile = form.sportProfile,
            skillLevel = form.skillLevel?.name.orEmpty(),
            goals = form.goals.map { it.name },
            coachingPreferences = com.athlink.app.data.model.CoachingPreferences(
                format = form.coachingFormat?.name.orEmpty(),
                trainingTime = form.trainingTime?.name.orEmpty(),
                maxDistanceKm = form.travelDistance?.km
            ),
            yearsOfExperience = form.yearsOfExperience.trim().toIntOrNull(),
            currentTeam = form.currentTeam.trim(),
            academy = form.academy.trim(),
            achievements = form.achievements,
            bio = form.bio.trim(),
            ranking = form.ranking.trim(),
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        return PlayerSnapshot(updatedUser, stored)
    }
}
