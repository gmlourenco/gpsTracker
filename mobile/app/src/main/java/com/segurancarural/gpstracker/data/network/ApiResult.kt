package com.segurancarural.gpstracker.data.network

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class HttpError(val code: Int, val message: String) : ApiResult<Nothing>()
    data class NetworkError(val exception: Throwable) : ApiResult<Nothing>()
    data class Unauthorized(val message: String = "Unauthorized") : ApiResult<Nothing>()
}
