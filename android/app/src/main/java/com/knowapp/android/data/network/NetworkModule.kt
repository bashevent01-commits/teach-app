package com.knowapp.android.data.network

import com.knowapp.android.BuildConfig
import com.knowapp.android.data.SessionStore
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Attaches the stored bearer token to every outgoing request, if present. */
private class AuthInterceptor(private val sessionStore: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionStore.currentToken()
        val request = chain.request().let { original ->
            if (token != null) {
                original.newBuilder().addHeader("Authorization", "Bearer $token").build()
            } else {
                original
            }
        }
        return chain.proceed(request)
    }
}

object NetworkModule {
    private val json = Json { ignoreUnknownKeys = true }

    fun buildApiService(sessionStore: SessionStore): ApiService {
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }

        // Render's free tier cold-starts after inactivity — a first request
        // can take 30-50s to wake the backend, so timeouts are generous
        // rather than the OkHttp default of 10s.
        val client = OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .addInterceptor(AuthInterceptor(sessionStore))
            .addInterceptor(logging)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        return retrofit.create(ApiService::class.java)
    }
}
