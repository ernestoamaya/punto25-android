package ar.com.mandados.app

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object PdfReports {
    private val stamp = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

    fun shareOrders(
        context: Context,
        title: String,
        from: String,
        to: String,
        orders: List<LocalOrder>,
        controller: MandadosController,
        extraSummary: List<Pair<String, String>> = emptyList()
    ): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safe = title.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "reporte" }
            val file = File(dir, "${safe}_${System.currentTimeMillis()}.pdf")
            val pdf = PdfDocument()

            val pageWidth = 595
            val pageHeight = 842
            val margin = 42f
            val body = Paint().apply { textSize = 10.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
            val bold = Paint(body).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            val titlePaint = Paint(bold).apply { textSize = 18f }
            val small = Paint(body).apply { textSize = 8.5f }

            var pageNo = 0
            var page: PdfDocument.Page? = null
            var y = 0f

            fun newPage() {
                page?.let { pdf.finishPage(it) }
                pageNo++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
                val canvas = page!!.canvas
                y = margin
                canvas.drawText(title, margin, y, titlePaint)
                y += 22f
                canvas.drawText("Período: $from a $to", margin, y, body)
                y += 15f
                canvas.drawText("Generado: ${LocalDateTime.now().format(stamp)}", margin, y, small)
                y += 20f
            }

            fun line(text: String, paint: Paint = body, indent: Float = 0f) {
                if (page == null || y > pageHeight - 60f) newPage()
                val canvas = page!!.canvas
                val maxChars = if (indent > 0) 82 else 88
                val chunks = wrap(text, maxChars)
                chunks.forEach { part ->
                    if (y > pageHeight - 60f) newPage()
                    page!!.canvas.drawText(part, margin + indent, y, paint)
                    y += 13f
                }
            }

            newPage()
            extraSummary.forEach { (label, value) -> line("$label: $value", bold) }
            if (extraSummary.isNotEmpty()) y += 8f
            line("Pedidos incluidos: ${orders.size}", bold)
            y += 8f

            orders.forEachIndexed { index, order ->
                line("${index + 1}. ${order.id} · ${order.createdAt}", bold)
                line("Estado: ${statusText(order.status)} · Tipo: ${categoryText(order.category)}", body, 8f)
                line("Cliente: ${order.customerName} · ${order.customerPhone}", body, 8f)
                val rider = controller.rider(order.assignedRiderId)
                line("Repartidor: ${rider?.let { "${it.name} · ${it.id}" } ?: order.assignedRiderId ?: "Sin asignar"}", body, 8f)
                line("Total servicio: ${money(order.totalAmount)}", body, 8f)
                controller.deliveryDurationSeconds(order)?.let { line("Tiempo de entrega: ${formatDuration(it)}", body, 8f) }
                y += 6f
            }

            page?.let { pdf.finishPage(it) }
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Guardar o compartir PDF"))
            true
        }.getOrDefault(false)
    }

    fun shareLines(
        context: Context,
        title: String,
        from: String,
        to: String,
        summary: List<Pair<String, String>>,
        lines: List<String>
    ): Boolean {
        return runCatching {
            val dir = File(context.cacheDir, "reports").apply { mkdirs() }
            val safe = title.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "reporte" }
            val file = File(dir, "${safe}_${System.currentTimeMillis()}.pdf")
            val pdf = PdfDocument()
            val pageWidth = 595
            val pageHeight = 842
            val margin = 42f
            val body = Paint().apply { textSize = 10.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL) }
            val bold = Paint(body).apply { typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            val titlePaint = Paint(bold).apply { textSize = 18f }
            val small = Paint(body).apply { textSize = 8.5f }
            var pageNo = 0
            var page: PdfDocument.Page? = null
            var y = 0f

            fun newPage() {
                page?.let { pdf.finishPage(it) }
                pageNo++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
                y = margin
                page!!.canvas.drawText(title, margin, y, titlePaint)
                y += 22f
                page!!.canvas.drawText("Período: $from a $to", margin, y, body)
                y += 15f
                page!!.canvas.drawText("Generado: ${LocalDateTime.now().format(stamp)}", margin, y, small)
                y += 20f
            }

            fun line(text: String, paint: Paint = body) {
                if (page == null || y > pageHeight - 60f) newPage()
                wrap(text, 88).forEach { part ->
                    if (y > pageHeight - 60f) newPage()
                    page!!.canvas.drawText(part, margin, y, paint)
                    y += 13f
                }
            }

            newPage()
            summary.forEach { (label, value) -> line("$label: $value", bold) }
            if (summary.isNotEmpty()) y += 8f
            lines.forEach { line(it) }
            page?.let { pdf.finishPage(it) }
            FileOutputStream(file).use { pdf.writeTo(it) }
            pdf.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Guardar o compartir PDF"))
            true
        }.getOrDefault(false)
    }

    private fun wrap(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val words = text.split(" ")
        val out = mutableListOf<String>()
        var current = StringBuilder()
        words.forEach { word ->
            if (current.isNotEmpty() && current.length + word.length + 1 > maxChars) {
                out += current.toString()
                current = StringBuilder()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out.ifEmpty { listOf(text.take(maxChars)) }
    }

    private fun statusText(v: OrderStatus) = when (v) {
        OrderStatus.AWAITING_QUOTE -> "A confirmar"
        OrderStatus.PENDING -> "Nuevo"
        OrderStatus.ACCEPTED -> "Aceptado"
        OrderStatus.IN_PROGRESS -> "En curso"
        OrderStatus.COMPLETED -> "Completado"
        OrderStatus.REJECTED -> "Rechazado"
        OrderStatus.CANCELLED -> "Cancelado"
    }

    private fun categoryText(v: ServiceCategory) = when (v) {
        ServiceCategory.PURCHASE -> "Compras"
        ServiceCategory.ERRAND -> "Encargos"
        ServiceCategory.PROCEDURE -> "Trámites"
        ServiceCategory.SHIPMENT -> "Envíos"
    }

    private fun money(v: Int?): String = if (v == null) "A confirmar" else "$" + "%,d".format(v).replace(',', '.')

    private fun formatDuration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return "%02d:%02d:%02d".format(h, m, sec)
    }
}
