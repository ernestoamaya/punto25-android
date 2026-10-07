package ar.com.mandados.app

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.UUID

/** A weekly rule is configuration only; it is never reservable. */
data class ShiftGenerationRule(
    val id: String,
    val daysOfWeek: Set<Int>,
    val startMinute: Int,
    val endMinute: Int,
    val capacity: Int
)

/** A reservable shift with an immutable operational date (the day on which it starts). */
data class ConcreteShift(
    val id: String,
    val serviceDate: String,
    val startMinute: Int,
    val endMinute: Int,
    val capacity: Int,
    val enabled: Boolean = true,
    val originRuleId: String? = null,
    val isException: Boolean = false
)

data class ConcreteShiftReservation(
    val id: String,
    val riderId: String,
    val concreteShiftId: String,
    val joinedAt: String,
    val status: ShiftReservationStatus = ShiftReservationStatus.RESERVED,
    val cancelledAt: String? = null,
    val cancellationCount: Int = 0,
    val lastCancelledAt: String? = null,
    val blockedRejoin: Boolean = false
)

data class ConcreteShiftAuditEvent(
    val id: String,
    val reservationId: String,
    val concreteShiftId: String,
    val riderId: String,
    val serviceDate: String,
    val type: ShiftEventType,
    val at: String,
    val actor: String
)

data class ShiftGenerationRequest(
    val fromDate: String,
    val toDate: String,
    val fromRuleId: String? = null,
    val toRuleId: String? = null
)

data class ShiftGenerationPreview(
    val request: ShiftGenerationRequest,
    val candidateCount: Int,
    val newShifts: List<ConcreteShift>,
    val materializedCount: Int,
    val exceptionCount: Int,
    val conflicts: List<String> = emptyList(),
    val error: String? = null
) {
    val canConfirm: Boolean get() = error == null && conflicts.isEmpty()
    val newCount: Int get() = newShifts.size
}

object ShiftSchedulePolicy {
    private const val MINUTES_PER_DAY = 1_440
    private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

