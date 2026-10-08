package com.athlink.app.data.model

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentChunksTest {

    @Test
    fun splitAndJoinRoundTrip() {
        val bytes = ByteArray(2_000_000) { (it % 251).toByte() }
        val parts = DocumentChunks.split(bytes)
        assertEquals(3, parts.size)
        assertTrue(parts.all { it.size <= DocumentChunks.CHUNK_BYTES })
        // Order of arrival doesn't matter.
        val joined = DocumentChunks.join(parts.mapIndexed { i, p -> i to p }.reversed(), parts.size)
        assertArrayEquals(bytes, joined)
    }

    @Test
    fun maxFileFitsInMaxChunks() {
        val parts = DocumentChunks.split(ByteArray(DocumentChunks.MAX_STORED_BYTES.toInt()))
        assertTrue(parts.size <= DocumentChunks.MAX_CHUNKS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingChunkIsDetected() {
        val parts = DocumentChunks.split(ByteArray(1_500_000))
        DocumentChunks.join(listOf(0 to parts[0], 2 to parts[2]), parts.size)
    }

    @Test
    fun preUploadValidation() {
        // PDFs must fit as-is; large photos are allowed because they get compressed.
        assertNotNull(OrganisationValidators.document("application/pdf", DocumentChunks.MAX_STORED_BYTES + 1))
        assertNull(OrganisationValidators.document("image/jpeg", 8L * 1024 * 1024))
        assertNotNull(OrganisationValidators.document("image/jpeg", DocumentChunks.MAX_RAW_IMAGE_BYTES + 1))
        assertNull(OrganisationValidators.document("application/pdf", -1)) // unknown size: checked after reading
        assertEquals("firestore:organisationDocuments/abc/chunks", DocumentChunks.storagePath("abc"))
    }
}
