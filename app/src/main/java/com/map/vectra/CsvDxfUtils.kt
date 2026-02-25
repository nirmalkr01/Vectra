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
                val x = row[xIndex].toDoubleOrNull()
                val y = row[yIndex].toDoubleOrNull()
                if (x != null && y != null) {
                    points.add(PointData(idCounter++, x, y))
                }
            }
        }
        return points
    }

    // --- NEW: DXF PARSER ---
    // Parses a DXF file and rebuilds PointData sequences
    fun parseDxf(dxfContent: String): Pair<List<PointData>, List<Pair<Int, Int>>> {
        val lines = dxfContent.lines().map { it.trim() }
        val parsedPoints = mutableListOf<PointData>()
        val parsedCustomLines = mutableListOf<Pair<Int, Int>>()

        var i = 0
        var currentEntity = ""
        var x1: Double? = null; var y1: Double? = null
        var x2: Double? = null; var y2: Double? = null

        val uniquePoints = mutableMapOf<Pair<Double, Double>, Int>()
        var idCounter = 0

        fun getPointId(x: Double, y: Double): Int {
            // Round slightly to prevent minor floating point parsing divergence
            val rx = (x * 1000000.0).roundToInt() / 1000000.0
            val ry = (y * 1000000.0).roundToInt() / 1000000.0
            return uniquePoints.getOrPut(Pair(rx, ry)) { idCounter++ }
        }

        while (i < lines.size) {
            val code = lines[i]
            val value = lines.getOrNull(i + 1) ?: ""

            if (code == "0") {
                // When hitting a new entity marker (0), process the previously gathered data
                if (value == "POINT" || value == "LINE" || value == "ENDSEC") {
                    if (currentEntity == "POINT" && x1 != null && y1 != null) {
                        getPointId(x1, y1)
                    } else if (currentEntity == "LINE" && x1 != null && y1 != null && x2 != null && y2 != null) {
                        val id1 = getPointId(x1, y1)
                        val id2 = getPointId(x2, y2)
                        parsedCustomLines.add(Pair(id1, id2))
                    }
                    currentEntity = value
                    x1 = null; y1 = null; x2 = null; y2 = null
                }
            } else {
                when (code) {
                    "10" -> x1 = value.toDoubleOrNull()
                    "20" -> y1 = value.toDoubleOrNull()
                    "11" -> x2 = value.toDoubleOrNull()
                    "21" -> y2 = value.toDoubleOrNull()
                }
            }
            i += 2
        }

        // Assemble the final PointData list by mapping the unique ID dictionary back to objects
        val pointMap = uniquePoints.entries.associate { it.value to PointData(it.value, it.key.first, it.key.second) }

        // Rebuild sequences: treat all parsed lines as gaps separated by default.
        // This ensures the Duplicate checker can naturally handle overlaps gracefully.
        parsedCustomLines.forEach { (id1, id2) ->
            pointMap[id1]?.let { parsedPoints.add(it) }
            pointMap[id2]?.let { parsedPoints.add(it) }
            parsedPoints.add(PointData(idCounter++, Double.NaN, Double.NaN))
        }

        // Clean up trailing gap
        if (parsedPoints.isNotEmpty() && parsedPoints.last().x.isNaN()) {
            parsedPoints.removeLast()
        }

        // We return the customLines empty because we converted all lines to gap-separated sequences
        // allowing Area Calc to treat them natively.
        return Pair(parsedPoints, emptyList())
    }

    // --- DXF GENERATOR ---
    fun generateDxf(points: List<PointData>, lines: List<Pair<Int, Int>>? = null): String {
        val sb = StringBuilder()

        fun formatDouble(d: Double): String = String.format(java.util.Locale.US, "%.6f", d)

        sb.append("0\nSECTION\n2\nHEADER\n")
        sb.append("9\n\$ACADVER\n1\nAC1009\n")
        sb.append("0\nENDSEC\n")

        sb.append("0\nSECTION\n2\nENTITIES\n")

        for (p in points) {
            if (p.x.isNaN() || p.y.isNaN()) continue
            sb.append("0\nPOINT\n8\n0\n")
            sb.append("10\n${formatDouble(p.x)}\n20\n${formatDouble(p.y)}\n30\n0.0\n")
        }

        val validPoints = points.filter { it.x.isFinite() && it.y.isFinite() }
        if (validPoints.size > 1) {
            // Draw Sequential paths but respect gaps
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                if (p1.x.isFinite() && p1.y.isFinite() && p2.x.isFinite() && p2.y.isFinite()) {
                    sb.append("0\nLINE\n8\n0\n")
                    sb.append("10\n${formatDouble(p1.x)}\n20\n${formatDouble(p1.y)}\n30\n0.0\n")
                    sb.append("11\n${formatDouble(p2.x)}\n21\n${formatDouble(p2.y)}\n31\n0.0\n")
                }
            }
        }

        if (lines != null) {
            val pointMap = validPoints.associateBy { it.id }
            for ((id1, id2) in lines) {
                val p1 = pointMap[id1]
                val p2 = pointMap[id2]
                if (p1 != null && p2 != null) {
                    sb.append("0\nLINE\n8\n0\n")
                    sb.append("10\n${formatDouble(p1.x)}\n20\n${formatDouble(p1.y)}\n30\n0.0\n")
                    sb.append("11\n${formatDouble(p2.x)}\n21\n${formatDouble(p2.y)}\n31\n0.0\n")
                }
            }
        }

        sb.append("0\nENDSEC\n0\nEOF\n")
        return sb.toString()
    }

    // --- GEOMETRY MATH ---
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

    fun distance(p1: PointData, p2: PointData): Double {
        return sqrt((p2.x - p1.x).pow(2) + (p2.y - p1.y).pow(2))
    }

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