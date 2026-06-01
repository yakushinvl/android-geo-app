package com.yaku.geo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.yaku.geo.data.AppDatabase
import com.yaku.geo.data.VisitedPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class LocationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var locationManager: LocationManager
    private lateinit var database: AppDatabase
    
    private var currentInterval = 10000L // Start with 10s
    private val CHANNEL_ID = "location_service_channel"
    private val NOTIFICATION_ID = 1

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            saveLocation(location)
            adaptFrequency(location.speed)
        }
    }

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        database = AppDatabase.getDatabase(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())
        startLocationUpdates(currentInterval)
        return START_STICKY
    }

    private fun startLocationUpdates(interval: Long) {
        try {
            locationManager.removeUpdates(locationListener)
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                interval,
                0f,
                locationListener
            )
        } catch (_: SecurityException) {
            // Handle appropriately
        }
    }

    private fun adaptFrequency(speed: Float) {
        // Speed is in m/s. 
        // 0 m/s -> 10s interval
        // 10 m/s (~36 km/h) -> 1s interval
        val newInterval = if (speed < 1f) {
            10000L
        } else if (speed < 5f) {
            5000L
        } else {
            1000L
        }

        if (newInterval != currentInterval) {
            currentInterval = newInterval
            startLocationUpdates(currentInterval)
        }
    }

    private fun saveLocation(location: Location) {
        serviceScope.launch {
            database.visitedPointDao().insert(
                VisitedPoint(
                    latitude = location.latitude,
                    longitude = location.longitude
                )
            )
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tracking movement",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Exploration active")
            .setContentText("Recording your path...")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationManager.removeUpdates(locationListener)
    }
}
