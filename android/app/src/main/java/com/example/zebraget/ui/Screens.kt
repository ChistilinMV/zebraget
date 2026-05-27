package com.example.zebraget.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.background
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign
import coil.compose.AsyncImage
import com.example.zebraget.data.model.Product
import com.example.zebraget.data.model.ProductGroup
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
    onProductClick: (Product) -> Unit,
    onLogout: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedGroupId by viewModel.selectedGroupId.collectAsState()
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

    val contentState = uiState as? UiState.Content
    val currentGroupName = if (contentState != null && selectedGroupId != null) {
        contentState.groups.find { it.id == selectedGroupId }?.name ?: "Группа"
    } else {
        null
    }

    // Handle back press if we are inside a group and not searching
    BackHandler(enabled = selectedGroupId != null && searchQuery.isBlank()) {
        viewModel.selectGroup(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (selectedGroupId != null && searchQuery.isBlank()) currentGroupName ?: "Zebraget" else "ZebraGet")
                },
                navigationIcon = {
                    if (selectedGroupId != null && searchQuery.isBlank()) {
                        IconButton(onClick = { viewModel.selectGroup(null) }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = { showInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = "Info")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Logout")
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
                        Button(onClick = { viewModel.loadData() }) {
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
                        
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.loadData(fromSwipe = true) },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val itemsToShow = getItemsToShow(state, searchQuery, selectedGroupId)
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(itemsToShow) { item ->
                                    when (item) {
                                        is CatalogItem.GroupItem -> {
                                            ProductGroupItem(item.group, onClick = { viewModel.selectGroup(item.group.id) })
                                        }
                                        is CatalogItem.ProductItem -> {
                                            ProductItem(item.product, onClick = { onProductClick(item.product) })
                                            Divider()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

sealed class CatalogItem {
    data class GroupItem(val group: ProductGroup) : CatalogItem()
    data class ProductItem(val product: Product) : CatalogItem()
}

fun getItemsToShow(state: UiState.Content, query: String, groupId: Long?): List<CatalogItem> {
    if (query.isNotBlank()) {
        // Search across all products
        return state.products
            .filter { it.name.contains(query, ignoreCase = true) }
            .map { CatalogItem.ProductItem(it) }
    }
    
    if (groupId == null) {
        // Root view: show groups, then products without groups
        val groups = state.groups.map { CatalogItem.GroupItem(it) }
        val productsNoGroup = state.products
            .filter { it.groupId == null }
            .map { CatalogItem.ProductItem(it) }
        return groups + productsNoGroup
    } else {
        // Group view: show products in this group
        return state.products
            .filter { it.groupId == groupId }
            .map { CatalogItem.ProductItem(it) }
    }
}

@Composable
fun ProductGroupItem(group: ProductGroup, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = group.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop,
                error = rememberVectorPainter(Icons.Default.Folder)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = group.name, 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open group",
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
fun ProductItem(product: Product, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = product.imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop,
            error = rememberVectorPainter(Icons.Default.Warning)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = product.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = product.barcodeValue, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                Text("Версия: 1.3", style = MaterialTheme.typography.bodyMedium)
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

// Helper for placeholder
@Composable
fun rememberVectorPainter(image: androidx.compose.ui.graphics.vector.ImageVector) = 
    androidx.compose.ui.graphics.vector.rememberVectorPainter(image)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScreen(
    productId: String?,
    viewModel: ZebragetViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val product = (uiState as? UiState.Content)?.products?.find { it.id.toString() == productId }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            else -> BarcodeFormat.EAN_13 
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
