package com.athlink.app.data.repository

import com.athlink.app.data.model.PickedFile
import com.athlink.app.data.model.PlayerField
import com.athlink.app.data.model.PlayerLanding
import com.athlink.app.data.model.PlayerProfile
import com.athlink.app.data.model.PlayerProfileForm
import com.athlink.app.data.model.PlayerProfilePolicy
import com.athlink.app.data.model.PlayerProfileStatus
import com.athlink.app.data.remote.PlayerDataSource
import com.athlink.app.data.remote.PlayerSnapshot
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** The form failed validation in the repository (second line of defence after the ViewModel). */
class InvalidProfileException(val errors: Map<PlayerField, String>) :
    IllegalArgumentException("Profile has ${errors.size} invalid field(s)")

/**
 * Player profile repository. Like the organisation area it never falls back to dummy data: a
 * player must always see exactly what is stored for them.
 */
@Singleton
class PlayerRepository @Inject constructor(
    private val source: PlayerDataSource
) {
    suspend fun loadOwn(): Result<PlayerSnapshot> = source.loadOwn()

    suspend fun getPlayer(uid: String): Result<PlayerProfile?> = source.getPlayer(uid)

    fun landingFor(snapshot: PlayerSnapshot, today: LocalDate = LocalDate.now()): PlayerLanding =
        PlayerProfilePolicy.landing(snapshot.user, snapshot.player, today)

    /**
     * Saves onboarding progress (called on every Next) so an unfinished profile can be resumed.
     * Never downgrades a COMPLETE profile; never marks anything COMPLETE.
     */
    suspend fun saveProgress(form: PlayerProfileForm, snapshot: PlayerSnapshot): Result<Unit> {
        val markIncomplete = snapshot.user.profileStatus != PlayerProfileStatus.COMPLETE.name
        return source.save(
            form = form,
            playerExists = snapshot.player != null,
            status = if (markIncomplete) PlayerProfileStatus.INCOMPLETE else null,
            recordConsent = false
        )
    }

    /**
     * Finishes onboarding: re-validates EVERYTHING, then writes both documents and marks the profile
     * COMPLETE in the same batch. If the write fails nothing changes and the user can retry.
     */
    suspend fun completeProfile(form: PlayerProfileForm, snapshot: PlayerSnapshot, today: LocalDate = LocalDate.now()): Result<Unit> {
        val errors = form.validateAll(today)
        if (errors.isNotEmpty()) return Result.failure(InvalidProfileException(errors))
        return source.save(
            form = form,
            playerExists = snapshot.player != null,
            status = PlayerProfileStatus.COMPLETE,
            recordConsent = !form.consentAlreadyRecorded
        )
    }

    /** Edit Profile: same full validation; the status stays COMPLETE. */
    suspend fun updateProfile(form: PlayerProfileForm, snapshot: PlayerSnapshot, today: LocalDate = LocalDate.now()): Result<Unit> {
        val errors = form.validateAll(today)
        if (errors.isNotEmpty()) return Result.failure(InvalidProfileException(errors))
        return source.save(
            form = form,
            playerExists = snapshot.player != null,
            status = PlayerProfileStatus.COMPLETE,
            recordConsent = !form.consentAlreadyRecorded
        )
    }

    /** Validates the picked image and turns it into a small data URI (not saved yet). */
    suspend fun preparePhoto(file: PickedFile): Result<String> {
        val mime = file.mimeType.orEmpty()
        if (!mime.startsWith("image/")) return Result.failure(InvalidFileException("Choose a photo (JPG, PNG or WebP)"))
        if (file.sizeBytes > MAX_PHOTO_SOURCE_BYTES) {
            return Result.failure(InvalidFileException("That photo is too large. Choose one under 15 MB."))
        }
        return source.preparePhoto(file)
    }

    companion object {
        /** Limit on the ORIGINAL picked file; it is compressed to ~80 KB before being saved. */
        const val MAX_PHOTO_SOURCE_BYTES = 15L * 1024 * 1024
    }
}
