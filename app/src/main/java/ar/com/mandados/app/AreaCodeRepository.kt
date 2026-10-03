package ar.com.mandados.app

import android.content.Context

object AreaCodeRepository {
    fun load(context: Context): Map<String, AreaCode> {
        val result = linkedMapOf<String, AreaCode>()
        context.resources.openRawResource(R.raw.area_codes).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.drop(1).forEach { line ->
                val p = line.split('|')
                if (p.size >= 4) {
                    val item = AreaCode(p[0], p[1], p[2].toInt(), p[3].toInt())
                    result[item.code] = item
                }
            }
        }
        return result
    }
}
