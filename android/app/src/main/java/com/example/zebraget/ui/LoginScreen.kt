package com.example.zebraget.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.zebraget.data.model.LoginRequest
import com.example.zebraget.domain.ProductRepository
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException

class LoginViewModel(private val repository: ProductRepository, private val deviceId: String, private val onLoginSuccess: (String) -> Unit) : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isLoading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun login() {
        if (username.isBlank() || password.isBlank()) {
            error = "Введите логин и пароль"
            return
        }

        viewModelScope.launch {
            isLoading = true
            error = null
            try {
                val response = repository.getApiService()?.login(LoginRequest(username, password, deviceId))
                if (response != null) {
                    onLoginSuccess(response.token)
                } else {
                    error = "Внутренняя ошибка"
                }
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    error = "Неверный логин или пароль"
                } else if (e.code() == 403) {
                    error = "Превышен лимит устройств"
                } else {
                    error = "Ошибка сервера: ${e.code()}"
                }
            } catch (e: ConnectException) {
                error = "Нет связи с сервером"
            } catch (e: Exception) {
                e.printStackTrace()
                error = "Неизвестная ошибка: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: LoginViewModel,
    onOpenSettings: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Вход в ZebraGet") },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Text("⚙", style = MaterialTheme.typography.titleLarge)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding() // Динамический отступ для клавиатуры
                .verticalScroll(rememberScrollState()) // Прокрутка при нехватке места
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Этот Spacer будет сжиматься при появлении клавиатуры
            Spacer(modifier = Modifier.weight(1f))

            Text("Добро пожаловать", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = viewModel.username,
                onValueChange = { viewModel.username = it; viewModel.error = null },
                label = { Text("Логин") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it; viewModel.error = null },
                label = { Text("Пароль") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            if (viewModel.error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = viewModel.error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { viewModel.login() },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                enabled = !viewModel.isLoading
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Войти")
                }
            }

            // Больший вес снизу гарантирует, что форма будет стремиться вверх при поджатии
            Spacer(modifier = Modifier.weight(2f))
        }
    }
}
