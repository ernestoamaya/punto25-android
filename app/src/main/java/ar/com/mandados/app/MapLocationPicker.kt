package ar.com.mandados.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.LocationServices
import org.maplibre.compose.camera.CameraPosition
import org.maplibre.compose.expressions.dsl.const
import org.maplibre.compose.interaction.ClickResult
import org.maplibre.compose.interaction.MapInteractions
import org.maplibre.compose.layers.CircleLayer
import org.maplibre.compose.map.MaplibreMap
import org.maplibre.compose.map.rememberMapState
import org.maplibre.compose.sources.GeoJsonData
import org.maplibre.compose.sources.rememberGeoJsonSource
import org.maplibre.compose.style.BaseStyle
import org.maplibre.spatialk.geojson.Position

private const val OPENFREEMAP_LIBERTY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private val DEFAULT_MAP_POINT = GeoPoint(-35.432471, -60.171559)

@SuppressLint("MissingPermission")
@Composable
internal fun LocationPickerScreen(
    c: MandadosController,
    target: MapTarget?,
    onBack: () -> Unit,
    onConfirmed: () -> Unit
) {
    val context = LocalContext.current
    val fused = remember { LocationServices.getFusedLocationProviderClient(context) }
    val initial = mapPointForTarget(c.draft, target) ?: DEFAULT_MAP_POINT
    var selectedPoint by remember(target) { mutableStateOf(initial) }

    val mapState = rememberMapState(
        baseStyle = BaseStyle.Uri(OPENFREEMAP_LIBERTY_STYLE),
        initialCameraPosition = CameraPosition(
            target = Position(longitude = initial.longitude, latitude = initial.latitude),
            zoom = 15.0
        )
    ) {
        val selectedSource = rememberGeoJsonSource(
            GeoJsonData.JsonString(
                """{"type":"Feature","geometry":{"type":"Point","coordinates":[${selectedPoint.longitude},${selectedPoint.latitude}]},"properties":{}}"""
            )
        )
        CircleLayer(
            id = "selected-location",
            source = selectedSource,
            color = const(Color(0xFF006B5E)),
            radius = const(8.dp),
            strokeColor = const(Color.White),
            strokeWidth = const(2.dp)
        )
    }

    fun useLastLocation() {
        runCatching {
            fused.lastLocation.addOnSuccessListener { location ->
                if (location == null) {
                    Toast.makeText(
                        context,
                        "No se pudo obtener una ubicación reciente. Mové el pin manualmente.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val point = geoPointFromMapCoordinates(location.latitude, location.longitude)
                    selectedPoint = point
                    mapState.setCameraPosition(
                        CameraPosition(
                            target = Position(longitude = point.longitude, latitude = point.latitude),
                            zoom = 17.0
                        )
                    )
                }
            }
        }.onFailure {
            Toast.makeText(
                context,
                "No se pudo acceder a la ubicación. Podés mover el pin manualmente.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) {
            useLastLocation()
        } else {
            Toast.makeText(
                context,
                "Permiso de ubicación no concedido. Podés mover el pin manualmente.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    fun requestCurrentLocation() {
        val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fine || coarse) {
            useLastLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Page("Marcar ubicación", onBack) {
        Text("Tocá el mapa para mover el pin.")
        Spacer(Modifier.height(10.dp))

        MaplibreMap(
            modifier = Modifier.fillMaxWidth().height(430.dp),
            state = mapState,
            interactions = MapInteractions {
                callbacks {
                    click {
                        onEvent { event ->
                            event.position?.let { position ->
                                selectedPoint = geoPointFromMapCoordinates(position.latitude, position.longitude)
                            }
                            ClickResult.Consume
                        }
                    }
                }
            }
        )

        Spacer(Modifier.height(10.dp))
        Text(
            "Ubicación marcada",
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(
            onClick = ::requestCurrentLocation,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("USAR MI UBICACIÓN COMO PUNTO INICIAL")
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                c.draft = applyMapSelection(c.draft, target, selectedPoint)
                onConfirmed()
            },
            enabled = target != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("USAR ESTA UBICACIÓN")
        }
        Spacer(Modifier.height(8.dp))
        AssistBox(
            "El pin y la dirección escrita son datos independientes. Mover el pin no cambia automáticamente la dirección ni la zona tarifaria."
        )
    }
}
