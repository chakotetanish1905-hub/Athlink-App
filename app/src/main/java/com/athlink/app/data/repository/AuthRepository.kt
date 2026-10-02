package com.athlink.app.data.repository

import com.athlink.app.data.model.CoachRegistration
import com.athlink.app.data.model.User
import com.athlink.app.data.model.UserRole
import com.athlink.app.data.remote.FirebaseAuthSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val authSource: FirebaseAuthSource
) {
    val isLoggedIn get() = authSource.currentUser != null

    suspend fun login(email: String, password: String): Result<User> =
        authSource.signIn(email, password)

    suspend fun register(name: String, email: String, password: String, role: UserRole): Result<User> =
        authSource.signUp(name, email, password, role)

    suspend fun registerCoach(registration: CoachRegistration): Result<User> =
        authSource.signUpCoach(registration)

    fun logout() = authSource.signOut()

    suspend fun getCurrentUser(): Result<User> = authSource.getCurrentUserData()
}
