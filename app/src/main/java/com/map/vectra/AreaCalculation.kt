package com.map.vectra

import kotlin.math.*

object AreaCalculation {

    fun calculateArea(points: List<PointData>): Double {
        if (points.size < 3) return 0.0

        val isLikelyGPS = points.all { abs(it.x) <= 180.0 && abs(it.y) <= 90.0 }

        if (isLikelyGPS) {
            return calculateEarthAreaInSquareMeters(points)
        }

        var area = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area / 2.0)
    }

    private fun calculateEarthAreaInSquareMeters(polygon: List<PointData>): Double {
        val radius = 6371009.0

        if (polygon.size < 3) return 0.0

        var totalArea = 0.0
        val prev = polygon.last()

        var prevTanLat = tan((PI / 2.0 - prev.y * (PI / 180.0)) / 2.0)
        var prevLng = prev.x * (PI / 180.0)

        for (point in polygon) {
            val tanLat = tan((PI / 2.0 - point.y * (PI / 180.0)) / 2.0)
            val lng = point.x * (PI / 180.0)

            val deltaLng = lng - prevLng
            val t = tanLat * prevTanLat

            totalArea += 2.0 * atan2(t * sin(deltaLng), 1.0 + t * cos(deltaLng))

            prevTanLat = tanLat
            prevLng = lng
        }

        return abs(totalArea * (radius * radius))
    }

    fun isInside(testPoint: PointData, polygon: List<PointData>): Boolean {
        if (polygon.size < 3) return false
        var result = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            if ((polygon[i].y > testPoint.y) != (polygon[j].y > testPoint.y) &&
                (testPoint.x < (polygon[j].x - polygon[i].x) * (testPoint.y - polygon[i].y) / (polygon[j].y - polygon[i].y) + polygon[i].x)) {
                result = !result
            }
            j = i
        }
        return result
    }
}