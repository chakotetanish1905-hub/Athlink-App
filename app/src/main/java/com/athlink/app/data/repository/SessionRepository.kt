package com.athlink.app.data.repository

import com.athlink.app.data.model.DummyData
import com.athlink.app.data.model.Session
import com.athlink.app.data.model.SessionStatus
import com.athlink.app.data.remote.FirestoreSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRepository @Inject constructor(
    private val firestoreSource: FirestoreSource
) {
    suspend fun bookSession(session: Session): Result<Unit> =
        firestoreSource.bookSession(session)

    suspend fun getPlayerSessions(playerId: String): Result<List<Session>> {
        val result = firestoreSource.getPlayerSessions(playerId)
        return if (result.isSuccess && result.getOrNull()!!.isNotEmpty()) result
        else Result.success(DummyData.sessions)
    }

    suspend fun getCoachSessions(coachId: String): Result<List<Session>> {
        val result = firestoreSource.getCoachSessions(coachId)
        return if (result.isSuccess && result.getOrNull()!!.isNotEmpty()) result
        else Result.success(DummyData.sessions.filter { it.coachId == coachId })
    }

    suspend fun updateSessionStatus(sessionId: String, status: SessionStatus): Result<Unit> =
        firestoreSource.updateSessionStatus(sessionId, status.name)
}
