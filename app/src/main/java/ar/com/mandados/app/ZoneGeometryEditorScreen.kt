package ar.com.mandados.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.layers.LineLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private const val ZONE_GEOMETRY_OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private val ZONE_GEOMETRY_DEFAULT_POINT = GeoPoint(-35.432471, -60.171559)

@Composable
internal fun ZoneGeometryEditorDialog(
    c: MandadosController,
    zoneId: String,
    adminAuthorized: Boolean,
    onDismiss: () -> Unit,
    onAuthLost: () -> Unit
) {
    val zone = c.zone(zoneId)
    LaunchedEffect(adminAuthorized, zoneId, zone) {
        if (!adminAuthorized || zone == null) onAuthLost()
    }
    if (!adminAuthorized || zone == null) return

    var snapshot by rememberSaveable(zone.id, stateSaver = zoneGeometryEditorSnapshotSaver) {
        mutableStateOf(initialZoneGeometryEditorSnapshot(zone.polygons))
    }
    var feedback by remember(zone.id) { mutableStateOf<String?>(null) }
    val exitGuard = rememberUnsavedChangesGuardState()
    val dirty = isZoneGeometryEditorDirty(snapshot)

    fun requestClose() {
        when (zoneGeometryExitDecision(adminAuthorized, dirty)) {
            ZoneGeometryExitDecision.EXIT_FAIL_CLOSED -> onAuthLost()
            ZoneGeometryExitDecision.EXIT -> onDismiss()
            ZoneGeometryExitDecision.CONFIRM_DISCARD -> exitGuard.requestExit(true, onDismiss)
        }
    }

    val contextZones = c.config.zones.filter { it.id != zone.id && it.enabled && it.polygons.isNotEmpty() }
    val viewportPoints = (snapshot.draft.flatten() + contextZones.flatMap { it.polygons.flatten() })
    val initialPoint = viewportPoints.takeIf { it.isNotEmpty() }?.let { points ->
        GeoPoint(
            latitude = points.map { it.latitude }.average(),
            longitude = points.map { it.longitude }.average()
        )
    } ?: ZONE_GEOMETRY_DEFAULT_POINT
    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(ZONE_GEOMETRY_OPENFREEMAP_STYLE),
        initialCameraPosition = CameraPosition(
            target = Position(longitude = initialPoint.longitude, latitude = initialPoint.latitude),
            zoom = if (viewportPoints.isEmpty()) 11.0 else 13.0
        )
    ) {
        contextZones.forEachIndexed { index, other ->
            val source = rememberGeoJsonSource(
                GeoJsonData.JsonString(zoneGeometryMultiLineGeoJson(other.polygons))
            )
            LineLayer(
                id = "zone-context-$index",
                source = source,
                color = const(Color(0xFF777777)),
                width = const(2.dp),
                opacity = const(0.70f)
            )
        }

        if (snapshot.draft.isNotEmpty()) {
            val draftSource = rememberGeoJsonSource(
                GeoJsonData.JsonString(zoneGeometryMultiLineGeoJson(snapshot.draft))
            )
            LineLayer(
                id = "zone-editing-draft",
                source = draftSource,
                color = const(Color(0xFF006B5E)),
                width = const(4.dp)
            )
        }

        if (snapshot.drawing.size >= 2) {
            val drawingSource = rememberGeoJsonSource(
                GeoJsonData.JsonString(zoneGeometryDrawingGeoJson(snapshot.drawing))
            )
            LineLayer(
                id = "zone-current-drawing",
                source = drawingSource,
                color = const(Color(0xFFB56A00)),
                width = const(4.dp)
            )
        }

        if (snapshot.drawing.isNotEmpty()) {
            val vertices = rememberGeoJsonSource(
                GeoJsonData.JsonString(zoneGeometryPointCollectionGeoJson(snapshot.drawing))
            )
            CircleLayer(
                id = "zone-current-vertices",
                source = vertices,
                color = const(Color(0xFFB56A00)),
                radius = const(7.dp),
                strokeColor = const(Color.White),
                strokeWidth = const(2.dp)
            )
        }
    }

    Dialog(
        onDismissRequest = ::requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(14.dp)
            ) {
                Text("Geometría · ${zone.name}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Tocá el mapa para agregar vértices. Las zonas activas ajenas se muestran en gris sólo como contexto.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(8.dp))

                MaplibreMap(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = mapState,
                    interactions = MapInteractions {
                        callbacks {
                            click {
                                onEvent { event ->
                                    event.position?.let { position ->
                                        snapshot = addZoneGeometryVertex(
                                            snapshot,
                                            GeoPoint(position.latitude, position.longitude)
                                        )
                                        feedback = null
                                    }
                                    ClickResult.Consume
                                }
                            }
                        }
                    }
                )

                Text(
                    if (snapshot.redrawingIndex == null) {
                        "Trazado actual: ${snapshot.drawing.size} vértices"
                    } else {
                        "Redibujando polígono ${snapshot.redrawingIndex!! + 1}: ${snapshot.drawing.size} vértices"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { snapshot = undoZoneGeometryVertex(snapshot); feedback = null },
                        enabled = snapshot.drawing.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) { Text("DESHACER") }
                    OutlinedButton(
                        onClick = { snapshot = cancelZoneGeometryDrawing(snapshot); feedback = null },
                        enabled = snapshot.drawing.isNotEmpty() || snapshot.redrawingIndex != null,
                        modifier = Modifier.weight(1f)
                    ) { Text("CANCELAR TRAZADO") }
                }
                Button(
                    onClick = {
                        val result = finishZoneGeometryDrawing(snapshot)
                        snapshot = result.snapshot
                        feedback = result.message
                    },
                    enabled = snapshot.drawing.size >= 3,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (snapshot.redrawingIndex == null) "FINALIZAR POLÍGONO" else "FINALIZAR REDIBUJADO") }

                if (snapshot.draft.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(snapshot.draft) { index, polygon ->
                            Card {
                                Column(Modifier.padding(8.dp)) {
                                    Text("Polígono ${index + 1} · ${polygon.size} vértices", fontWeight = FontWeight.SemiBold)
                                    Row {
                                        OutlinedButton(
                                            onClick = {
                                                snapshot = beginZoneGeometryRedraw(snapshot, index)
                                                feedback = "Tocá los vértices del nuevo trazado para reemplazar el polígono ${index + 1}."
                                            }
                                        ) { Text("REDIBUJAR") }
                                        Spacer(Modifier.width(6.dp))
                                        OutlinedButton(
                                            onClick = { snapshot = deleteZoneGeometryPolygon(snapshot, index); feedback = null }
                                        ) { Text("ELIMINAR") }
                                    }
                                }
                            }
                        }
                    }
                }

                feedback?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }

                Button(
                    onClick = {
                        if (snapshot.drawing.isNotEmpty()) {
                            feedback = "Finalizá o cancelá el trazado actual antes de guardar."
                        } else {
                            val result = c.updateZoneGeometry(zone.id, snapshot.draft)
                            if (result.success) {
                                snapshot = markZoneGeometrySaved(snapshot)
                                feedback = "Geometría guardada."
                            } else {
                                feedback = result.message ?: "No se pudo guardar la geometría."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) { Text("GUARDAR GEOMETRÍA") }

                OutlinedButton(onClick = ::requestClose, modifier = Modifier.fillMaxWidth()) { Text("VOLVER") }
            }
        }
    }

    if (exitGuard.hasPendingExit) {
        DiscardChangesDialog(
            onKeepEditing = exitGuard::keepEditing,
            onDiscard = { exitGuard.discard() }
        )
    }
}

private fun zoneGeometryPointCollectionGeoJson(points: List<GeoPoint>): String {
    val coordinates = points.joinToString(",") { "[${it.longitude},${it.latitude}]" }
    return """{"type":"Feature","geometry":{"type":"MultiPoint","coordinates":[$coordinates]},"properties":{}}"""
}
