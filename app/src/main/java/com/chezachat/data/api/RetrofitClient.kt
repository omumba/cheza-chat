package com.chezachat.data.api

import android.content.Intent
import com.chezachat.BuildConfig
import com.chezachat.ui.auth.AuthActivity
import com.chezachat.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    @Volatile private var sessionManager: SessionManager? = null
    @Volatile private var _api: ChezaApiService? = null

    fun init(sm: SessionManager) {
        if (sessionManager !== sm) {
            sessionManager = sm
            _api = null
        }
    }

    val api: ChezaApiService
        get() = _api ?: synchronized(this) { _api ?: buildApi().also { _api = it } }

    private fun buildApi(): ChezaApiService {
        val authInterceptor = Interceptor { chain ->
            val token   = sessionManager?.getToken()
            val request = chain.request().newBuilder().apply {
                if (token != null) header("Authorization", "Bearer $token")
                header("Accept", "application/json")
            }.build()
            val response = chain.proceed(request)

            // On 401, clear session and send user back to login
            if (response.code == 401) {
                sessionManager?.logout()
                sessionManager?.appContext?.let { ctx ->
                    val intent = Intent(ctx, AuthActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    ctx.startActivity(intent)
                }
            }
            response
        }

        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
                    else HttpLoggingInterceptor.Level.NONE
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChezaApiService::class.java)
    }
}
