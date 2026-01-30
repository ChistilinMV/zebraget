package com.example.zebraget.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class Product(
    val id: Int,
    val name: String,
    val imageUrl: String,
    val barcodeValue: String,
    val barcodeFormat: String? = null // Defaults to EAN_13 logic on client if null
)
