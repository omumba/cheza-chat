package com.chezachat.data.api

import com.chezachat.model.*
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface ChezaApiService {

    // ── Auth ──────────────────────────────────────────────────────────────────

    @POST("auth/login.php")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register.php")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/logout.php")
    suspend fun logout(): Response<ApiResponse<Unit>>

    @POST("auth/refresh.php")
    suspend fun refreshToken(): Response<AuthResponse>

    // ── Conversations ─────────────────────────────────────────────────────────

    @GET("conversations/list.php")
    suspend fun getConversations(): Response<ConversationsResponse>

    @GET("conversations/get.php")
    suspend fun getConversation(
        @Query("id") id: Int
    ): Response<ApiResponse<Conversation>>

    @POST("conversations/create.php")
    suspend fun createDirectConversation(
        @Body body: Map<String, Int>
    ): Response<ApiResponse<Conversation>>

    @POST("conversations/create_group.php")
    suspend fun createGroupConversation(
        @Body body: Map<String, Any>
    ): Response<ApiResponse<Conversation>>

    // ── Messages ──────────────────────────────────────────────────────────────

    @GET("messages/list.php")
    suspend fun getMessages(
        @Query("conversation_id") conversationId: Int,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<MessagesResponse>

    @POST("messages/send.php")
    suspend fun sendMessage(@Body body: SendMessagePayload): Response<ApiResponse<Message>>

    @POST("messages/delete.php")
    suspend fun deleteMessage(@Body body: Map<String, Int>): Response<ApiResponse<Unit>>

    @POST("messages/react.php")
    suspend fun reactToMessage(@Body body: Map<String, Any>): Response<ApiResponse<Unit>>

    // ── Media Upload ──────────────────────────────────────────────────────────

    @Multipart
    @POST("media/upload.php")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("type") type: RequestBody,
        @Part("conversation_id") conversationId: RequestBody
    ): Response<ApiResponse<Map<String, String>>>

    // ── Users ─────────────────────────────────────────────────────────────────

    @GET("users/search.php")
    suspend fun searchUsers(
        @Query("q") query: String
    ): Response<UsersResponse>

    @GET("users/profile.php")
    suspend fun getProfile(): Response<ApiResponse<User>>

    @POST("users/update_profile.php")
    suspend fun updateProfile(@Body body: Map<String, String>): Response<ApiResponse<User>>

    @Multipart
    @POST("users/update_avatar.php")
    suspend fun updateAvatar(
        @Part image: MultipartBody.Part
    ): Response<ApiResponse<Map<String, String>>>

    @POST("messages/mark_read.php")
    suspend fun markMessagesRead(@Body body: Map<String, Int>): Response<ApiResponse<Unit>>

    @POST("auth/change_password.php")
    suspend fun changePassword(@Body body: Map<String, String>): Response<ApiResponse<Unit>>

    @POST("users/update_fcm.php")
    suspend fun updateFcmToken(@Body body: Map<String, String>): Response<ApiResponse<Unit>>

    // ── Friend Requests ───────────────────────────────────────────────────────

    @POST("friends/send_request.php")
    suspend fun sendFriendRequest(@Body body: Map<String, Int>): Response<ApiResponse<Unit>>

    @POST("friends/respond.php")
    suspend fun respondFriendRequest(@Body body: RespondFriendRequest): Response<ApiResponse<Unit>>

    @GET("friends/requests.php")
    suspend fun getFriendRequests(): Response<FriendRequestsResponse>

    @GET("friends/list.php")
    suspend fun getFriends(): Response<FriendsResponse>


    @GET("friends/sent_requests.php")
    suspend fun getSentFriendRequests(): Response<FriendRequestsResponse>

    @GET("users/get_profile.php")
    suspend fun getUserProfile(@Query("id") userId: Int): Response<ApiResponse<User>>

    @POST("friends/remove.php")
    suspend fun removeFriend(@Body body: Map<String, Int>): Response<ApiResponse<Unit>>

    // ── Forgot / Reset Password ───────────────────────────────────────────────

    @POST("auth/forgot_password.php")
    suspend fun forgotPassword(@Body body: ForgotPasswordRequest): Response<ApiResponse<Unit>>

    @POST("auth/verify_otp.php")
    suspend fun verifyOtp(@Body body: Map<String, String>): Response<ApiResponse<Unit>>

    @POST("auth/reset_password.php")
    suspend fun resetPassword(@Body body: ResetPasswordRequest): Response<ApiResponse<Unit>>
}
