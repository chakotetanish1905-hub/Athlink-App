package com.athlink.app.data.repository

import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.DummyData
import com.athlink.app.data.remote.FirestoreSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepository @Inject constructor(
    private val firestoreSource: FirestoreSource
) {
    // Falls back to dummy data if Firebase returns empty
    suspend fun getCoaches(): Result<List<Coach>> {
        val result = firestoreSource.getCoaches()
        return if (result.isSuccess && result.getOrNull()!!.isNotEmpty()) {
            result
        } else {
            Result.success(DummyData.coaches)
        }
    }

    suspend fun getCoachById(coachId: String): Result<Coach> {
        val result = firestoreSource.getCoachById(coachId)
        return if (result.isSuccess) result
        else Result.success(DummyData.coaches.first { it.uid == coachId })
    }

    suspend fun searchCoaches(query: String, sport: String?): List<Coach> {
        val all = getCoaches().getOrDefault(DummyData.coaches)
        return all.filter { coach ->
            (query.isEmpty() || coach.name.contains(query, true) || coach.sport.contains(query, true)) &&
            (sport == null || coach.sport.equals(sport, true))
        }
    }
}
