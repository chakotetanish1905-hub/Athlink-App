package com.athlink.app.data.repository

import com.athlink.app.data.model.DummyData
import com.athlink.app.data.model.Event
import com.athlink.app.data.model.Message
import com.athlink.app.data.remote.FirestoreSource
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepository @Inject constructor(
    private val firestoreSource: FirestoreSource
) {
    suspend fun getEvents(): Result<List<Event>> {
        val result = firestoreSource.getEvents()
        return if (result.isSuccess && result.getOrNull()!!.isNotEmpty()) result
        else Result.success(DummyData.events)
    }

    suspend fun createEvent(event: Event): Result<Unit> =
        firestoreSource.createEvent(event)

    // ── Organisation's own events: real data only, never the dummy fallback ──

    suspend fun getOrganisationEvents(organisationId: String): Result<List<Event>> =
        firestoreSource.getOrganisationEvents(organisationId)

    suspend fun getEventDrafts(organisationId: String): Result<List<Event>> =
        firestoreSource.getEventDrafts(organisationId)

    suspend fun saveEventDraft(event: Event): Result<String> = firestoreSource.saveEventDraft(event)

    suspend fun deleteEventDraft(draftId: String): Result<Unit> = firestoreSource.deleteEventDraft(draftId)

    suspend fun publishEvent(event: Event, fromDraftId: String?): Result<String> =
        firestoreSource.publishEvent(event, fromDraftId)
}

@Singleton
class ChatRepository @Inject constructor(
    private val firestoreSource: FirestoreSource
) {
    fun getMessages(threadId: String): Flow<List<Message>> =
        firestoreSource.getMessages(threadId)

    suspend fun sendMessage(threadId: String, message: Message): Result<Unit> =
        firestoreSource.sendMessage(threadId, message)
}
