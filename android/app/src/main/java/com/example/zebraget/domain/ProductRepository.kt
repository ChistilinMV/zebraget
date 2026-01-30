package com.example.zebraget.domain

import android.content.Context
import com.example.zebraget.data.model.Product
import com.example.zebraget.data.network.ApiService
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class ProductRepository(
    private val context: Context,
    private val moshi: Moshi
) {
    private var apiService: ApiService? = null

    fun setApiService(service: ApiService) {
        this.apiService = service
    }

    suspend fun fetchFromNetwork(): List<Product> {
        return withContext(Dispatchers.IO) {
            if (apiService == null) throw IOException("Server not configured")
            val products = apiService!!.getProducts()
            saveToCache(products)
            products
        }
    }

    suspend fun getCachedOrAssets(): List<Product> {
        return withContext(Dispatchers.IO) {
            val cached = loadFromCache()
            if (cached.isNotEmpty()) cached else loadFromAssets()
        }
    }

    private fun saveToCache(products: List<Product>) {
        try {
            val file = File(context.filesDir, "products_cache.json")
            val type = Types.newParameterizedType(List::class.java, Product::class.java)
            val adapter: JsonAdapter<List<Product>> = moshi.adapter(type)
            val json = adapter.toJson(products)
            file.writeText(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromCache(): List<Product> {
        return try {
            val file = File(context.filesDir, "products_cache.json")
            if (!file.exists()) return emptyList()
            val json = file.readText()
            val type = Types.newParameterizedType(List::class.java, Product::class.java)
            val adapter: JsonAdapter<List<Product>> = moshi.adapter(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun loadFromAssets(): List<Product> {
        return try {
            val json = context.assets.open("products.json").bufferedReader().use { it.readText() }
            val type = Types.newParameterizedType(List::class.java, Product::class.java)
            val adapter: JsonAdapter<List<Product>> = moshi.adapter(type)
            adapter.fromJson(json) ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
