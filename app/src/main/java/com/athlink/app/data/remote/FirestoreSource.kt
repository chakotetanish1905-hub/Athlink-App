package com.athlink.app.data.remote

import com.athlink.app.data.model.AcademyRequest
import com.athlink.app.data.model.AcademyRequestStatus
import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.CoachAvailability
import com.athlink.app.data.model.CoachPrivateProfile
import com.athlink.app.data.model.Organisation
import com.athlink.app.data.model.OrganisationVerificationStatus
import com.athlink.app.data.model.SessionPolicy
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.model.VerificationStatus
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
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
    /**
     * Coaches players can find and book: VERIFIED (server-side filter) and ACTIVE (checked with
     * [SessionPolicy.isBookable], which also covers documents with unexpected values).
     */
    suspend fun getCoaches(): Result<List<Coach>> = try {
        val snapshot = firestore.collection(FirestorePaths.COACHES)
            .whereEqualTo("verificationStatus", VerificationStatus.VERIFIED.name)
            .get().await()
        val coaches = snapshot.documents.mapNotNull { doc ->
            doc.toObject(Coach::class.java)?.let { if (it.uid.isBlank()) it.copy(uid = doc.id) else it }
        }.filter { SessionPolicy.isBookable(it) }
        Result.success(coaches)
    } catch (e: Exception) { Result.failure(e) }

    /** Weekly availability ranges of a coach (`coaches/{uid}/availability`). */
    suspend fun getCoachAvailability(coachId: String): Result<List<CoachAvailability>> = try {
        val snapshot = firestore.collection(FirestorePaths.COACHES).document(coachId)
            .collection(FirestorePaths.AVAILABILITY).get().await()
        Result.success(snapshot.documents.mapNotNull { it.toObject(CoachAvailability::class.java) })
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
    /**
     * Books a slot. The document id is [SessionPolicy.slotId], so firestore.rules refuse the write
     * when somebody else already holds that slot ([SlotTakenException]).
     */
    suspend fun bookSession(session: Session): Result<Session> = try {
        val id = SessionPolicy.slotId(session.coachId, session.date, session.startTime)
        val withId = session.copy(id = id)
        firestore.collection(FirestorePaths.SESSIONS).document(id).set(withId).await()
        Result.success(withId)
    } catch (e: FirebaseFirestoreException) {
        Result.failure(if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) SlotTakenException() else e)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getPlayerSessions(playerId: String): Result<List<Session>> = try {
        val snapshot = firestore.collection(FirestorePaths.SESSIONS)
            .whereEqualTo("playerId", playerId)
            .get().await()
        val sessions = snapshot.documents.mapNotNull { it.toObject(Session::class.java) }
        Result.success(sessions)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getCoachSessions(coachId: String): Result<List<Session>> = try {
        val snapshot = firestore.collection(FirestorePaths.SESSIONS)
            .whereEqualTo("coachId", coachId)
            .get().await()
        val sessions = snapshot.documents.mapNotNull { it.toObject(Session::class.java) }
        Result.success(sessions)
    } catch (e: Exception) { Result.failure(e) }

    // ─── Academies (verified organisations) ───────────────────────
    /** Verified / official organisations (single-field `in` query, no composite index). */
    suspend fun getListedOrganisations(): Result<List<Organisation>> = try {
        val snapshot = firestore.collection(FirestorePaths.ORGANISATIONS)
            .whereIn("verificationStatus", listOf(
                OrganisationVerificationStatus.VERIFIED.name, OrganisationVerificationStatus.OFFICIAL_GOVERNMENT.name
            ))
            .get().await()
        Result.success(snapshot.documents.mapNotNull { doc ->
            doc.toObject(Organisation::class.java)?.let { if (it.organisationId.isBlank()) it.copy(organisationId = doc.id) else it }
        })
    } catch (e: Exception) { Result.failure(e) }

    /** Bookable coaches linked to an academy (`coaches.academyIds` contains it). */
    suspend fun getAcademyCoaches(organisationId: String): Result<List<Coach>> = try {
        val snapshot = firestore.collection(FirestorePaths.COACHES)
            .whereArrayContains("academyIds", organisationId)
            .get().await()
        Result.success(snapshot.documents.mapNotNull { doc ->
            doc.toObject(Coach::class.java)?.let { if (it.uid.isBlank()) it.copy(uid = doc.id) else it }
        }.filter { SessionPolicy.isBookable(it) }.sortedBy { it.name.lowercase() })
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getOrganisation(id: String): Result<Organisation?> = try {
        val doc = firestore.collection(FirestorePaths.ORGANISATIONS).document(id).get().await()
        Result.success(doc.takeIf { it.exists() }?.toObject(Organisation::class.java))
    } catch (e: Exception) { Result.failure(e) }

    /** Creates a PENDING request with server timestamps (rules check every field). */
    suspend fun createAcademyRequest(request: AcademyRequest): Result<String> = try {
        val ref = firestore.collection(FirestorePaths.ACADEMY_REQUESTS).document()
        ref.set(mapOf(
            "requestId" to ref.id,
            "organisationId" to request.organisationId,
            "organisationName" to request.organisationName,
            "organisationLocation" to request.organisationLocation,
            "organisationOwnerUid" to request.organisationOwnerUid,
            "coachId" to request.coachId,
            "coachName" to request.coachName,
            "playerId" to request.playerId,
            "playerName" to request.playerName,
            "sport" to request.sport,
            "preferredDate" to request.preferredDate,
            "preferredTime" to request.preferredTime,
            "message" to request.message,
            "contactPhone" to request.contactPhone,
            "status" to AcademyRequestStatus.PENDING.name,
            "responseNote" to "",
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun getPlayerAcademyRequests(playerId: String): Result<List<AcademyRequest>> = try {
        val snapshot = firestore.collection(FirestorePaths.ACADEMY_REQUESTS)
            .whereEqualTo("playerId", playerId).get().await()
        Result.success(snapshot.documents.mapNotNull { it.toObject(AcademyRequest::class.java) }
            .sortedByDescending { it.createdAt })
    } catch (e: Exception) { Result.failure(e) }

    /**
     * Requests sent to the organisation owned by [ownerUid]. Queried by owner (not organisation id)
     * because that is what firestore.rules check for reads.
     */
    suspend fun getOrganisationAcademyRequests(ownerUid: String): Result<List<AcademyRequest>> = try {
        val snapshot = firestore.collection(FirestorePaths.ACADEMY_REQUESTS)
            .whereEqualTo("organisationOwnerUid", ownerUid).get().await()
        Result.success(snapshot.documents.mapNotNull { it.toObject(AcademyRequest::class.java) }
            .sortedByDescending { it.createdAt })
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateAcademyRequest(requestId: String, status: AcademyRequestStatus, note: String? = null): Result<Unit> = try {
        val fields = mutableMapOf<String, Any>("status" to status.name, "updatedAt" to FieldValue.serverTimestamp())
        if (note != null) fields["responseNote"] = note
        firestore.collection(FirestorePaths.ACADEMY_REQUESTS).document(requestId).update(fields).await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun updateSessionStatus(sessionId: String, status: String): Result<Unit> = try {
        firestore.collection(FirestorePaths.SESSIONS).document(sessionId)
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

    /** Published events of one organisation (single-field query, no composite index needed). */
    suspend fun getOrganisationEvents(organisationId: String): Result<List<Event>> = try {
        val snapshot = firestore.collection(FirestorePaths.EVENTS)
            .whereEqualTo("organisationId", organisationId).get().await()
        Result.success(snapshot.documents.mapNotNull { it.toObject(Event::class.java) }.sortedByDescending { it.createdAt })
    } catch (e: Exception) { Result.failure(e) }

    /** The organisation's private drafts (`eventDrafts`, owner-only in rules). */
    suspend fun getEventDrafts(organisationId: String): Result<List<Event>> = try {
        val snapshot = firestore.collection(FirestorePaths.EVENT_DRAFTS)
            .whereEqualTo("organisationId", organisationId).get().await()
        Result.success(snapshot.documents.mapNotNull { it.toObject(Event::class.java) }.sortedByDescending { it.createdAt })
    } catch (e: Exception) { Result.failure(e) }

    /** Creates or overwrites a draft. Returns the draft id. */
    suspend fun saveEventDraft(event: Event): Result<String> = try {
        val col = firestore.collection(FirestorePaths.EVENT_DRAFTS)
        val ref = if (event.id.isBlank()) col.document() else col.document(event.id)
        ref.set(event.copy(id = ref.id)).await()
        Result.success(ref.id)
    } catch (e: Exception) { Result.failure(e) }

    suspend fun deleteEventDraft(draftId: String): Result<Unit> = try {
        firestore.collection(FirestorePaths.EVENT_DRAFTS).document(draftId).delete().await()
        Result.success(Unit)
    } catch (e: Exception) { Result.failure(e) }

    /**
     * Publishes [event] to `events` (and deletes the draft it came from, in the same batch).
     * firestore.rules reject this unless the organisation is VERIFIED / OFFICIAL_GOVERNMENT,
     * unexpired, and the event's organisationName / organisationVerificationLevel match its
     * organisation document.
     */
    suspend fun publishEvent(event: Event, fromDraftId: String?): Result<String> = try {
        val ref = firestore.collection(FirestorePaths.EVENTS).document()
        val batch = firestore.batch().set(ref, event.copy(id = ref.id))
        if (!fromDraftId.isNullOrBlank()) batch.delete(firestore.collection(FirestorePaths.EVENT_DRAFTS).document(fromDraftId))
        batch.commit().await()
        Result.success(ref.id)
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

/** The slot was booked by someone else in the meantime (rules refused the write). */
class SlotTakenException : IllegalStateException("This slot is no longer available. Please pick another time.")

/** Raw result of [FirestoreSource.getOwnCoachDocuments]. */
data class OwnCoachDocuments(
    val coach: Coach?,
    val privateProfile: CoachPrivateProfile?,
    val legacyEmail: String,
    val legacyPhone: String
)
