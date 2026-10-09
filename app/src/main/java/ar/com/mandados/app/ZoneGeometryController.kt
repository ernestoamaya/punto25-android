package ar.com.mandados.app

internal fun MandadosController.updateZoneGeometry(
    zoneId: String,
    polygons: List<List<GeoPoint>>
): ZoneGeometryMutationResult {
    val current = zone(zoneId)
        ?: return ZoneGeometryMutationResult(false, "La zona ya no existe.")
    val snapshot = polygons.map { polygon -> polygon.toList() }
    val candidate = current.copy(polygons = snapshot)
    val candidateZones = config.zones.map { if (it.id == zoneId) candidate else it }
    val validation = validateZoneGeometryForZone(candidate, candidateZones)
    if (!validation.valid) {
        return ZoneGeometryMutationResult(
            success = false,
            message = validation.message ?: "La geometría no es válida.",
            conflictingZoneId = validation.conflictingZoneId
        )
    }
    if (candidate.polygons == current.polygons) return ZoneGeometryMutationResult.SUCCESS

    if (!updateConfig(config.copy(zones = candidateZones))) {
        return ZoneGeometryMutationResult(false, "La configuración activa no es segura para resolución automática.")
    }
    return ZoneGeometryMutationResult.SUCCESS
}

internal fun canApplyZoneMetadataUpdate(
    config: AdminConfig,
    current: ZoneConfig,
    candidate: ZoneConfig
): Boolean {
    if (current.enabled || !candidate.enabled) return true
    val candidateZones = config.zones.map { if (it.id == candidate.id) candidate else it }
    return validateZoneGeometryForZone(candidate, candidateZones).valid
}