    val isoDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ISO_LOCAL_DATE.withResolverStyle(ResolverStyle.STRICT)
    val displayDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)

    fun parseIsoDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim(), isoDateFormatter) }.getOrNull()

    fun parseDisplayDate(text: String): LocalDate? =
        runCatching { LocalDate.parse(text.trim(), displayDateFormatter) }.getOrNull()

    fun toIsoDate(date: LocalDate): String = date.format(isoDateFormatter)
    fun toDisplayDate(date: LocalDate): String = date.format(displayDateFormatter)
    fun displayDate(isoDate: String): String? = parseIsoDate(isoDate)?.let(::toDisplayDate)

    fun parseClock(raw: String, allow24: Boolean): Int? {
        val clean = raw.trim()
        val digits = clean.filter(Char::isDigit)
        if (digits.length != 4) return null
        val hour = digits.substring(0, 2).toIntOrNull() ?: return null
        val minute = digits.substring(2, 4).toIntOrNull() ?: return null
        if (minute !in 0..59) return null
        if (hour == 24) return if (allow24 && minute == 0) MINUTES_PER_DAY else null
        if (hour !in 0..23) return null
        return hour * 60 + minute
    }

    fun formatMinute(minute: Int): String {
        if (minute == MINUTES_PER_DAY) return "24:00"
        require(minute in 0 until MINUTES_PER_DAY)
        return "%02d:%02d".format(minute / 60, minute % 60)
    }

    fun validateWindow(startMinute: Int, endMinute: Int): String? = when {
        startMinute !in 0 until MINUTES_PER_DAY -> "Horario de inicio inválido."
        endMinute !in 0..MINUTES_PER_DAY -> "Horario de fin inválido."
        startMinute == endMinute -> "El inicio y el fin no pueden ser iguales."
        else -> null
    }

    fun window(serviceDate: String, startMinute: Int, endMinute: Int): Pair<LocalDateTime, LocalDateTime>? {
        val date = parseIsoDate(serviceDate) ?: return null
        if (validateWindow(startMinute, endMinute) != null) return null
        val start = date.atStartOfDay().plusMinutes(startMinute.toLong())
        val end = when {
            endMinute == MINUTES_PER_DAY -> date.plusDays(1).atStartOfDay()
            endMinute <= startMinute -> date.plusDays(1).atStartOfDay().plusMinutes(endMinute.toLong())
            else -> date.atStartOfDay().plusMinutes(endMinute.toLong())
        }
        return start to end
    }

    fun window(shift: ConcreteShift): Pair<LocalDateTime, LocalDateTime>? =
        window(shift.serviceDate, shift.startMinute, shift.endMinute)

    fun overlaps(a: ConcreteShift, b: ConcreteShift): Boolean {
        val aw = window(a) ?: return true
        val bw = window(b) ?: return true
        return aw.first.isBefore(bw.second) && bw.first.isBefore(aw.second)
    }

    fun validateRule(rule: ShiftGenerationRule): String? = when {
        rule.id.isBlank() -> "La regla no tiene identidad válida."
        rule.daysOfWeek.isEmpty() -> "Seleccioná al menos un día."
        rule.daysOfWeek.any { it !in 1..7 } -> "La regla contiene un día inválido."
        rule.capacity < 1 -> "El cupo debe ser mayor a cero."
        else -> validateWindow(rule.startMinute, rule.endMinute)
    }

    private fun weeklyIntervals(rule: ShiftGenerationRule): List<Pair<Int, Int>> =
        rule.daysOfWeek.sorted().map { day ->
            val base = (day - 1) * MINUTES_PER_DAY
            val start = base + rule.startMinute
            val end = when {
                rule.endMinute == MINUTES_PER_DAY -> base + MINUTES_PER_DAY
                rule.endMinute <= rule.startMinute -> base + MINUTES_PER_DAY + rule.endMinute
                else -> base + rule.endMinute
            }
            start to end
        }

    fun rulesOverlap(a: ShiftGenerationRule, b: ShiftGenerationRule): Boolean {
        if (validateRule(a) != null || validateRule(b) != null) return true
        val ai = weeklyIntervals(a)
        val bi = weeklyIntervals(b)
        return ai.any { x ->
            bi.any { y ->
                listOf(-MINUTES_PER_WEEK, 0, MINUTES_PER_WEEK).any { offset ->
                    val ys = y.first + offset
                    val ye = y.second + offset
                    x.first < ye && ys < x.second
                }
            }
        }
    }

    fun firstRuleConflict(rules: List<ShiftGenerationRule>, candidate: ShiftGenerationRule): ShiftGenerationRule? =
        rules.firstOrNull { it.id != candidate.id && rulesOverlap(it, candidate) }

    fun validateConcrete(shift: ConcreteShift): String? = when {
        shift.id.isBlank() -> "Hay un turno sin identidad válida."
        parseIsoDate(shift.serviceDate) == null -> "Hay un turno con fecha inválida."
        shift.capacity < 1 -> "Hay un turno con cupo inválido."
        shift.originRuleId?.isBlank() == true -> "Hay un turno con origen inválido."
        else -> validateWindow(shift.startMinute, shift.endMinute)
    }

    fun applicableRules(date: LocalDate, rules: List<ShiftGenerationRule>): List<ShiftGenerationRule> =
        rules.filter { date.dayOfWeek.value in it.daysOfWeek }
            .sortedWith(compareBy<ShiftGenerationRule> { it.startMinute }.thenBy { effectiveEndSort(it) }.thenBy { it.id })

    private fun effectiveEndSort(rule: ShiftGenerationRule): Int = when {
        rule.endMinute == MINUTES_PER_DAY -> MINUTES_PER_DAY
        rule.endMinute <= rule.startMinute -> MINUTES_PER_DAY + rule.endMinute
        else -> rule.endMinute
    }

    fun buildGenerationPreview(
        request: ShiftGenerationRequest,
        rules: List<ShiftGenerationRule>,
        existing: List<ConcreteShift>,
        idFactory: () -> String = { "CS-${UUID.randomUUID()}" }
    ): ShiftGenerationPreview {
        val from = parseIsoDate(request.fromDate)
            ?: return invalidPreview(request, "Fecha inicial inválida.")
        val to = parseIsoDate(request.toDate)
            ?: return invalidPreview(request, "Fecha final inválida.")
        if (to.isBefore(from)) return invalidPreview(request, "La fecha final no puede ser anterior a la inicial.")

        rules.forEach { rule ->
            validateRule(rule)?.let { return invalidPreview(request, "Regla inválida ${rule.id}: $it") }
        }
        for (i in rules.indices) {
            for (j in i + 1 until rules.size) {
                if (rulesOverlap(rules[i], rules[j])) {
                    return invalidPreview(request, "La configuración contiene reglas superpuestas.")
                }
            }
        }

        existing.forEach { shift ->
            validateConcrete(shift)?.let { return invalidPreview(request, it) }
        }
        val duplicateLineage = existing.filter { it.originRuleId != null }
            .groupBy { it.originRuleId!! to it.serviceDate }
            .entries.firstOrNull { it.value.size > 1 }
        if (duplicateLineage != null) {
            return invalidPreview(request, "Hay turnos duplicados para una misma regla y fecha. Corregilos antes de generar.")
        }

        val selected = mutableListOf<Pair<LocalDate, ShiftGenerationRule>>()
        var date = from
        while (!date.isAfter(to)) {
            var dayRules = applicableRules(date, rules)
            if (date == from && request.fromRuleId != null) {
                val index = dayRules.indexOfFirst { it.id == request.fromRuleId }
                if (index < 0) return invalidPreview(request, "El turno inicial seleccionado no aplica a la fecha inicial.")
                dayRules = dayRules.drop(index)
            }
            if (date == to && request.toRuleId != null) {
                val index = dayRules.indexOfFirst { it.id == request.toRuleId }
                if (index < 0) return invalidPreview(request, "El turno final seleccionado no aplica a la fecha final.")
                dayRules = dayRules.take(index + 1)
            }
            selected += dayRules.map { date to it }
            date = date.plusDays(1)
        }

        if (from == to && request.fromRuleId != null && request.toRuleId != null) {
            val all = applicableRules(from, rules)
            val fromIndex = all.indexOfFirst { it.id == request.fromRuleId }
            val toIndex = all.indexOfFirst { it.id == request.toRuleId }
            if (fromIndex < 0 || toIndex < 0 || fromIndex > toIndex) {
                return invalidPreview(request, "El límite inicial no puede quedar después del límite final.")
            }
        }

        var materialized = 0
        var exceptions = 0
        val newShifts = mutableListOf<ConcreteShift>()
        selected.forEach { (candidateDate, rule) ->
            val iso = toIsoDate(candidateDate)
            val prior = existing.firstOrNull { it.originRuleId == rule.id && it.serviceDate == iso }
            if (prior != null) {
                materialized++
                if (prior.isException || !prior.enabled) exceptions++
            } else {
                newShifts += ConcreteShift(
                    id = idFactory(),
                    serviceDate = iso,
                    startMinute = rule.startMinute,
                    endMinute = rule.endMinute,
                    capacity = rule.capacity,
                    enabled = true,
                    originRuleId = rule.id,
                    isException = false
                )
            }
        }

        val conflicts = mutableListOf<String>()
        for (i in newShifts.indices) {
            for (j in i + 1 until newShifts.size) {
                if (overlaps(newShifts[i], newShifts[j])) {
                    conflicts += conflictText(newShifts[i], newShifts[j])
                }
            }
            existing.firstOrNull { other ->
                val sameLineage = other.originRuleId == newShifts[i].originRuleId && other.serviceDate == newShifts[i].serviceDate
                !sameLineage && overlaps(other, newShifts[i])
            }?.let { conflicts += conflictText(newShifts[i], it) }
        }

        return ShiftGenerationPreview(
            request = request,
            candidateCount = selected.size,
            newShifts = newShifts,
            materializedCount = materialized,
            exceptionCount = exceptions,
            conflicts = conflicts.distinct()
        )
    }

    private fun invalidPreview(request: ShiftGenerationRequest, message: String) =
        ShiftGenerationPreview(request, 0, emptyList(), 0, 0, error = message)

    private fun conflictText(a: ConcreteShift, b: ConcreteShift): String {
        val aDate = displayDate(a.serviceDate) ?: a.serviceDate
        val bDate = displayDate(b.serviceDate) ?: b.serviceDate
        return "$aDate ${formatMinute(a.startMinute)}–${formatMinute(a.endMinute)} se superpone con " +
            "$bDate ${formatMinute(b.startMinute)}–${formatMinute(b.endMinute)}."
    }

    fun epochMillisUtc(date: LocalDate): Long = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
}
