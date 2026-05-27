package com.megalitr.zebraget.data.network

import com.megalitr.zebraget.data.model.Product
import com.megalitr.zebraget.data.model.ProductGroup
import com.megalitr.zebraget.data.model.LoginRequest
import com.megalitr.zebraget.data.model.LoginResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body

interface ApiService {
    @GET("products")
    suspend fun getProducts(): List<Product>

    @GET("groups")
    suspend fun getGroups(): List<ProductGroup>

    @POST("api/client/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @POST("api/client/logout")
    suspend fun logout()
}
