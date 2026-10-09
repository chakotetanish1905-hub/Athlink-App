package com.athlink.app.data.repository

import com.athlink.app.data.model.CoachAvailability
import com.athlink.app.data.model.Session
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.remote.FirestoreSource
import javax.inject.Inject
import javax.inject.Singleton

/** Real bookings only: no dummy fallback (an empty list means "no sessions yet"). */
@Singleton
class SessionRepository @Inject constructor(
    private val firestoreSource: FirestoreSource
) {
    suspend fun bookSession(session: Session): Result<Session> =
        firestoreSource.bookSession(session)

    suspend fun getPlayerSessions(playerId: String): Result<List<Session>> =
        firestoreSource.getPlayerSessions(playerId)

    suspend fun getCoachSessions(coachId: String): Result<List<Session>> =
        firestoreSource.getCoachSessions(coachId)

    suspend fun getCoachAvailability(coachId: String): Result<List<CoachAvailability>> =
        firestoreSource.getCoachAvailability(coachId)

    suspend fun saveAvailability(coachId: String, range: CoachAvailability): Result<CoachAvailability> =
        firestoreSource.saveAvailability(coachId, range)

    suspend fun deleteAvailability(coachId: String, availabilityId: String): Result<Unit> =
        firestoreSource.deleteAvailability(coachId, availabilityId)

    suspend fun updateSessionStatus(sessionId: String, status: SessionStatus): Result<Unit> =
        firestoreSource.updateSessionStatus(sessionId, status.name)
}
