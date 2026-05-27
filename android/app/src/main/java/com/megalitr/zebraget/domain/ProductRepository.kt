package com.megalitr.zebraget.domain

import android.content.Context
import com.megalitr.zebraget.data.model.Product
import com.megalitr.zebraget.data.model.ProductGroup
import com.megalitr.zebraget.data.network.ApiService
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
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

    fun getApiService(): ApiService? = apiService

    suspend fun fetchFromNetwork(): Pair<List<Product>, List<ProductGroup>> {
        return withContext(Dispatchers.IO) {
            if (apiService == null) throw IOException("Server not configured")
            
            val productsDeferred = async { apiService!!.getProducts() }
            val groupsDeferred = async { 
                try { apiService!!.getGroups() } catch (e: Exception) { emptyList() }
            }
            
            val products = productsDeferred.await()
            val groups = groupsDeferred.await()
            
            saveToCache(products, groups)
            Pair(products, groups)
        }
    }

    suspend fun getCachedOrAssets(): Pair<List<Product>, List<ProductGroup>> {
        return withContext(Dispatchers.IO) {
            val (cachedProducts, cachedGroups) = loadFromCache()
            if (cachedProducts.isNotEmpty()) {
                Pair(cachedProducts, cachedGroups)
            } else {
                loadFromAssets()
            }
        }
    }

    private fun saveToCache(products: List<Product>, groups: List<ProductGroup>) {
        try {
            val fileProducts = File(context.filesDir, "products_cache.json")
            val typeProducts = Types.newParameterizedType(List::class.java, Product::class.java)
            val adapterProducts: JsonAdapter<List<Product>> = moshi.adapter(typeProducts)
            fileProducts.writeText(adapterProducts.toJson(products))
            
            val fileGroups = File(context.filesDir, "groups_cache.json")
            val typeGroups = Types.newParameterizedType(List::class.java, ProductGroup::class.java)
            val adapterGroups: JsonAdapter<List<ProductGroup>> = moshi.adapter(typeGroups)
            fileGroups.writeText(adapterGroups.toJson(groups))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadFromCache(): Pair<List<Product>, List<ProductGroup>> {
        val products = try {
            val file = File(context.filesDir, "products_cache.json")
            if (!file.exists()) emptyList()
            else {
                val json = file.readText()
                val type = Types.newParameterizedType(List::class.java, Product::class.java)
                val adapter: JsonAdapter<List<Product>> = moshi.adapter(type)
                adapter.fromJson(json) ?: emptyList()
            }
        } catch (e: Exception) { emptyList() }
        
        val groups = try {
            val file = File(context.filesDir, "groups_cache.json")
            if (!file.exists()) emptyList()
            else {
                val json = file.readText()
                val type = Types.newParameterizedType(List::class.java, ProductGroup::class.java)
                val adapter: JsonAdapter<List<ProductGroup>> = moshi.adapter(type)
                adapter.fromJson(json) ?: emptyList()
            }
        } catch (e: Exception) { emptyList() }
        
        return Pair(products, groups)
    }

    private fun loadFromAssets(): Pair<List<Product>, List<ProductGroup>> {
        return try {
            val json = context.assets.open("db.json").bufferedReader().use { it.readText() }
            val adapter = moshi.adapter(AssetData::class.java)
            val data = adapter.fromJson(json)
            Pair(data?.products ?: emptyList(), data?.groups ?: emptyList())
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), emptyList())
        }
    }

    @JsonClass(generateAdapter = true)
    private data class AssetData(
        val products: List<Product>,
        val groups: List<ProductGroup>
    )
}
