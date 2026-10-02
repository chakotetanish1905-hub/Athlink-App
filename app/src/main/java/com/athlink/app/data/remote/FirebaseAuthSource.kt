package com.athlink.app.data.remote

import com.athlink.app.data.model.User
import com.athlink.app.data.model.UserRole
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthSource @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {

    val currentUser get() = auth.currentUser

    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID null")
            val userDoc = firestore.collection("users").document(uid).get().await()
            val user = userDoc.toObject(User::class.java) ?: throw Exception("User not found")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        role: UserRole
    ): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            val uid = result.user?.uid ?: throw Exception("UID null")
            val user = User(
                uid = uid,
                name = name,
                email = email,
                role = role
            )
            firestore.collection("users").document(uid).set(user).await()
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() = auth.signOut()

    suspend fun getCurrentUserData(): Result<User> {
        return try {
            val uid = auth.currentUser?.uid ?: throw Exception("Not logged in")
            val doc = firestore.collection("users").document(uid).get().await()
            val user = doc.toObject(User::class.java) ?: throw Exception("User data not found")
            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
