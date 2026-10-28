package com.classpulse.app

import android.content.Context
import coil.ImageLoader
import com.classpulse.app.data.ClassroomRepository
import com.classpulse.app.data.SessionManager
import com.classpulse.app.data.api.ApiProvider
import com.google.gson.GsonBuilder
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val gson = GsonBuilder().create()
    val sessionManager = SessionManager(appContext, gson)
    val apiProvider = ApiProvider(sessionManager, gson)
    val repository = ClassroomRepository(apiProvider, sessionManager, gson)

    val imageLoader: ImageLoader = ImageLoader.Builder(appContext)
        .okHttpClient(
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val builder = chain.request().newBuilder()
                    sessionManager.token?.let { builder.header("Authorization", "Bearer $it") }
                    chain.proceed(builder.build())
                }
                .build(),
        )
        .crossfade(true)
        .build()

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCache() {
        imageLoader.memoryCache?.clear()
        imageLoader.diskCache?.clear()
    }
}
