package com.example.zebraget.data.model

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val username: String,
    val password: String,
    val deviceId: String
)

@JsonClass(generateAdapter = true)
data class LoginResponse(
    val token: String
)
