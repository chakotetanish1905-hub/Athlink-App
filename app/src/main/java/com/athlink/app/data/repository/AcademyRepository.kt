package com.athlink.app.data.repository

import com.athlink.app.data.model.AcademyDirectory
import com.athlink.app.data.model.AcademyRequest
import com.athlink.app.data.model.AcademyRequestStatus
import com.athlink.app.data.model.Organisation
import com.athlink.app.data.remote.FirestoreSource
import javax.inject.Inject
import javax.inject.Singleton

/** Academies players can browse (verified organisations, incl. the imported directory) and requests to them. */
@Singleton
class AcademyRepository @Inject constructor(
    private val firestoreSource: FirestoreSource
) {
    /** Cached for the app session; [refresh] forces a reload. */
    @Volatile private var cache: List<Organisation>? = null

    suspend fun getAcademies(refresh: Boolean = false): Result<List<Organisation>> {
        if (!refresh) cache?.let { return Result.success(it) }
        return firestoreSource.getListedOrganisations()
            .map { list -> list.filter { AcademyDirectory.isListed(it) } }
            .onSuccess { cache = it }
    }

    suspend fun getAcademy(id: String): Result<Organisation?> {
        cache?.firstOrNull { it.organisationId == id }?.let { return Result.success(it) }
        return firestoreSource.getOrganisation(id)
    }

    suspend fun sendRequest(request: AcademyRequest): Result<String> = firestoreSource.createAcademyRequest(request)

    suspend fun getPlayerRequests(playerId: String): Result<List<AcademyRequest>> =
        firestoreSource.getPlayerAcademyRequests(playerId)

    /** Requests to the academy owned by [ownerUid] (an organisation's id is its owner's uid). */
    suspend fun getOrganisationRequests(ownerUid: String): Result<List<AcademyRequest>> =
        firestoreSource.getOrganisationAcademyRequests(ownerUid)

    suspend fun cancelRequest(requestId: String): Result<Unit> =
        firestoreSource.updateAcademyRequest(requestId, AcademyRequestStatus.CANCELLED)

    suspend fun respond(requestId: String, status: AcademyRequestStatus, note: String): Result<Unit> =
        firestoreSource.updateAcademyRequest(requestId, status, note.trim())
}
