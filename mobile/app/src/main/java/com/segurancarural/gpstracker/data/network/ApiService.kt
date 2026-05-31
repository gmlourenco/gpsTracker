package com.segurancarural.gpstracker.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApiService(@PublishedApi internal val client: HttpClient = ApiClient.httpClient) {

    suspend inline fun <reified T> get(url: String): ApiResult<T> = withContext(Dispatchers.IO) {
        execute { client.get(url) }
    }

    suspend inline fun <reified T> post(url: String, body: String): ApiResult<T> = withContext(Dispatchers.IO) {
        execute {
            client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    suspend inline fun <reified T> patch(url: String, body: String): ApiResult<T> = withContext(Dispatchers.IO) {
        execute {
            client.patch(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    /**
     * Post raw string body and return only success/failure (no deserialized body needed).
     */
    suspend fun postRaw(url: String, body: String): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val responseBody = response.bodyAsText()
            when (response.status) {
                HttpStatusCode.OK -> ApiResult.Success(responseBody)
                HttpStatusCode.Unauthorized -> ApiResult.Unauthorized()
                else -> ApiResult.HttpError(response.status.value, responseBody)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    /**
     * Patch raw string body and return only success/failure.
     */
    suspend fun patchRaw(url: String, body: String): ApiResult<String> = withContext(Dispatchers.IO) {
        try {
            val response = client.patch(url) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            val responseBody = response.bodyAsText()
            when (response.status) {
                HttpStatusCode.OK -> ApiResult.Success(responseBody)
                HttpStatusCode.Unauthorized -> ApiResult.Unauthorized()
                else -> ApiResult.HttpError(response.status.value, responseBody)
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }

    @PublishedApi
    internal suspend inline fun <reified T> execute(
        crossinline block: suspend () -> io.ktor.client.statement.HttpResponse
    ): ApiResult<T> {
        return try {
            val response = block()
            when (response.status) {
                HttpStatusCode.OK -> ApiResult.Success(response.body<T>())
                HttpStatusCode.Unauthorized -> ApiResult.Unauthorized()
                else -> {
                    val errorBody = response.bodyAsText()
                    ApiResult.HttpError(response.status.value, errorBody)
                }
            }
        } catch (e: Exception) {
            ApiResult.NetworkError(e)
        }
    }
}
