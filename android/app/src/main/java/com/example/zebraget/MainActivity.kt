package com.example.zebraget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.*
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.example.zebraget.data.network.ApiService
import com.example.zebraget.domain.ProductRepository
import com.example.zebraget.ui.BarcodeScreen
import com.example.zebraget.ui.CatalogScreen
import com.example.zebraget.ui.ZebragetViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = getSharedPreferences("zebraget_prefs", MODE_PRIVATE)
        val defaultUrl = "http://10.0.2.2:3000/"
        val savedUrl = prefs.getString("server_url", defaultUrl) ?: defaultUrl
        val savedIsDark = prefs.getBoolean("is_dark_theme", false)

        // Manual DI for simplicity
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            
        val repository = ProductRepository(applicationContext, moshi)
        
        fun updateNetwork(url: String) {
            try {
                val cleanUrl = if (url.endsWith("/")) url else "$url/"
                val retrofit = Retrofit.Builder()
                    .baseUrl(cleanUrl)
                    .addConverterFactory(MoshiConverterFactory.create(moshi))
                    .build()
                
                val apiService = retrofit.create(ApiService::class.java)
                repository.setApiService(apiService)
                
                prefs.edit().putString("server_url", cleanUrl).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        updateNetwork(savedUrl)
        
        val viewModel = ZebragetViewModel(repository) // Sharing same VM instance (simplified)

        setContent {
            val navController = rememberNavController()
            var currentUrl by remember { mutableStateOf(savedUrl) }
            var isDarkTheme by remember { mutableStateOf(savedIsDark) }

            androidx.compose.material3.MaterialTheme(
                colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()
            ) {
                NavHost(navController = navController, startDestination = "catalog") {
                    composable("catalog") {
                        CatalogScreen(
                            viewModel = viewModel,
                            currentUrl = currentUrl,
                            isDarkTheme = isDarkTheme,
                            onThemeChange = { newTheme ->
                                isDarkTheme = newTheme
                                prefs.edit().putBoolean("is_dark_theme", newTheme).apply()
                            },
                            onUpdateUrl = { newUrl ->
                                updateNetwork(newUrl)
                                currentUrl = newUrl
                                viewModel.loadProducts()
                            },
                            onProductClick = { product ->
                                navController.navigate("barcode/${product.id}")
                            }
                        )
                    }
                    composable("barcode/{productId}") { backStackEntry ->
                        val productId = backStackEntry.arguments?.getString("productId")
                        BarcodeScreen(
                            productId = productId,
                            viewModel = viewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
