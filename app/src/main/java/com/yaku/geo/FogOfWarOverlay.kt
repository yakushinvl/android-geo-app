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
import android.graphics.Matrix

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

    private val identityMatrix = Matrix()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        
        val projection = mapView.projection
        val width = mapView.width.toFloat()
        val height = mapView.height.toFloat()
        
        if (width <= 0 || height <= 0) return

        // 1. Save state and switch to screen-space (0,0 is top-left of the View)
        val saveCount = canvas.save()
        canvas.setMatrix(identityMatrix)
        
        // 2. Create a layer for the entire screen to support PorterDuff.Mode.CLEAR
        // Explicitly defining bounds helps avoid artifacts on some devices
        val layerCount = canvas.saveLayer(0f, 0f, width, height, null)
        
        // 3. Draw the fog over the whole screen
        canvas.drawRect(0f, 0f, width, height, fogPaint)
        
        // 4. Calculate radius geographically
        val radiusInMeters = 100.0 
        val radius = projection.metersToPixels(radiusInMeters.toFloat()).coerceAtLeast(10f)
        
        val screenPoint = Point()
        
        // 5. Draw historical points (projection.toPixels returns screen coordinates)
        for (point in points) {
            projection.toPixels(GeoPoint(point.latitude, point.longitude), screenPoint)
            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius, clearPaint)
        }

        // 6. Draw current location hole
        currentLocation?.let {
            projection.toPixels(it, screenPoint)
            canvas.drawCircle(screenPoint.x.toFloat(), screenPoint.y.toFloat(), radius, clearPaint)
        }
        
        canvas.restoreToCount(layerCount)
        canvas.restoreToCount(saveCount)
    }
}
