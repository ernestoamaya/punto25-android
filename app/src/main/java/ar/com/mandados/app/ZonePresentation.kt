package ar.com.mandados.app

import java.text.Normalizer
import java.util.Locale

private fun Char.isCombiningMark(): Boolean {
    val type = Character.getType(this)
    return type == Character.NON_SPACING_MARK.toInt() ||
        type == Character.COMBINING_SPACING_MARK.toInt() ||
        type == Character.ENCLOSING_MARK.toInt()
}

internal fun zonePresentationKey(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFD)
        .filterNot { it.isCombiningMark() }
        .lowercase(Locale.ROOT)

internal fun zonesForPresentation(zones: List<ZoneConfig>): List<ZoneConfig> =
    zones.withIndex()
        .sortedWith(
  compareBy<IndexedValue<ZoneConfig>>(
      { zonePresentationKey(it.value.category) },
      { zonePresentationKey(it.value.name) },
      { it.index }
  )
        )
        .map { it.value }
