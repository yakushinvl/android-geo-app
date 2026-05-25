package com.yaku.geo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yaku.geo.data.AppDatabase
import com.yaku.geo.data.VisitedPoint
import com.yaku.geo.data.VisitedPointDao
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.osmdroid.util.GeoPoint

class LocationViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: VisitedPointDao = AppDatabase.getDatabase(application).visitedPointDao()

    val visitedPoints: StateFlow<List<VisitedPoint>> = dao.getAllVisitedPoints()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun addPoint(geoPoint: GeoPoint) {
        viewModelScope.launch {
            dao.insert(VisitedPoint(latitude = geoPoint.latitude, longitude = geoPoint.longitude))
        }
    }
    
    fun clearHistory() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }
}
