package com.athlink.app.data.model

/**
 * A professional reference: `coaches/{uid}/references/{referenceId}`.
 *
 * PRIVATE: it holds a third party's contact details, so only the coach and admins can read it
 * (players never can, even for ACTIVE coaches). Optional today; [CoachProfileRules] can make it
 * mandatory for a verification level later.
 */
data class CoachReference(
    val referenceId: String = "",
    val name: String = "",
    val organisation: String = "",
    val relationship: String = "",
    /** Email or phone of the referee, given with their consent. */
    val contact: String = "",
    /** [CheckStatus] name. Coach can write NOT_SUBMITTED/PENDING only. */
    val status: String = CheckStatus.NOT_SUBMITTED.name,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)
