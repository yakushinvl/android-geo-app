package com.yaku.geo

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RepeatingFloatingActionButton(
    onClick: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: ComposeColor = FloatingActionButtonDefaults.containerColor,
    contentColor: ComposeColor = contentColorFor(containerColor),
    icon: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(400)
            while (isPressed) {
                onRepeat()
                delay(150)
            }
        }
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        containerColor = containerColor,
        contentColor = contentColor,
        content = icon
    )
}

@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: LocationViewModel = viewModel<LocationViewModel>()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val visitedPoints by viewModel.visitedPoints.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    val lastSync by viewModel.lastSync.collectAsState()

    var showDebugMenu by remember { mutableStateOf(false) }
    var showAuthScreen by remember { mutableStateOf(false) }
    var showAccountDetails by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }

    remember {
        Configuration.getInstance().load(context, context.getSharedPreferences("osmdroid", 0))
        Configuration.getInstance().userAgentValue = context.packageName
        true
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            minZoomLevel = 6.0
            maxZoomLevel = 20.0
            setHorizontalMapRepetitionEnabled(false)
            setVerticalMapRepetitionEnabled(false)
            setScrollableAreaLimitDouble(BoundingBox(85.0, 180.0, -85.0, -180.0))
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(0.0, 0.0))
            setFitsSystemWindows(false)
        }
    }

    val locationProvider = remember { GpsMyLocationProvider(context) }
    val locationOverlay = remember {
        MyLocationNewOverlay(locationProvider, mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
    }

    val fogOverlay = remember { FogOfWarOverlay(visitedPoints) }

    LaunchedEffect(visitedPoints) {
        fogOverlay.setPoints(visitedPoints)
        mapView.invalidate()
    }

    LaunchedEffect(mapView) {
        if (!mapView.overlays.contains(fogOverlay)) mapView.overlays.add(fogOverlay)
        if (!mapView.overlays.contains(locationOverlay)) mapView.overlays.add(locationOverlay)
        mapView.invalidate()
    }

    DisposableEffect(context) {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                fogOverlay.setCurrentLocation(geoPoint)
                mapView.postInvalidate()
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            val initial = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            initial?.let {
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                fogOverlay.setCurrentLocation(geoPoint)
                mapView.postInvalidate()
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
        } catch (_: SecurityException) {}
        onDispose { locationManager.removeUpdates(locationListener) }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    locationOverlay.enableMyLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    locationOverlay.disableMyLocation()
                    mapView.onPause()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clickable {
                    if (nickname == null) showAuthScreen = true else showAccountDetails = true
                },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = nickname ?: "Войти в аккаунт",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(contentPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (BuildConfig.DEBUG) {
                FloatingActionButton(
                    onClick = { showDebugMenu = true },
                    modifier = Modifier.padding(bottom = 8.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ) {
                    Icon(Icons.Default.BugReport, "ОТЛАДКА")
                }
            }

            RepeatingFloatingActionButton(
                onClick = { mapView.controller.zoomIn() },
                onRepeat = { mapView.controller.zoomIn() },
                modifier = Modifier.padding(bottom = 8.dp),
                icon = { Icon(Icons.Default.Add, "+") }
            )

            RepeatingFloatingActionButton(
                onClick = { mapView.controller.zoomOut() },
                onRepeat = { mapView.controller.zoomOut() },
                modifier = Modifier.padding(bottom = 16.dp),
                icon = { Icon(Icons.Default.Remove, "-") }
            )

            FloatingActionButton(
                onClick = {
                    locationOverlay.enableFollowLocation()
                    locationOverlay.myLocation?.let { mapView.controller.animateTo(it, 18.0, 1000L) }
                }
            ) {
                Icon(Icons.Default.MyLocation, "ГЛО")
            }
        }

        if (showDebugMenu) DebugMenu(onDismiss = { showDebugMenu = false }, viewModel = viewModel)
        if (showAuthScreen) AuthScreen(onDismiss = { showAuthScreen = false }, viewModel = viewModel)
        if (showAccountDetails) {
            AccountDetailsScreen(
                nickname = nickname ?: "",
                lastSync = lastSync,
                syncError = viewModel.syncError,
                onDismiss = { showAccountDetails = false },
                onLogout = {
                    viewModel.logout()
                    showAccountDetails = false
                },
                onSync = { viewModel.sync() },
                onChangePassword = { showChangePassword = true }
            )
        }
        if (showChangePassword) {
            ChangePasswordScreen(
                onDismiss = { showChangePassword = false },
                onConfirm = { newPass ->
                    viewModel.changePassword(newPass) { success ->
                        if (success) showChangePassword = false
                    }
                }
            )
        }
    }
}

@Composable
fun AuthScreen(onDismiss: () -> Unit, viewModel: LocationViewModel) {
    var isRegister by remember { mutableStateOf(false) }
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    BackHandler { onDismiss() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.systemBarsPadding().padding(24.dp).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть") }
            }

            Spacer(modifier = Modifier.weight(1f))

            Text(if (isRegister) "Регистрация" else "Вход", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(
                value = login,
                onValueChange = { if (it.length <= 12) login = it },
                label = { Text("Никнейм") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Пароль") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            if (isRegister) {
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Подтвердите пароль") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            viewModel.syncError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp)) }
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = {
                    if (isRegister) {
                        if (password == confirmPassword) viewModel.register(login, password) { onDismiss() }
                    } else {
                        viewModel.login(login, password) { onDismiss() }
                    }
                },
                modifier = Modifier.height(56.dp).fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(if (isRegister) "Зарегистрироваться" else "Войти")
            }
            TextButton(onClick = { isRegister = !isRegister }) {
                Text(if (isRegister) "Уже есть аккаунт? Войти" else "Нет аккаунта? Создать")
            }
            
            Spacer(modifier = Modifier.weight(1.5f))
        }
    }
}

@Composable
fun AccountDetailsScreen(
    nickname: String,
    lastSync: Long?,
    syncError: String?,
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onSync: () -> Unit,
    onChangePassword: () -> Unit
) {
    BackHandler { onDismiss() }
    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.systemBarsPadding().padding(24.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Аккаунт", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть") }
            }
            Spacer(modifier = Modifier.height(40.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(text = "Логин: $nickname", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Пароль: ••••••••", style = MaterialTheme.typography.bodyLarge, color = ComposeColor.Gray)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onChangePassword, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Изменить пароль")
            }
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text("Выйти из аккаунта")
            }
            Spacer(modifier = Modifier.weight(1f))
            val syncText = when {
                syncError != null -> syncError
                lastSync != null -> "Синхронизировано: ${dateFormatter.format(Date(lastSync))}"
                else -> "Ожидание синхронизации..."
            }
            
            Text(
                text = syncText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures(onLongPress = { onSync() }) }
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ChangePasswordScreen(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    BackHandler { onDismiss() }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.systemBarsPadding().padding(24.dp).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть") }
            }
            Text("Смена пароля", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(32.dp))
            OutlinedTextField(value = newPassword, onValueChange = { newPassword = it }, label = { Text("Новый пароль") }, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = { onConfirm(newPassword) }, modifier = Modifier.fillMaxWidth()) { Text("Сохранить") }
        }
    }
}

