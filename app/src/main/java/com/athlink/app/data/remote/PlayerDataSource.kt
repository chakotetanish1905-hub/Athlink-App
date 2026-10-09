package com.athlink.app.data.remote

import com.athlink.app.data.model.PickedFile
import com.athlink.app.data.model.PlayerProfile
import com.athlink.app.data.model.PlayerProfileForm
import com.athlink.app.data.model.PlayerProfileStatus
import com.athlink.app.data.model.PlayerSignupForm
import com.athlink.app.data.model.User
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** The signed-in player's private account record plus their public profile (null until first saved). */
data class PlayerSnapshot(val user: User, val player: PlayerProfile?)

/** Thrown when there is no Firebase Auth user; the UID is never taken from the UI. */
class NotSignedInException : IllegalStateException("Not signed in")

/**
 * The server didn't confirm a write in time (usually offline). Firestore keeps the write queued and
 * sends it when the connection returns, so nothing is lost; the UI just stops waiting.
 */
class SaveNotConfirmedException : IllegalStateException("Write not confirmed by the server in time")

/**
 * Firestore calls for player profiles. The document id is ALWAYS the Firebase Auth uid of the
 * signed-in user, read here, never passed in from the UI.
 *
 * `users/{uid}` (private) and `players/{uid}` (public) are always written in ONE batch, so the
 * profile status can only become COMPLETE together with the data it describes.
 */
@Singleton
class PlayerDataSource @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val fileReader: FileBytesReader
) {
    private fun requireUid(): String = auth.currentUser?.uid ?: throw NotSignedInException()
    private fun userRef(uid: String) = firestore.collection(FirestorePaths.USERS).document(uid)
    private fun playerRef(uid: String) = firestore.collection(FirestorePaths.PLAYERS).document(uid)

    /** Loads the signed-in player's documents. A missing `players` doc is normal (new / legacy player). */
    suspend fun loadOwn(): Result<PlayerSnapshot> = try {
        val uid = requireUid()
        val userDoc = userRef(uid).get().await()
        val user = userDoc.toObject(User::class.java) ?: throw IllegalStateException("Account record not found")
        val playerDoc = playerRef(uid).get().await()
        Result.success(PlayerSnapshot(user, playerDoc.takeIf { it.exists() }?.toObject(PlayerProfile::class.java)))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Public profile of any player (signed-in users only). Never includes private account data. */
    suspend fun getPlayer(uid: String): Result<PlayerProfile?> = try {
        val doc = playerRef(uid).get().await()
        Result.success(doc.takeIf { it.exists() }?.toObject(PlayerProfile::class.java))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Writes the form to `players/{uid}` and `users/{uid}` in one batch.
     *
     * @param playerExists whether `players/{uid}` already exists (create needs `createdAt`; updates
     *   use `update` so whole maps such as `sportProfile` are replaced, not deep-merged).
     * @param status new profile status, or null to leave it unchanged.
     * @param recordConsent true when the player accepted Terms / Privacy in this form (accounts
     *   created before player onboarding existed); stamps server-time consent once.
     */
    suspend fun save(
        form: PlayerProfileForm,
        playerExists: Boolean,
        status: PlayerProfileStatus?,
        recordConsent: Boolean
    ): Result<Unit> = try {
        val uid = requireUid()
        val playerFields = form.toPlayerFields(uid) + ("updatedAt" to FieldValue.serverTimestamp())
        val userFields = buildMap<String, Any?> {
            putAll(form.toUserFields())
            put("updatedAt", FieldValue.serverTimestamp())
            if (status != null) put("profileStatus", status.name)
            if (recordConsent) {
                put("termsAcceptedAt", FieldValue.serverTimestamp())
                put("privacyAcceptedAt", FieldValue.serverTimestamp())
                put("termsVersion", PlayerSignupForm.TERMS_VERSION)
            }
        }
        val batch = firestore.batch()
        if (playerExists) {
            batch.update(playerRef(uid), playerFields)
        } else {
            batch.set(playerRef(uid), playerFields + ("createdAt" to FieldValue.serverTimestamp()))
        }
        batch.update(userRef(uid), userFields)
        // commit() only completes once the server acknowledges it, which never happens offline.
        val confirmed = withTimeoutOrNull(SAVE_TIMEOUT_MS) { batch.commit().await(); true } ?: false
        if (!confirmed) throw SaveNotConfirmedException()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    companion object {
        const val SAVE_TIMEOUT_MS = 15_000L
    }

    /** Shrinks the picked photo to a small JPEG data URI. Nothing is written until the profile is saved. */
    suspend fun preparePhoto(file: PickedFile): Result<String> = try {
        Result.success(fileReader.prepareProfilePhotoDataUri(file.uri))
    } catch (e: Exception) {
        Result.failure(e)
    }
}
