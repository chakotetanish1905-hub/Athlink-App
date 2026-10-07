package com.athlink.app.data.remote

import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.CoachPrivateProfile
import com.athlink.app.data.model.Event
import com.athlink.app.data.model.Message
import com.athlink.app.data.model.Session
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreSource @Inject constructor(
    private val firestore: FirebaseFirestore
) {

    // ─── Coaches ───────────────────────────────────────────────
    suspend fun getCoaches(): Result<List<Coach>> = try {
        val snapshot = firestore.collection(FirestorePaths.COACHES).get().await()
        val coaches = snapshot.documents.mapNotNull { it.toObject(Coach::class.java) }
        Result.success(coaches)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getCoachById(coachId: String): Result<Coach> = try {
        val doc = firestore.collection(FirestorePaths.COACHES).document(coachId).get().await()
        val coach = doc.toObject(Coach::class.java) ?: throw Exception("Coach not found")
        Result.success(coach)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * The signed-in coach's own profile documents: `coaches/{uid}` and `coachPrivate/{uid}`.
     * A missing document is returned as `null` (not an error), so the caller can tell
     * "no profile yet" apart from "network / permission failure".
     *
     * Coaches registered before the private doc existed have email/phone inside
     * `coaches/{uid}`; those are returned as [legacyEmail] / [legacyPhone] so they still show.
     */
    suspend fun getOwnCoachDocuments(uid: String): Result<OwnCoachDocuments> = try {
        val coachDoc = firestore.collection(FirestorePaths.COACHES).document(uid).get().await()
        val privateDoc = firestore.collection(FirestorePaths.COACH_PRIVATE).document(uid).get().await()
        Result.success(
            OwnCoachDocuments(
                coach = coachDoc.takeIf { it.exists() }?.toObject(Coach::class.java),
                privateProfile = privateDoc.takeIf { it.exists() }?.toObject(CoachPrivateProfile::class.java),
                legacyEmail = coachDoc.getString("email").orEmpty(),
                legacyPhone = coachDoc.getString("phone").orEmpty()
            )
        )
    } catch (e: Exception) { Result.failure(e) }

    // ─── Sessions ───────────────────────────────────────────────
    suspend fun bookSession(session: Session): Result<Unit> = try {
        val ref = firestore.collection("sessions").document()
        val withId = session.copy(id = ref.id)
        ref.set(withId).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getPlayerSessions(playerId: String): Result<List<Session>> = try {
        val snapshot = firestore.collection("sessions")
            .whereEqualTo("playerId", playerId)
            .get().await()
        val sessions = snapshot.documents.mapNotNull { it.toObject(Session::class.java) }
        Result.success(sessions)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getCoachSessions(coachId: String): Result<List<Session>> = try {
        val snapshot = firestore.collection("sessions")
            .whereEqualTo("coachId", coachId)
            .get().await()
        val sessions = snapshot.documents.mapNotNull { it.toObject(Session::class.java) }
        Result.success(sessions)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateSessionStatus(sessionId: String, status: String): Result<Unit> = try {
        firestore.collection("sessions").document(sessionId)
            .update("status", status).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ─── Events ─────────────────────────────────────────────────
    suspend fun getEvents(): Result<List<Event>> = try {
        val snapshot = firestore.collection("events").get().await()
        val events = snapshot.documents.mapNotNull { it.toObject(Event::class.java) }
        Result.success(events)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun createEvent(event: Event): Result<Unit> = try {
        val ref = firestore.collection("events").document()
        val withId = event.copy(id = ref.id)
        ref.set(withId).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    // ─── Messages ────────────────────────────────────────────────
    fun getMessages(threadId: String): Flow<List<Message>> = callbackFlow {
        val listener = firestore.collection("chats")
            .document(threadId)
            .collection("messages")
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val messages = snapshot?.documents?.mapNotNull {
                    it.toObject(Message::class.java)
                } ?: emptyList()
                trySend(messages)
            }
        awaitClose { listener.remove() }
    }

    suspend fun sendMessage(threadId: String, message: Message): Result<Unit> = try {
        val ref = firestore.collection("chats")
            .document(threadId)
            .collection("messages")
            .document()
        val withId = message.copy(id = ref.id)
        ref.set(withId).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }
}

/** Raw result of [FirestoreSource.getOwnCoachDocuments]. */
data class OwnCoachDocuments(
    val coach: Coach?,
    val privateProfile: CoachPrivateProfile?,
    val legacyEmail: String,
    val legacyPhone: String
)
