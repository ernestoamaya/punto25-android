package ar.com.mandados.app

internal enum class MapTarget {
    DELIVERY_ORIGIN,
    DELIVERY_DESTINATION,
    SHOPPING_PRE_PICKUP,
    SHOPPING_STORE,
    SHOPPING_DESTINATION
}

internal fun mapPointForTarget(draft: OrderDraft, target: MapTarget?): GeoPoint? = when (target) {
    MapTarget.DELIVERY_ORIGIN -> draft.originLocation
    MapTarget.DELIVERY_DESTINATION -> draft.destinationLocation
    MapTarget.SHOPPING_PRE_PICKUP -> draft.prePickupLocation
    MapTarget.SHOPPING_STORE -> draft.storeLocation
    MapTarget.SHOPPING_DESTINATION -> draft.destinationLocation
    null -> null
}

internal fun geoPointFromMapCoordinates(latitude: Double, longitude: Double): GeoPoint =
    GeoPoint(latitude = latitude, longitude = longitude)

internal fun applyMapSelection(draft: OrderDraft, target: MapTarget?, point: GeoPoint): OrderDraft = when (target) {
    MapTarget.DELIVERY_ORIGIN -> draft.copy(originLocation = point)
    MapTarget.DELIVERY_DESTINATION -> draft.copy(destinationLocation = point)
    MapTarget.SHOPPING_PRE_PICKUP -> if (draft.sameDeliveryAsPrePickup) {
        draft.copy(prePickupLocation = point, destinationLocation = point)
    } else {
        draft.copy(prePickupLocation = point)
    }
    MapTarget.SHOPPING_STORE -> draft.copy(storeLocation = point)
    MapTarget.SHOPPING_DESTINATION -> draft.copy(destinationLocation = point)
    null -> draft
}