@Composable
fun DebugMenu(onDismiss: () -> Unit, viewModel: LocationViewModel) {
    BackHandler { onDismiss() }
    val nickname by viewModel.nickname.collectAsState()
    val token by viewModel.authToken.collectAsState()
    val logs = viewModel.logs

    LaunchedEffect(Unit) { viewModel.checkConnection() }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.systemBarsPadding().padding(16.dp).fillMaxSize()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "Дебаг-меню", style = MaterialTheme.typography.headlineMedium)
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Закрыть") }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ПОДКЛЮЧЕНИЕ", style = MaterialTheme.typography.labelLarge)
                        Text("Статус: ${viewModel.serverStatus}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { viewModel.checkConnection() }) {
                        Icon(Icons.Default.Refresh, "Обновить")
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("СЕССИЯ", style = MaterialTheme.typography.labelLarge)
                    Text("Логин: ${nickname ?: "нет авторизации"}", style = MaterialTheme.typography.bodyMedium)
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("JWT: ", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(
                            text = token ?: "нет авторизации",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("КОНСОЛЬ", style = MaterialTheme.typography.labelLarge)
            Surface(modifier = Modifier.weight(1f).fillMaxWidth(), color = ComposeColor(0xFF1E1E1E), shape = RoundedCornerShape(8.dp)) {
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    items(logs.size) { index ->
                        Text(
                            text = logs[index],
                            color = if (logs[index].contains("Ошибка", true) || logs[index].contains("failed", true) || logs[index].contains("Error", true)) ComposeColor.Red else ComposeColor.Green,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { viewModel.clearHistory() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                Text("Очистить историю исследований локально")
            }
        }
    }
}
