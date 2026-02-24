package com.map.vectra

import kotlin.math.*

// UPDATED: Completely migrated to Double to prevent data destruction during import
data class PointData(
    val id: Int,
    val x: Double,
    val y: Double
)

object CsvDxfUtils {

    // --- PARSERS (Existing) ---
    fun parseRawCsv(csvContent: String): List<List<String>> {
        val cleanContent = csvContent.replace("\uFEFF", "")
        val lines = cleanContent.lines()
        val rawData = mutableListOf<List<String>>()

        lines.forEach { line ->
            val cleanLine = line.trim()
            if (cleanLine.isNotEmpty()) {
                val parts = cleanLine.split(Regex("[,\\t\\s]+")).map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.isNotEmpty()) rawData.add(parts)
            }
        }
        return rawData
    }

    fun mapToPoints(rawRows: List<List<String>>, xIndex: Int, yIndex: Int): List<PointData> {
        val points = mutableListOf<PointData>()
        var idCounter = 0
        rawRows.forEach { row ->
            if (row.size > xIndex && row.size > yIndex) {
                // Parse directly to Double
                val x = row[xIndex].toDoubleOrNull()
                val y = row[yIndex].toDoubleOrNull()
                if (x != null && y != null) {
                    points.add(PointData(idCounter++, x, y))
                }
            }
        }
        return points
    }

    fun generateDxf(points: List<PointData>): String {
        val sb = StringBuilder()
        sb.append("0\nSECTION\n2\nENTITIES\n")
        for (p in points) {
            sb.append("0\nPOINT\n")
            sb.append("8\n0\n")
            sb.append("10\n${p.x}\n")
            sb.append("20\n${p.y}\n")
            sb.append("30\n0.0\n")
        }
        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }

    // --- NEW: GEOMETRY MATH ---

    // 1. Calculate Polygon Area (Shoelace Formula)
    fun calculatePolygonArea(points: List<PointData>): Double {
        var area = 0.0
        val n = points.size
        for (i in 0 until n) {
            val j = (i + 1) % n
            area += points[i].x * points[j].y
            area -= points[j].x * points[i].y
        }
        return abs(area / 2.0)
    }

    // 2. Distance between two points
    fun distance(p1: PointData, p2: PointData): Double {
        return sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
    }

    // 3. Check if a point is inside the polygon (Ray Casting Algorithm)
    fun isInsidePolygon(testPoint: PointData, polygon: List<PointData>): Boolean {
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

    // 4. Distance from a point to a line segment
    fun distanceToSegment(p: PointData, v: PointData, w: PointData): Double {
        val l2 = (v.x - w.x).pow(2) + (v.y - w.y).pow(2)
        if (l2 == 0.0) return distance(p, v)
        var t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
        t = max(0.0, min(1.0, t))
        val projectionX = v.x + t * (w.x - v.x)
        val projectionY = v.y + t * (w.y - v.y)
        return distance(p, PointData(0, projectionX, projectionY))
    }
}