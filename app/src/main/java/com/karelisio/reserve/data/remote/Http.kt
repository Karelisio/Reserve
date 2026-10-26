package com.karelisio.reserve.data.remote

import com.karelisio.reserve.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

class HttpException(val code: Int, message: String) : IOException(message)

object Http {
    /** OSM, Overpass and OSRM usage policies require an identifying User-Agent. */
    val userAgent = "Reserve/${BuildConfig.VERSION_NAME} (Android; com.karelisio.reserve)"

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Volatile
    private var instance: OkHttpClient? = null

    fun client(cacheDir: File? = null): OkHttpClient = instance ?: synchronized(this) {
        instance ?: OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .addInterceptor(Interceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
            })
            .apply { if (cacheDir != null) cache(okhttp3.Cache(File(cacheDir, "http"), 50L * 1024 * 1024)) }
            .build()
            .also { instance = it }
    }
}

suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            cont.resume(response) { _, _, _ -> response.close() }
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}

suspend fun OkHttpClient.getJson(request: Request): JsonElement {
    val response = newCall(request).await()
    return withContext(Dispatchers.IO) {
        response.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw HttpException(it.code, "HTTP ${it.code} ${request.url.host}")
            Http.json.parseToJsonElement(body)
        }
    }
}
