package com.athlink.app.data.repository

import com.athlink.app.data.model.Coach
import com.athlink.app.data.model.CoachPrivateProfile
import com.athlink.app.data.model.CoachProfileSnapshot
import com.athlink.app.data.remote.FirestoreSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CoachRepository @Inject constructor(
    private val firestoreSource: FirestoreSource
) {
    /** Bookable coaches only (VERIFIED + ACTIVE). Real data only: empty means none yet. */
    suspend fun getCoaches(): Result<List<Coach>> = firestoreSource.getCoaches()

    suspend fun getCoachById(coachId: String): Result<Coach> = firestoreSource.getCoachById(coachId)

    suspend fun searchCoaches(query: String, sport: String?): List<Coach> {
        val all = getCoaches().getOrDefault(emptyList())
        return all.filter { coach ->
            (query.isEmpty() || coach.name.contains(query, true) || coach.sport.contains(query, true)) &&
            (sport == null || coach.sport.equals(sport, true))
        }
    }

    /**
     * The signed-in coach's OWN profile. Never falls back to dummy data: a coach must see
     * exactly what is stored for them.
     *
     * Returns `success(null)` when no `coaches/{uid}` document exists (e.g. an account created
     * before coach registration wrote one), and a failure for network/permission errors.
     * [accountEmail] (from `users/{uid}` / Firebase Auth) fills the email for older accounts.
     */
    suspend fun getOwnProfile(uid: String, accountEmail: String): Result<CoachProfileSnapshot?> =
        firestoreSource.getOwnCoachDocuments(uid).map { docs ->
            val coach = docs.coach ?: return@map null
            val stored = docs.privateProfile ?: CoachPrivateProfile(uid = uid)
            val privateProfile = stored.copy(
                email = stored.email.ifBlank { docs.legacyEmail.ifBlank { accountEmail } },
                phone = stored.phone.ifBlank { docs.legacyPhone }
            )
            CoachProfileSnapshot(coach = coach, privateProfile = privateProfile)
        }
}
