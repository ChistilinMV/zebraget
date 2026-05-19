package com.example.zebraget.data.network

import com.example.zebraget.data.model.Product
import com.example.zebraget.data.model.ProductGroup
import retrofit2.http.GET

interface ApiService {
    @GET("products")
    suspend fun getProducts(): List<Product>

    @GET("groups")
    suspend fun getGroups(): List<ProductGroup>
}
