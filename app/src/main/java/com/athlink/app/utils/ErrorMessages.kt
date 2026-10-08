package com.athlink.app.utils

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.StorageException

/**
 * Turns Firebase exceptions into short, user-facing messages and logs the technical detail.
 * Raw Firebase messages are never shown to users.
 */
object ErrorMessages {

    private const val TAG = "Athlink"

    fun from(e: Throwable, fallback: String, context: String = ""): String {
        Log.w(TAG, "Failure${if (context.isNotEmpty()) " ($context)" else ""}: ${e.javaClass.simpleName}: ${e.message}", e)
        return when (e) {
            is FirebaseNetworkException -> "No internet connection. Check your connection and try again."
            is FirebaseFirestoreException -> when (e.code) {
                FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                    "You don't have permission to do that. If your verification is under review or approved, those details are locked."
                FirebaseFirestoreException.Code.UNAVAILABLE, FirebaseFirestoreException.Code.DEADLINE_EXCEEDED ->
                    "Couldn't reach the server. Please try again."
                FirebaseFirestoreException.Code.UNAUTHENTICATED -> "Your session has expired. Please log in again."
                FirebaseFirestoreException.Code.NOT_FOUND -> "That record no longer exists. Pull to refresh."
                else -> fallback
            }
            is StorageException -> when {
                e.httpResultCode == 402 || e.errorCode == StorageException.ERROR_BUCKET_NOT_FOUND ||
                    e.errorCode == StorageException.ERROR_PROJECT_NOT_FOUND ->
                    "Uploads are unavailable right now: file storage isn't enabled for this Firebase project."
                e.errorCode == StorageException.ERROR_NOT_AUTHORIZED ->
                    "Upload not allowed. Files can't be changed while verification is under review, and must be a PDF, JPG or PNG under 5 MB."
                e.errorCode == StorageException.ERROR_NOT_AUTHENTICATED -> "Your session has expired. Please log in again."
                e.errorCode == StorageException.ERROR_QUOTA_EXCEEDED -> "Storage quota exceeded. Please try again later."
                e.errorCode == StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> "The upload timed out. Check your connection and try again."
                e.errorCode == StorageException.ERROR_CANCELED -> "Upload cancelled."
                else -> "Upload failed. Please try again."
            }
            is FirebaseAuthException -> "Your session has expired. Please log in again."
            else -> fallback
        }
    }
}
