package com.yaku.geo

import android.content.Context
import androidx.work.*
import com.yaku.geo.data.AppDatabase
import com.yaku.geo.data.AuthRepository
import com.yaku.geo.network.ApiClient
import com.yaku.geo.network.SyncPoint
import com.yaku.geo.network.SyncRequest
import com.yaku.geo.network.SyncResponse
import com.yaku.geo.data.VisitedPoint
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val authRepo = AuthRepository(applicationContext)
            val database = AppDatabase.getDatabase(applicationContext)
            val dao = database.visitedPointDao()
            
            // Check if logged in
            val token = authRepo.authToken.first()
            if (token.isNullOrBlank()) return Result.success() // Not logged in, skip sync

            val apiClient = ApiClient()
            val localPoints = dao.getAllVisitedPoints().first()
            
            val syncRequest = SyncRequest(localPoints.map { SyncPoint(it.latitude, it.longitude, it.timestamp) })
            
            val response = apiClient.client.post("sync") {
                header(HttpHeaders.Authorization, "Bearer $token")
                setBody(syncRequest)
                contentType(ContentType.Application.Json)
            }
            
            if (response.status == HttpStatusCode.OK) {
                val serverPoints = response.body<SyncResponse>().points
                for (sp in serverPoints) {
                    val exists = localPoints.any { it.latitude == sp.latitude && it.longitude == sp.longitude }
                    if (!exists) {
                        dao.insert(VisitedPoint(latitude = sp.latitude, longitude = sp.longitude, timestamp = sp.timestamp))
                    }
                }
                authRepo.updateSyncTimestamp()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (_: Exception) {
            Result.failure()
        }
    }

    companion object {
        fun scheduleSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "geo_sync_work",
                ExistingPeriodicWorkPolicy.KEEP,
                syncRequest
            )
        }
    }
}
