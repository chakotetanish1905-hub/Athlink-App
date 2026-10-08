package com.athlink.app.data.model

/**
 * Private document storage on the Firebase Spark plan (no Cloud Storage).
 *
 * A verification document is stored as metadata in `organisationDocuments/{id}` plus its bytes
 * split into `organisationDocuments/{id}/chunks/{index}` documents (`{ index, data: Blob }`).
 * Firestore documents are limited to 1 MiB, so each chunk holds at most [CHUNK_BYTES].
 * firestore.rules give the chunks the same owner + admin-only access as the metadata.
 *
 * Switching back to Cloud Storage later only needs a new upload path in OrganisationDataSource;
 * metadata carries [STORAGE_BACKEND] so both kinds can coexist.
 */
object DocumentChunks {
    /** Below Firestore's 1 MiB document limit with room for field overhead. */
    const val CHUNK_BYTES = 700 * 1024
    /** Largest file we store (after image compression). */
    const val MAX_STORED_BYTES = 3L * 1024 * 1024
    /** ceil(MAX_STORED_BYTES / CHUNK_BYTES); also enforced in firestore.rules. */
    const val MAX_CHUNKS = 5
    const val STORAGE_BACKEND = "FIRESTORE"
    /** Images larger than this are rejected before compression is even attempted. */
    const val MAX_RAW_IMAGE_BYTES = 25L * 1024 * 1024

    fun split(bytes: ByteArray, chunkSize: Int = CHUNK_BYTES): List<ByteArray> {
        require(bytes.isNotEmpty()) { "Empty file" }
        require(chunkSize > 0)
        return (bytes.indices step chunkSize).map { start -> bytes.copyOfRange(start, minOf(start + chunkSize, bytes.size)) }
    }

    /** Joins chunks given as (index, bytes) in any order; fails if one is missing. */
    fun join(chunks: List<Pair<Int, ByteArray>>, expectedCount: Int): ByteArray {
        val sorted = chunks.sortedBy { it.first }
        require(sorted.size == expectedCount && sorted.mapIndexed { i, c -> c.first == i }.all { it }) {
            "Document is incomplete (${sorted.size}/$expectedCount parts)"
        }
        val out = ByteArray(sorted.sumOf { it.second.size })
        var pos = 0
        sorted.forEach { (_, part) -> part.copyInto(out, pos); pos += part.size }
        return out
    }

    /** Storage reference written to metadata, matched by firestore.rules. */
    fun storagePath(documentId: String) = "firestore:organisationDocuments/$documentId/chunks"
}
