package ar.com.mandados.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

internal enum class RiderOrderNavigationPoint(val buttonLabel: String) {
    ORIGIN("ABRIR RETIRO"),
    PRE_PICKUP("ABRIR RETIRO PREVIO"),
    STORE("ABRIR COMERCIO"),
    DESTINATION("ABRIR ENTREGA")
}

internal data class RiderOrderNavigationDestination(
    val type: RiderOrderNavigationPoint,
    val point: GeoPoint
)

internal enum class RiderNavigationLaunchResult {
    OPENED,
    NO_HANDLER
}

internal fun riderOrderNavigationDestinations(order: LocalOrder): List<RiderOrderNavigationDestination> =
    orderLocationPoints(order).map { location ->
        RiderOrderNavigationDestination(
            type = when (location.kind) {
                OrderLocationKind.ORIGIN -> RiderOrderNavigationPoint.ORIGIN
                OrderLocationKind.PRE_PICKUP -> RiderOrderNavigationPoint.PRE_PICKUP
                OrderLocationKind.STORE -> RiderOrderNavigationPoint.STORE
                OrderLocationKind.DESTINATION -> RiderOrderNavigationPoint.DESTINATION
            },
            point = location.point
        )
    }

internal fun riderNavigationUri(point: GeoPoint): Uri {
    val latitude = java.lang.Double.toString(point.latitude)
    val longitude = java.lang.Double.toString(point.longitude)
    return Uri.parse("geo:$latitude,$longitude?q=$latitude,$longitude")
}

internal fun riderNavigationIntent(destination: RiderOrderNavigationDestination): Intent =
    Intent(Intent.ACTION_VIEW, riderNavigationUri(destination.point))

internal fun launchRiderNavigation(
    intent: Intent,
    launcher: (Intent) -> Unit
): RiderNavigationLaunchResult = try {
    launcher(intent)
    RiderNavigationLaunchResult.OPENED
} catch (_: ActivityNotFoundException) {
    RiderNavigationLaunchResult.NO_HANDLER
}
