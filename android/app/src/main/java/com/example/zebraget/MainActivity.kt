package com.example.zebraget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.example.zebraget.ui.LoginScreen
import com.example.zebraget.ui.LoginViewModel
import com.example.zebraget.ui.ZebragetViewModel
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val prefs = getSharedPreferences("zebraget_prefs", MODE_PRIVATE)
        
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }

        val defaultUrl = "http://10.0.2.2:3000/"
        val savedUrl = prefs.getString("server_url", defaultUrl) ?: defaultUrl
        val savedIsDark = prefs.getBoolean("is_dark_theme", false)
        val initialToken = prefs.getString("auth_token", null)

        // Manual DI for simplicity
        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()
            
        val repository = ProductRepository(applicationContext, moshi)
        
        fun updateNetwork(url: String) {
            try {
                val cleanUrl = if (url.endsWith("/")) url else "$url/"
                
                val okHttpClient = OkHttpClient.Builder().addInterceptor { chain ->
                    val original = chain.request()
                    val token = prefs.getString("auth_token", null)
                    val requestBuilder = original.newBuilder()
                    if (token != null) {
                        requestBuilder.header("Authorization", "Bearer $token")
                    }
                    val request = requestBuilder.build()
                    chain.proceed(request)
                }.build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(cleanUrl)
                    .client(okHttpClient)
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
                val startDest = if (prefs.getString("auth_token", null) != null) "catalog" else "login"
                NavHost(navController = navController, startDestination = startDest) {
                    composable("login") {
                        val loginViewModel = remember {
                            LoginViewModel(repository, deviceId) { token ->
                                prefs.edit().putString("auth_token", token).apply()
                                viewModel.loadData() // refresh data with new token
                                navController.navigate("catalog") {
                                    popUpTo("login") { inclusive = true }
                                }
                            }
                        }
                        LoginScreen(
                            viewModel = loginViewModel,
                            onOpenSettings = {
                                // For simplicity, we could just reuse SettingsDialog here if needed,
                                // but let's navigate to a simple url setup if we want, or just leave it.
                                // I'll just pass a dummy or implement a standalone one.
                            }
                        )
                    }
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
                                viewModel.loadData()
                            },
                            onProductClick = { product ->
                                navController.navigate("barcode/${product.id}")
                            },
                            onLogout = {
                                prefs.edit().remove("auth_token").apply()
                                navController.navigate("login") {
                                    popUpTo("catalog") { inclusive = true }
                                }
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
