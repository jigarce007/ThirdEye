package org.linphone.model

data class AuthResponse(
    val idToken: String,
    val uid: String,
    val email: String
)
