package com.classpulse.app.data.api

import com.classpulse.app.BuildConfig
import com.classpulse.app.data.SessionManager
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class ApiProvider(
    private val sessionManager: SessionManager,
    private val gson: Gson,
) {
    @Volatile
    private var cachedUrl: String? = null

    @Volatile
    private var cachedApi: ClassroomApi? = null

    val api: ClassroomApi
        get() = synchronized(this) {
            val currentUrl = sessionManager.serverUrl
            if (cachedApi == null || cachedUrl != currentUrl) {
                cachedApi = build(currentUrl)
                cachedUrl = currentUrl
            }
            requireNotNull(cachedApi)
        }

    private fun build(baseUrl: String): ClassroomApi {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                sessionManager.token?.let { builder.header("Authorization", "Bearer $it") }
                chain.proceed(builder.build())
            }
            .apply {
                if (BuildConfig.DEBUG) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            level = HttpLoggingInterceptor.Level.BASIC
                            redactHeader("Authorization")
                        },
                    )
                }
            }
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(ClassroomApi::class.java)
    }
}
