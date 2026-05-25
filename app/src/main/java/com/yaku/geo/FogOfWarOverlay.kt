package com.yaku.geo

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import com.yaku.geo.data.VisitedPoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

class FogOfWarOverlay(private var points: List<VisitedPoint>) : Overlay() {
    
    private var currentLocation: GeoPoint? = null

    private val fogPaint = Paint().apply {
        color = Color.argb(200, 0, 0, 0)
        style = Paint.Style.FILL
    }
    
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val pathPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    fun setPoints(newPoints: List<VisitedPoint>) {
        this.points = newPoints
    }

    fun setCurrentLocation(location: GeoPoint?) {
        this.currentLocation = location
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        
        val projection = mapView.projection
        val clipBounds = canvas.clipBounds
        
        // Save layer to support PorterDuff.Mode.CLEAR
        // Using null for bounds saves the entire current clip area
        val saveCount = canvas.saveLayer(null, null)
        
        // 1. Draw the fog
        canvas.drawRect(clipBounds, fogPaint)
        
        // Use a meter-based radius so it scales geographically with the map
        val radiusInMeters = 100.0 
        val radius = projection.metersToPixels(radiusInMeters.toFloat()).coerceAtLeast(10f)
        
        val screenPoint = Point()
        
        // 3. Draw historical points as circles only (no lines)
        for (point in points) {
            projection.toPixels(GeoPoint(point.latitude, point.longitude), screenPoint)
            
            // Draw clearing circle
            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius, clearPaint)
        }

        // 4. Draw current location hole
        currentLocation?.let {
            projection.toPixels(it, screenPoint)
            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius, clearPaint)
        }
        
        canvas.restoreToCount(saveCount)
    }
}
