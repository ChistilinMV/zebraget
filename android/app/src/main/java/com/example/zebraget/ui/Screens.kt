package com.example.zebraget.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.background
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.example.zebraget.data.model.Product
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    viewModel: ZebragetViewModel,
    currentUrl: String,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onUpdateUrl: (String) -> Unit,
    onProductClick: (Product) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredProducts by viewModel.filteredProducts.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusManager = LocalFocusManager.current
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsDialog(
            initialUrl = currentUrl,
            isDarkTheme = isDarkTheme,
            onThemeChange = onThemeChange,
            onDismiss = { showSettings = false },
            onConfirm = { newUrl ->
                onUpdateUrl(newUrl)
                showSettings = false
            }
        )
    }

    if (showInfo) {
        InfoDialog(onDismiss = { showInfo = false })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ZebraGet Catalog") },
                actions = {
                    IconButton(onClick = { viewModel.loadProducts() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Search Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                placeholder = { Text("Поиск по названию...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )

            // Content
            when (val state = uiState) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Ошибка: ${state.message}", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadProducts() }) {
                            Text("Повторить")
                        }
                    }
                }
                is UiState.Content -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.isOffline) {
                            Text(
                                text = "Нет связи с сервером. Сохранённые данные.",
                                color = MaterialTheme.colorScheme.onError,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.error)
                                    .padding(8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                        LazyColumn {
                            items(filteredProducts) { product ->
                                ProductItem(product, onClick = { onProductClick(product) })
                                Divider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    initialUrl: String,
    isDarkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var url by remember { mutableStateOf(initialUrl) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки сервера") },
        text = {
            Column {
                Text("URL адрес сервера:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                         Icon(Icons.Filled.DarkMode, contentDescription = null)
                         Spacer(modifier = Modifier.width(8.dp))
                         Text("Тёмная тема")
                    }
                    Switch(
                        checked = isDarkTheme,
                        onCheckedChange = onThemeChange
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url) }) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun InfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("О приложении")
        },
        text = {
            Column {
                Text("ZebraGet", style = MaterialTheme.typography.titleLarge)
                Text("Версия: 1.0", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Связь с разработчиком:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Text("Email: max.chistilin@gmail.com")
                Text("Telegram: @megalitr")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("ОК")
            }
        }
    )
}

@Composable
fun ProductItem(product: Product, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            contentScale = ContentScale.Crop,
            error = rememberVectorPainter(Icons.Default.Warning) // Placeholderish
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = product.name, style = MaterialTheme.typography.bodyLarge)
    }
}

// Helper for placeholder
@Composable
fun rememberVectorPainter(image: androidx.compose.ui.graphics.vector.ImageVector) = 
    androidx.compose.ui.graphics.vector.rememberVectorPainter(image)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    productId: String?, // Passed from nav arguments
    viewModel: ZebragetViewModel,
    onBack: () -> Unit
) {
    // Ideally we pass objects, but with nav it's usually ID. 
    // For simplicity, we can find the product in the VM list. Use a 'derived' approach or simple find.
    // NOTE: In a real app we'd fetch strictly by ID, but here we rely on the loaded list.
    
    val uiState by viewModel.uiState.collectAsState()
    val product = (uiState as? UiState.Content)?.products?.find { it.id.toString() == productId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcode") },
                navigationIcon = {
                    // Back logic handled by system back usually if no icon, 
                    // or add an ArrowBack icon button here calling onBack()
                    IconButton(onClick = onBack) {
                         // Default icons might not be imported, let's just use Text "<" or skip nav icon since back press works
                         Text("<", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (product == null) {
                Text("Товар не найден")
            } else {
                ProductBarcodeDetail(product)
            }
        }
    }
}

@Composable
fun ProductBarcodeDetail(product: Product) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(product.name, style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(32.dp))
        
        val format = product.barcodeFormat ?: "EAN_13"
        val isValid = if (format == "EAN_13") validateEan13(product.barcodeValue) else true
        
        if (isValid) {
            val bitmap = generateBarcode(product.barcodeValue, format)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Barcode",
                    modifier = Modifier.size(width = 300.dp, height = 150.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(product.barcodeValue)
            } else {
                Text("Error generating barcode", color = Color.Red)
            }
        } else {
             Text("Invalid EAN-13 Barcode", color = Color.Red)
             Text(product.barcodeValue)
        }
    }
}

fun validateEan13(code: String): Boolean {
    if (code.length != 13 || !code.all { it.isDigit() }) return false
    // detailed checksum
    val sum = code.take(12).mapIndexed { i, c ->
        c.toString().toInt() * (if (i % 2 == 0) 1 else 3)
    }.sum()
    val check = (10 - (sum % 10)) % 10
    return check == code.last().toString().toInt()
}

fun generateBarcode(value: String, format: String): Bitmap? {
    return try {
        val zxingFormat = when(format) {
            "EAN_13" -> BarcodeFormat.EAN_13
            // Add others if needed
            else -> BarcodeFormat.EAN_13 // Fallback per requirement
        }
        val writer = MultiFormatWriter()
        val bitMatrix = writer.encode(value, zxingFormat, 600, 300)
        val w = bitMatrix.width
        val h = bitMatrix.height
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (x in 0 until w) {
            for (y in 0 until h) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
