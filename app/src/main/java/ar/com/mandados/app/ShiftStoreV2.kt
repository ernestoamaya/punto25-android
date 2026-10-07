package ar.com.mandados.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal data class ShiftStoreInitResult(val success: Boolean, val initializedNow: Boolean)
internal data class ShiftStoreSnapshot(
    val rules: List<ShiftGenerationRule>,
    val shifts: List<ConcreteShift>,
    val reservations: List<ConcreteShiftReservation>,
    val audit: List<ConcreteShiftAuditEvent>,
    val healthy: Boolean
)

/**
 * Explicit v2 storage for the Alpha shift subsystem. Legacy shift keys are never read.
 *
 * Every critical write uses commit() and a persisted revision. The process-wide lock prevents
 * same-process interleaving; the revision prevents a stale controller/repository instance from
 * overwriting a newer snapshot. On a stale write we fail closed and leave persistence untouched.
 */
internal class ShiftStoreV2(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var observedRevision: Long = prefs.getLong(KEY_REVISION, 0L)

    fun initializeIfNeeded(): ShiftStoreInitResult = synchronized(PROCESS_LOCK) {
        if (prefs.getBoolean(KEY_INITIALIZED, false)) {
            observedRevision = prefs.getLong(KEY_REVISION, 0L)
            return@synchronized ShiftStoreInitResult(true, false)
        }

        val reconciledRiders = runCatching {
            val raw = prefs.getString(KEY_RIDERS, "[]") ?: "[]"
            val source = JSONArray(raw)
            JSONArray().apply {
                for (i in 0 until source.length()) {
                    val rider = JSONObject(source.getJSONObject(i).toString())
                    rider.put("available", false)
                    rider.put("availableUntilAt", JSONObject.NULL)
                    put(rider)
                }
            }.toString()
        }.getOrElse { return@synchronized ShiftStoreInitResult(false, false) }

        val ok = prefs.edit()
            .putString(KEY_RULES, "[]")
            .putString(KEY_SHIFTS, "[]")
            .putString(KEY_RESERVATIONS, "[]")
            .putString(KEY_AUDIT, "[]")
            .putString(KEY_RIDERS, reconciledRiders)
            .remove(LEGACY_SHIFTS)
            .remove(LEGACY_RESERVATIONS)
            .remove(LEGACY_AUDIT)
            .putLong(KEY_REVISION, 0L)
            .putBoolean(KEY_INITIALIZED, true)
            .commit()
        if (ok) observedRevision = 0L
        ShiftStoreInitResult(ok, ok)
    }

    fun loadSnapshot(): ShiftStoreSnapshot = synchronized(PROCESS_LOCK) {
        val snapshot = runCatching {
            ShiftStoreSnapshot(
                rules = decodeRules(prefs.getString(KEY_RULES, "[]") ?: "[]"),
                shifts = decodeShifts(prefs.getString(KEY_SHIFTS, "[]") ?: "[]"),
                reservations = decodeReservations(prefs.getString(KEY_RESERVATIONS, "[]") ?: "[]"),
                audit = decodeAudit(prefs.getString(KEY_AUDIT, "[]") ?: "[]"),
                healthy = true
            )
        }.getOrElse { ShiftStoreSnapshot(emptyList(), emptyList(), emptyList(), emptyList(), false) }
        if (snapshot.healthy) observedRevision = prefs.getLong(KEY_REVISION, 0L)
        snapshot
    }

    fun saveRules(rules: List<ShiftGenerationRule>): Boolean =
        commitRevisioned { editor -> editor.putString(KEY_RULES, encodeRules(rules)) }

    fun saveConcreteShifts(shifts: List<ConcreteShift>): Boolean =
        commitRevisioned { editor -> editor.putString(KEY_SHIFTS, encodeShifts(shifts)) }

    fun saveReservationsAndAudit(
        reservations: List<ConcreteShiftReservation>,
        audit: List<ConcreteShiftAuditEvent>
    ): Boolean = commitRevisioned { editor ->
        editor.putString(KEY_RESERVATIONS, encodeReservations(reservations))
        editor.putString(KEY_AUDIT, encodeAudit(audit))
    }

    fun saveAll(
        rules: List<ShiftGenerationRule>,
        shifts: List<ConcreteShift>,
        reservations: List<ConcreteShiftReservation>,
        audit: List<ConcreteShiftAuditEvent>
    ): Boolean = commitRevisioned { editor ->
        editor.putString(KEY_RULES, encodeRules(rules))
        editor.putString(KEY_SHIFTS, encodeShifts(shifts))
        editor.putString(KEY_RESERVATIONS, encodeReservations(reservations))
        editor.putString(KEY_AUDIT, encodeAudit(audit))
    }

    private inline fun commitRevisioned(
        crossinline changes: (android.content.SharedPreferences.Editor) -> android.content.SharedPreferences.Editor
    ): Boolean = synchronized(PROCESS_LOCK) {
        runCatching {
            val persistedRevision = prefs.getLong(KEY_REVISION, 0L)
            if (persistedRevision != observedRevision) return@synchronized false
            val nextRevision = persistedRevision + 1L
            val editor = changes(prefs.edit()).putLong(KEY_REVISION, nextRevision)
            val committed = editor.commit()
            if (committed) observedRevision = nextRevision
            committed
        }.getOrDefault(false)
    }

    private fun encodeRules(items: List<ShiftGenerationRule>) = JSONArray().apply {
        items.forEach { rule ->
            put(JSONObject().apply {
                put("id", rule.id)
                put("daysOfWeek", JSONArray(rule.daysOfWeek.sorted()))
                put("startMinute", rule.startMinute)
                put("endMinute", rule.endMinute)
                put("capacity", rule.capacity)
            })
        }
    }.toString()

    private fun decodeRules(raw: String): List<ShiftGenerationRule> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val daysArray = o.getJSONArray("daysOfWeek")
                val days = buildSet { for (j in 0 until daysArray.length()) add(daysArray.getInt(j)) }
                add(
                    ShiftGenerationRule(
                        id = o.getString("id"),
                        daysOfWeek = days,
                        startMinute = o.getInt("startMinute"),
                        endMinute = o.getInt("endMinute"),
                        capacity = o.getInt("capacity")
                    )
                )
            }
        }
    }

    private fun encodeShifts(items: List<ConcreteShift>) = JSONArray().apply {
        items.forEach { shift ->
            put(JSONObject().apply {
                put("id", shift.id)
                put("serviceDate", shift.serviceDate)
                put("startMinute", shift.startMinute)
                put("endMinute", shift.endMinute)
                put("capacity", shift.capacity)
                put("enabled", shift.enabled)
                put("originRuleId", shift.originRuleId ?: JSONObject.NULL)
                put("isException", shift.isException)
            })
        }
    }.toString()

    private fun decodeShifts(raw: String): List<ConcreteShift> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ConcreteShift(
                        id = o.getString("id"),
                        serviceDate = o.getString("serviceDate"),
                        startMinute = o.getInt("startMinute"),
                        endMinute = o.getInt("endMinute"),
                        capacity = o.getInt("capacity"),
                        enabled = o.optBoolean("enabled", true),
                        originRuleId = o.optStringOrNullV2("originRuleId"),
                        isException = o.optBoolean("isException", false)
                    )
                )
            }
        }
    }

    private fun encodeReservations(items: List<ConcreteShiftReservation>) = JSONArray().apply {
        items.forEach { r ->
            put(JSONObject().apply {
                put("id", r.id)
                put("riderId", r.riderId)
                put("concreteShiftId", r.concreteShiftId)
                put("joinedAt", r.joinedAt)
                put("status", r.status.name)
                put("cancelledAt", r.cancelledAt ?: JSONObject.NULL)
                put("cancellationCount", r.cancellationCount)
                put("lastCancelledAt", r.lastCancelledAt ?: JSONObject.NULL)
                put("blockedRejoin", r.blockedRejoin)
            })
        }
    }.toString()

    private fun decodeReservations(raw: String): List<ConcreteShiftReservation> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ConcreteShiftReservation(
                        id = o.getString("id"),
                        riderId = o.getString("riderId"),
                        concreteShiftId = o.getString("concreteShiftId"),
                        joinedAt = o.getString("joinedAt"),
                        status = enumValueOf(o.getString("status")),
                        cancelledAt = o.optStringOrNullV2("cancelledAt"),
                        cancellationCount = o.optInt("cancellationCount", 0),
                        lastCancelledAt = o.optStringOrNullV2("lastCancelledAt"),
                        blockedRejoin = o.optBoolean("blockedRejoin", false)
                    )
                )
            }
        }
    }

    private fun encodeAudit(items: List<ConcreteShiftAuditEvent>) = JSONArray().apply {
        items.forEach { event ->
            put(JSONObject().apply {
                put("id", event.id)
                put("reservationId", event.reservationId)
                put("concreteShiftId", event.concreteShiftId)
                put("riderId", event.riderId)
                put("serviceDate", event.serviceDate)
                put("type", event.type.name)
                put("at", event.at)
                put("actor", event.actor)
            })
        }
    }.toString()

    private fun decodeAudit(raw: String): List<ConcreteShiftAuditEvent> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(
                    ConcreteShiftAuditEvent(
                        id = o.getString("id"),
                        reservationId = o.getString("reservationId"),
                        concreteShiftId = o.getString("concreteShiftId"),
                        riderId = o.getString("riderId"),
                        serviceDate = o.getString("serviceDate"),
                        type = enumValueOf(o.getString("type")),
                        at = o.getString("at"),
                        actor = o.getString("actor")
                    )
                )
            }
        }
    }

    private fun JSONObject.optStringOrNullV2(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key, null)?.takeIf { it.isNotBlank() }

    companion object {
        private val PROCESS_LOCK = Any()

        private const val PREFS_NAME = "mandados_alpha1"
        private const val KEY_INITIALIZED = "shift_v2_initialized"
        private const val KEY_REVISION = "shift_v2_revision"
        private const val KEY_RULES = "shift_generation_rules_v2"
        private const val KEY_SHIFTS = "concrete_shifts_v2"
        private const val KEY_RESERVATIONS = "concrete_shift_reservations_v2"
        private const val KEY_AUDIT = "concrete_shift_audit_v2"
        private const val KEY_RIDERS = "riders"

        private const val LEGACY_SHIFTS = "shift_templates"
        private const val LEGACY_RESERVATIONS = "shift_reservations"
        private const val LEGACY_AUDIT = "shift_audit_events"
    }
}
