package ar.com.mandados.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ZonePresentationTest {
    private fun zone(id: String, name: String, category: String) =
        ZoneConfig(id = id, name = name, description = "", category = category)

    @Test
    fun categoriesAndNamesAreSortedIgnoringCaseAndAccents() {
        val zones = listOf(
  zone("zeta", "Éxodo", "Zeta"),
  zone("arbol", "Árbol", "bÁrrios"),
  zone("aguila", "aguila", "BARRIOS"),
  zone("accesos", "avellaneda", "Accesos")
        )
        assertEquals(
  listOf("accesos", "aguila", "arbol", "zeta"),
  zonesForPresentation(zones).map { it.id }
        )
    }

    @Test
    fun namesUseAccentAndCaseInsensitiveAlphabeticalOrder() {
        val zones = listOf(
  zone("exodo", "Éxodo", "Única"),
  zone("arbol", "Árbol", "unica"),
  zone("aguila-1", "Águila", "UNICA"),
  zone("avellaneda", "avellaneda", "única"),
  zone("eucalipto", "eucalipto", "Unica"),
  zone("aguila-2", "aguila", "única")
        )
        assertEquals(
  listOf("aguila-1", "aguila-2", "arbol", "avellaneda", "eucalipto", "exodo"),
  zonesForPresentation(zones).map { it.id }
        )
    }

    @Test
    fun normalizedEquivalentNamesRemainStableAndDeterministic() {
        val first = zone("first", "Águila", "ÁREA")
        val second = zone("second", "aguila", "area")
        val third = zone("third", "AGUILA", "Área")
        val zones = listOf(first, second, third)
        val once = zonesForPresentation(zones)
        val twice = zonesForPresentation(zones)
        assertEquals(listOf("first", "second", "third"), once.map { it.id })
        assertEquals(once.map { it.id }, twice.map { it.id })
        assertSame(first, once[0])
        assertSame(second, once[1])
        assertSame(third, once[2])
    }

    @Test
    fun emptyListRemainsEmpty() {
        assertEquals(emptyList<ZoneConfig>(), zonesForPresentation(emptyList()))
    }

    @Test
    fun singleZonePreservesOriginalObject() {
        val only = zone("only", "Única", "Categoría")
        val result = zonesForPresentation(listOf(only))
        assertEquals(1, result.size)
        assertSame(only, result.single())
    }

    @Test
    fun sortingDoesNotMutateInputOrCloneZoneObjects() {
        val first = zone("b", "Beta", "Misma")
        val second = zone("a", "Alfa", "Misma")
        val source = mutableListOf(first, second)
        val before = source.toList()
        val result = zonesForPresentation(source)
        assertEquals(before, source)
        assertEquals(listOf("a", "b"), result.map { it.id })
        assertSame(second, result[0])
        assertSame(first, result[1])
    }
}
