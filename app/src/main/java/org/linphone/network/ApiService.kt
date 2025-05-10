package org.linphone.network

import org.linphone.model.AuthResponse
import org.linphone.model.UserDetailsResponse
import retrofit2.http.POST
import retrofit2.http.Query

interface ApiService {

    @POST("auth")
    suspend fun login(
        @Query("email") email: String,
        @Query("password") password: String
    ): AuthResponse

    @POST("user-details")
    suspend fun getUserDetails(
        @Query("uid") uid: String
    ): UserDetailsResponse
}
