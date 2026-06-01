package com.yaku.geo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.lifecycle.viewmodel.compose.viewModel
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.content.Context

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import kotlinx.coroutines.delay

@Composable
fun RepeatingFloatingActionButton(
    onClick: () -> Unit,
    onRepeat: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: ComposeColor = FloatingActionButtonDefaults.containerColor,
    contentColor: ComposeColor = contentColorFor(containerColor),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(400) // Initial delay before repeat
            while (isPressed) {
                onRepeat()
                delay(150) // Repeat interval
            }
        }
    }

    FloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        containerColor = containerColor,
        contentColor = contentColor,
        content = content
    )
}

/**
 * Composable that displays an OpenStreetMap using osmdroid.
 */
@Composable
fun OsmMapView(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: LocationViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val visitedPoints by viewModel.visitedPoints.collectAsState()
    var showDebugMenu by remember { mutableStateOf(false) }

    // Initialize osmdroid configuration
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
            
            // Limit zoom and repetition
            minZoomLevel = 6.0
            maxZoomLevel = 20.0
            setHorizontalMapRepetitionEnabled(false)
            setVerticalMapRepetitionEnabled(false)
            
            // Limit scrollable area to world bounds
            setScrollableAreaLimitDouble(BoundingBox(85.0, 180.0, -85.0, -180.0))
            
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(0.0, 0.0))
            
            // Ensure map fills space under status bar correctly
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

    // Fog of War Overlay
    val fogOverlay = remember {
        FogOfWarOverlay(visitedPoints)
    }

    // Update fog when points change
    LaunchedEffect(visitedPoints) {
        fogOverlay.setPoints(visitedPoints)
        mapView.invalidate()
    }

    // Add overlays - Order: Fog first, then Location marker on top
    LaunchedEffect(mapView) {
        if (!mapView.overlays.contains(fogOverlay)) {
            mapView.overlays.add(fogOverlay)
        }
        if (!mapView.overlays.contains(locationOverlay)) {
            mapView.overlays.add(locationOverlay)
        }
        mapView.invalidate()
    }

    // Track location for immediate fog clearing in the UI
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
            // Get initial location
            val initial = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            initial?.let {
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                fogOverlay.setCurrentLocation(geoPoint)
                mapView.postInvalidate()
            }

            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L, 
                0f,
                locationListener
            )
        } catch (_: SecurityException) {
            // Permission should be handled by MainActivity
        }

        onDispose {
            locationManager.removeUpdates(locationListener)
        }
    }

    // Lifecycle handling
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
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Control buttons column
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
                    containerColor = ComposeColor.Red,
                    contentColor = ComposeColor.White
                ) {
                    Icon(imageVector = Icons.Default.BugReport, contentDescription = "Открыть дебаг меню")
                }
            }

            RepeatingFloatingActionButton(
                onClick = { mapView.controller.zoomIn() },
                onRepeat = { mapView.controller.zoomIn() },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Приблизить")
            }

            RepeatingFloatingActionButton(
                onClick = { mapView.controller.zoomOut() },
                onRepeat = { mapView.controller.zoomOut() },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(imageVector = Icons.Default.Remove, contentDescription = "Отдалить")
            }

            FloatingActionButton(
                onClick = {
                    locationOverlay.enableFollowLocation()
                    val location = locationOverlay.myLocation
                    if (location != null) {
                        // Animate to location and zoom in to 18.0
                        mapView.controller.animateTo(location, 18.0, 1000L)
                    }
                }
            ) {
                Icon(imageVector = Icons.Default.MyLocation, contentDescription = "Моя геолокация")
            }
        }


        // Full-screen Debug Menu
        if (showDebugMenu) {
            DebugMenu(
                onDismiss = { showDebugMenu = false },
                onResetHistory = {
                    viewModel.clearHistory()
                    showDebugMenu = false
                }
            )
        }
    }
}

@Composable
fun DebugMenu(
    onDismiss: () -> Unit,
    onResetHistory: () -> Unit
) {
    BackHandler { onDismiss() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Дебаг меню",
                    style = MaterialTheme.typography.headlineMedium
                )
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Закрыть")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onResetHistory,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ComposeColor.Red)
            ) {
                Text("Сбросить исорию геолокаций")
            }
        }
    }
}
