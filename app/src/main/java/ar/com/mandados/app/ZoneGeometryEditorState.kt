package ar.com.mandados.app

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver

internal data class ZoneGeometryEditorSnapshot(
    val baseline: List<List<GeoPoint>>,
    val draft: List<List<GeoPoint>>,
    val drawing: List<GeoPoint> = emptyList(),
    val redrawingIndex: Int? = null
)

internal data class ZoneGeometryEditorActionResult(
    val snapshot: ZoneGeometryEditorSnapshot,
    val message: String? = null
)

internal enum class ZoneGeometryExitDecision {
    EXIT_FAIL_CLOSED,
    EXIT,
    CONFIRM_DISCARD
}

internal fun initialZoneGeometryEditorSnapshot(polygons: List<List<GeoPoint>>): ZoneGeometryEditorSnapshot {
    val copy = polygons.map { it.toList() }
    return ZoneGeometryEditorSnapshot(baseline = copy, draft = copy)
}

internal fun isZoneGeometryEditorDirty(snapshot: ZoneGeometryEditorSnapshot): Boolean =
    snapshot.draft != snapshot.baseline || snapshot.drawing.isNotEmpty()

internal fun zoneGeometryExitDecision(
    adminAuthorized: Boolean,
    dirty: Boolean
): ZoneGeometryExitDecision = when {
    !adminAuthorized -> ZoneGeometryExitDecision.EXIT_FAIL_CLOSED
    dirty -> ZoneGeometryExitDecision.CONFIRM_DISCARD
    else -> ZoneGeometryExitDecision.EXIT
}

internal fun addZoneGeometryVertex(
    snapshot: ZoneGeometryEditorSnapshot,
    point: GeoPoint
): ZoneGeometryEditorSnapshot = snapshot.copy(drawing = snapshot.drawing + point)

internal fun undoZoneGeometryVertex(snapshot: ZoneGeometryEditorSnapshot): ZoneGeometryEditorSnapshot =
    if (snapshot.drawing.isEmpty()) snapshot else snapshot.copy(drawing = snapshot.drawing.dropLast(1))

internal fun cancelZoneGeometryDrawing(snapshot: ZoneGeometryEditorSnapshot): ZoneGeometryEditorSnapshot =
    snapshot.copy(drawing = emptyList(), redrawingIndex = null)

internal fun beginZoneGeometryRedraw(
    snapshot: ZoneGeometryEditorSnapshot,
    polygonIndex: Int
): ZoneGeometryEditorSnapshot = if (polygonIndex in snapshot.draft.indices) {
    snapshot.copy(drawing = emptyList(), redrawingIndex = polygonIndex)
} else snapshot

internal fun deleteZoneGeometryPolygon(
    snapshot: ZoneGeometryEditorSnapshot,
    polygonIndex: Int
): ZoneGeometryEditorSnapshot {
    if (polygonIndex !in snapshot.draft.indices) return snapshot
    val next = snapshot.draft.toMutableList().also { it.removeAt(polygonIndex) }
    return snapshot.copy(
        draft = next,
        drawing = emptyList(),
        redrawingIndex = null
    )
}

internal fun finishZoneGeometryDrawing(snapshot: ZoneGeometryEditorSnapshot): ZoneGeometryEditorActionResult {
    val drawingValidation = validateZoneGeometry(listOf(snapshot.drawing))
    if (!drawingValidation.valid) {
        return ZoneGeometryEditorActionResult(
            snapshot = snapshot,
            message = drawingValidation.message ?: "El polígono no es válido."
        )
    }
    val next = snapshot.draft.toMutableList()
    val replacing = snapshot.redrawingIndex
    if (replacing != null && replacing in next.indices) next[replacing] = snapshot.drawing.toList()
    else next += listOf(snapshot.drawing.toList())

    val completeValidation = validateZoneGeometry(next)
    if (!completeValidation.valid) {
        return ZoneGeometryEditorActionResult(
            snapshot = snapshot,
            message = completeValidation.message ?: "La geometría de la zona no es válida."
        )
    }
    return ZoneGeometryEditorActionResult(
        snapshot = snapshot.copy(draft = next, drawing = emptyList(), redrawingIndex = null)
    )
}

internal fun markZoneGeometrySaved(snapshot: ZoneGeometryEditorSnapshot): ZoneGeometryEditorSnapshot =
    snapshot.copy(
        baseline = snapshot.draft.map { it.toList() },
        drawing = emptyList(),
        redrawingIndex = null
    )

internal fun encodeZoneGeometryEditorSnapshot(snapshot: ZoneGeometryEditorSnapshot): List<String> = buildList {
    add("ZGE1")
    encodePolygons(snapshot.baseline, this)
    encodePolygons(snapshot.draft, this)
    add(snapshot.drawing.size.toString())
    snapshot.drawing.forEach { point ->
        add(point.latitude.toString())
        add(point.longitude.toString())
    }
    add(snapshot.redrawingIndex?.toString().orEmpty())
}

internal fun decodeZoneGeometryEditorSnapshot(saved: List<String>): ZoneGeometryEditorSnapshot? = runCatching {
    if (saved.firstOrNull() != "ZGE1") return@runCatching null
    var cursor = 1
    fun readPolygons(): List<List<GeoPoint>> {
        val polygonCount = saved.getOrNull(cursor++)?.toIntOrNull() ?: error("polygon count")
        require(polygonCount >= 0)
        return buildList {
            repeat(polygonCount) {
                val vertexCount = saved.getOrNull(cursor++)?.toIntOrNull() ?: error("vertex count")
                require(vertexCount >= 0)
                add(buildList {
                    repeat(vertexCount) {
                        val lat = saved.getOrNull(cursor++)?.toDoubleOrNull() ?: error("latitude")
                        val lon = saved.getOrNull(cursor++)?.toDoubleOrNull() ?: error("longitude")
                        add(GeoPoint(lat, lon))
                    }
                })
            }
        }
    }
    val baseline = readPolygons()
    val draft = readPolygons()
    val drawingCount = saved.getOrNull(cursor++)?.toIntOrNull() ?: error("drawing count")
    require(drawingCount >= 0)
    val drawing = buildList {
        repeat(drawingCount) {
            val lat = saved.getOrNull(cursor++)?.toDoubleOrNull() ?: error("drawing latitude")
            val lon = saved.getOrNull(cursor++)?.toDoubleOrNull() ?: error("drawing longitude")
            add(GeoPoint(lat, lon))
        }
    }
    val redrawingRaw = saved.getOrNull(cursor++) ?: error("redraw")
    require(cursor == saved.size)
    ZoneGeometryEditorSnapshot(
        baseline = baseline,
        draft = draft,
        drawing = drawing,
        redrawingIndex = redrawingRaw.takeIf { it.isNotEmpty() }?.toIntOrNull()
    )
}.getOrNull()

private fun encodePolygons(polygons: List<List<GeoPoint>>, target: MutableList<String>) {
    target += polygons.size.toString()
    polygons.forEach { polygon ->
        target += polygon.size.toString()
        polygon.forEach { point ->
            target += point.latitude.toString()
            target += point.longitude.toString()
        }
    }
}

internal val zoneGeometryEditorSnapshotSaver: Saver<ZoneGeometryEditorSnapshot, Any> = listSaver(
    save = { encodeZoneGeometryEditorSnapshot(it) },
    restore = { decodeZoneGeometryEditorSnapshot(it) }
)
