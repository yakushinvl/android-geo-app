package com.yaku.geo

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yaku.geo.data.AppDatabase
import com.yaku.geo.data.AuthRepository
import com.yaku.geo.data.VisitedPoint
import com.yaku.geo.data.VisitedPointDao
import com.yaku.geo.network.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint
import java.text.SimpleDateFormat
import java.util.*

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: VisitedPointDao = AppDatabase.getDatabase(application).visitedPointDao()
    private val authRepo = AuthRepository(application)
    private val apiClient = ApiClient()

    val visitedPoints: StateFlow<List<VisitedPoint>> = dao.getAllVisitedPoints()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val nickname: StateFlow<String?> = authRepo.nickname
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    val authToken: StateFlow<String?> = authRepo.authToken
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    
    val lastSync: StateFlow<Long?> = authRepo.lastSync
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    var syncError by mutableStateOf<String?>(null)
        private set

    private val _logs = mutableStateListOf<String>()
    val logs: List<String> = _logs

    var serverStatus by mutableStateOf("Неизвестно")
        private set

    fun addLog(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.add(0, "[$time] $message")
        if (_logs.size > 100) _logs.removeAt(_logs.size - 1)
    }

    fun checkConnection() {
        viewModelScope.launch {
            try {
                addLog("СЕТЬ: Check...")
                val response = apiClient.client.get("/")
                serverStatus = "Online (${response.status})"
                addLog("СЕТЬ: Ответ сервера: ${response.status}")
            } catch (e: Exception) {
                serverStatus = "Error: ${e.message}"
                addLog("СЕТЬ: Ошибка подключения: ${e.message}")
            }
        }
    }

    fun addPoint(geoPoint: GeoPoint) {
        viewModelScope.launch {
            dao.insert(VisitedPoint(latitude = geoPoint.latitude, longitude = geoPoint.longitude))
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            dao.deleteAll()
            addLog("ЛОКАЛЬНО: История очищена")
        }
    }

    fun register(login: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                addLog("ВХОД: Регистрация $login")
                val response = apiClient.client.post("register") {
                    contentType(ContentType.Application.Json)
                    setBody(AuthRequest(login, pass))
                }
                if (response.status == HttpStatusCode.Created) {
                    val body = response.body<AuthResponse>()
                    authRepo.saveAuth(body.token, body.login)
                    addLog("ВХОД: Успешно зарегистрирован")
                    onSuccess()
                } else {
                    val text = response.bodyAsText()
                    syncError = "Ошибка регистрации: ${response.status}"
                    addLog("ВХОД: Ошибка: $text")
                }
            } catch (e: Exception) {
                syncError = "Сервер не отвечает"
                addLog("ВХОД: Ошибка сети: ${e.message}")
            }
        }
    }

    fun login(login: String, pass: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            try {
                addLog("ВХОД: Авторизация $login")
                val response = apiClient.client.post("login") {
                    contentType(ContentType.Application.Json)
                    setBody(AuthRequest(login, pass))
                }
                if (response.status == HttpStatusCode.OK) {
                    val body = response.body<AuthResponse>()
                    authRepo.saveAuth(body.token, body.login)
                    addLog("ВХОД: Успешный вход")
                    onSuccess()
                } else {
                    syncError = "Неверный логин или пароль"
                    addLog("ВХОД: Ошибка авторизации: ${response.status}")
                }
            } catch (e: Exception) {
                syncError = "Сервер не отвечает"
                addLog("ВХОД: Ошибка сети: ${e.message}")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            addLog("ВХОД: Выход из аккаунта")
            authRepo.clearAuth()
        }
    }

    fun changePassword(newPass: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                addLog("БЕЗОПАСНОСТЬ: Смена пароля...")
                val token = authRepo.authToken.first()
                val response = apiClient.client.post("change-password") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(PasswordChangeRequest(newPass))
                }
                val success = response.status == HttpStatusCode.OK
                addLog("БЕЗОПАСНОСТЬ: Результат: ${response.status}")
                onResult(success)
            } catch (e: Exception) {
                addLog("БЕЗОПАСНОСТЬ: Ошибка: ${e.message}")
                onResult(false)
            }
        }
    }

    fun sync() {
        viewModelScope.launch {
            try {
                syncError = null
                val token = authRepo.authToken.first()
                if (token.isNullOrBlank()) {
                    addLog("СИНХР: Ошибка - Нет токена")
                    return@launch
                }

                val localPoints = visitedPoints.value.map { SyncPoint(it.latitude, it.longitude, it.timestamp) }
                addLog("СИНХР: Начало. Отправка ${localPoints.size} точек.")
                
                val response = apiClient.client.post("sync") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(SyncRequest(localPoints))
                }
                
                addLog("СИНХР: Статус ответа: ${response.status}")
                
                if (response.status == HttpStatusCode.OK) {
                    val serverPoints = response.body<SyncResponse>().points
                    addLog("СИНХР: Получено ${serverPoints.size} точек с сервера")
                    var added = 0
                    for (sp in serverPoints) {
                        val exists = visitedPoints.value.any { 
                            Math.abs(it.latitude - sp.latitude) < 0.000001 && 
                            Math.abs(it.longitude - sp.longitude) < 0.000001 
                        }
                        if (!exists) {
                            dao.insert(VisitedPoint(latitude = sp.latitude, longitude = sp.longitude, timestamp = sp.timestamp))
                            added++
                        }
                    }
                    authRepo.updateSyncTimestamp()
                    addLog("СИНХР: Успешно. Добавлено $added новых точек.")
                } else {
                    val detail = response.bodyAsText()
                    syncError = "Ошибка синхронизации: ${response.status}"
                    addLog("СИНХР: Ошибка: $detail")
                }
            } catch (e: Exception) {
                syncError = "Сервер не отвечает"
                addLog("СИНХР: Ошибка сети: ${e.message}")
            }
        }
    }
}
