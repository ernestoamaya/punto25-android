package ar.com.mandados.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private const val ORDER_LOCATIONS_OPENFREEMAP_STYLE = "https://tiles.openfreemap.org/styles/liberty"

@Composable
internal fun OrderLocationsMapDialog(
    points: List<OrderLocationPoint>,
    onDismiss: () -> Unit
) {
    val safePoints = points.filter { isValidOrderLocation(it.point) }
    val viewport = orderLocationsViewport(safePoints) ?: return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    "Ubicaciones del pedido",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Vista de consulta. Los puntos no se pueden editar desde esta pantalla.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))

                key(safePoints) {
                    val mapState = rememberMapState(
                        baseStyle = BaseStyle.Uri(ORDER_LOCATIONS_OPENFREEMAP_STYLE),
                        initialCameraPosition = CameraPosition(
                            target = Position(
                                longitude = viewport.target.longitude,
                                latitude = viewport.target.latitude
                            ),
                            zoom = viewport.zoom
                        )
                    ) {
                        safePoints.forEachIndexed { index, location ->
                            val coordinate = orderMapCoordinate(location.point)
                            val source = rememberGeoJsonSource(
                                GeoJsonData.JsonString(
                                    """{"type":"Feature","geometry":{"type":"Point","coordinates":[${coordinate.longitude},${coordinate.latitude}]},"properties":{}}"""
                                )
                            )
                            CircleLayer(
                                id = "order-location-${location.kind.name.lowercase()}-$index",
                                source = source,
                                color = const(orderLocationColor(location.kind)),
                                radius = const(8.dp),
                                strokeColor = const(Color.White),
                                strokeWidth = const(2.dp)
                            )
                        }
                    }

                    MaplibreMap(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        state = mapState
                    )
                }

                Column(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    safePoints.forEach { location ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .background(orderLocationColor(location.kind), CircleShape)
                            )
                            Text(
                                location.kind.label,
                                modifier = Modifier.padding(start = 8.dp),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                ) {
                    Text("VOLVER")
                }
            }
        }
    }
}

@Composable
internal fun StructuredOrderPresentation(
    c: MandadosController,
    order: LocalOrder,
    modifier: Modifier = Modifier,
    includeLocationDetails: Boolean = true
) {
    val lines = orderPresentationLines(
        order = order,
        includeLocationDetails = includeLocationDetails
    ) { zoneId ->
        when {
            zoneId == UNKNOWN_ZONE_ID -> "A confirmar"
            else -> c.zone(zoneId)?.name ?: zoneId
        }
    }
    Column(modifier) {
        lines.forEach { line ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                Text(
                    line.label,
                    modifier = Modifier.weight(0.38f),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    line.value,
                    modifier = Modifier.weight(0.62f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
internal fun AdminStructuredOrderPresentation(
    c: MandadosController,
    order: LocalOrder
) {
    val locations = orderLocationPoints(order)
    var selectedLocations by remember(order.id) { mutableStateOf<List<OrderLocationPoint>>(emptyList()) }

    Card(Modifier.fillMaxWidth()) {
        StructuredOrderPresentation(c, order, Modifier.padding(12.dp))
    }

    AdminOrderZoneOverridesPanel(c, order)

    if (locations.isNotEmpty()) {
        Spacer(Modifier.height(10.dp))
        Text("UBICACIONES", fontWeight = FontWeight.Bold)
        locations.forEach { location ->
            OutlinedButton(
                onClick = { selectedLocations = listOf(location) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Text(location.kind.adminButtonLabel)
            }
        }
        if (locations.size > 1) {
            OutlinedButton(
                onClick = { selectedLocations = locations },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            ) {
                Text("VER TODAS LAS UBICACIONES")
            }
        }
    }

    if (selectedLocations.isNotEmpty()) {
        OrderLocationsMapDialog(selectedLocations) { selectedLocations = emptyList() }
    }
}

@Composable
internal fun RiderStructuredOrderPresentation(
    c: MandadosController,
    order: LocalOrder
) {
    var showLocations by remember(order.id) { mutableStateOf(false) }
    val assignedRiderId = order.assignedRiderId
    val locations = if (
        assignedRiderId != null &&
        order.status in setOf(OrderStatus.PENDING, OrderStatus.ACCEPTED, OrderStatus.IN_PROGRESS)
    ) {
        authenticatedRiderOrderLocations(c, assignedRiderId, order.id)
    } else {
        emptyList()
    }

    StructuredOrderPresentation(c, order)

    if (locations.isNotEmpty()) {
        Text("UBICACIONES", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        OutlinedButton(
            onClick = { showLocations = true },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        ) {
            Text("VER UBICACIONES")
        }
    }

    if (showLocations && locations.isNotEmpty()) {
        OrderLocationsMapDialog(locations) { showLocations = false }
    }
}

private fun orderLocationColor(kind: OrderLocationKind): Color = when (kind) {
    OrderLocationKind.ORIGIN -> Color(0xFF006B5E)
    OrderLocationKind.PRE_PICKUP -> Color(0xFFB87916)
    OrderLocationKind.STORE -> Color(0xFF315DA8)
    OrderLocationKind.DESTINATION -> Color(0xFFA84D4D)
}
