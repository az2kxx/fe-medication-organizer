package com.elcomp.medicationorganizer.core.demo

import com.elcomp.medicationorganizer.core.network.apiJson
import com.elcomp.medicationorganizer.features.organizers.data.dto.OrganizerModelDetails
import com.elcomp.medicationorganizer.features.organizers.data.dto.toSummary
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.encodedPath
import io.ktor.http.headersOf
import kotlin.time.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonPrimitive

/** DemoSession serializes all calls to the routes and reset. */
internal class DemoRoutes(private val empty: Boolean = false) {
    private var state = DemoState(empty)

    suspend fun reset() {
        state = DemoState(empty)
    }

    suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        val segments = path.split('/')
        val ids = segments.mapNotNull(String::toLongOrNull)
        val route = segments.joinToString("/") { if (it.toLongOrNull() == null) it else "{id}" }
        val query = request.url.parameters

        fun id(index: Int = 0): Long = ids.getOrNull(index) ?: missing("Unknown demo resource")
        fun date(name: String): String = requireNotNull(query[name]) { "Missing $name" }
        fun organizerId(): Long = requireNotNull(query["organizerId"]?.toLongOrNull()) { "Invalid organizerId" }

        return try {
            when ("${request.method.value} $route") {
                "GET /api/organizer-models" -> json(
                    (query["productCode"]?.let(DemoCatalog::findByProductCode) ?: DemoCatalog.models)
                        .map(OrganizerModelDetails::toSummary),
                )
                "GET /api/organizer-models/{id}" -> json(DemoCatalog.model(id()) ?: missing("Unknown demo model"))
                "GET /api/medications" -> json(state.medications())
                "POST /api/medications" -> json(state.createMedication(request.decode()), HttpStatusCode.Created)
                "PATCH /api/medications/{id}" -> json(state.updateMedication(id(), request.decode()))
                "PATCH /api/medications/{id}/status" -> json(state.updateMedicationStatus(id(), request.decode()))
                "PUT /api/medications/{id}/schedule" -> json(state.replaceSchedule(id(), request.decode()))
                "GET /api/plan/daily" -> json(state.dailyPlan(organizerId(), date("date")))
                "GET /api/plan/weekly" -> json(state.weeklyPlan(organizerId(), date("weekStart")))
                "POST /api/users/me/organizers/{id}/preparations" ->
                    json(state.startPreparation(id(), request.decode()), HttpStatusCode.Created)
                "GET /api/users/me/organizers/{id}/preparations/current" -> json(state.currentPreparation(id()))
                "PATCH /api/preparations/{id}/compartments/{id}" ->
                    json(state.markCompartment(id(), id(1), request.decode()))
                "POST /api/preparations/{id}/complete" -> json(state.completePreparation(id()))
                "POST /api/dose-occurrences/{id}/intake" ->
                    json(state.recordIntake(id(), request.decode()), HttpStatusCode.Created)
                "GET /api/intake" -> json(state.intakeHistory(date("from"), date("to")))
                else -> missing("Unknown demo route")
            }
        } catch (error: DemoFailure) {
            respond(error.message.orEmpty(), error.status)
        } catch (_: SerializationException) {
            respond("Invalid demo request body", HttpStatusCode.BadRequest)
        } catch (_: IllegalArgumentException) {
            respond("Invalid demo request", HttpStatusCode.BadRequest)
        }
    }
}

private suspend inline fun <reified Request> HttpRequestData.decode(): Request =
    apiJson.decodeFromString(body.toByteArray().decodeToString())

private inline fun <reified Response> MockRequestHandleScope.json(
    response: Response,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    apiJson.encodeToString(response),
    status,
    headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

private class DemoFailure(val status: HttpStatusCode, message: String) : Exception(message)

private fun missing(message: String): Nothing = throw DemoFailure(HttpStatusCode.NotFound, message)

private fun conflict(message: String): Nothing = throw DemoFailure(HttpStatusCode.Conflict, message)

private val demoNow = Instant.parse("${DEMO_DATE}T09:00:00Z")
private val dateFormat = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")
private val timeFormat = Regex("([01][0-9]|2[0-3]):[0-5][0-9]")
private val decimalFormat = Regex("[0-9]+(\\.[0-9]+)?")
private const val secondsPerDay = 86_400L

private fun dayOf(date: String): Long {
    require(dateFormat.matches(date))
    return Instant.parse("${date}T00:00:00Z").epochSeconds / secondsPerDay
}

private fun dateOf(day: Long): String = Instant.fromEpochSeconds(day * secondsPerDay).toString().substringBefore('T')

private fun isoWeekday(day: Long): Int = (day + 3).mod(7L).toInt() + 1

private fun JsonPrimitive.isPositiveDecimal(): Boolean =
    !isString && decimalFormat.matches(content) && content.any { it in '1'..'9' }

private class DemoState(empty: Boolean) {
    private data class StoredMedication(val summary: Medication, val request: CreateMedication)
    private data class DoseKey(val medicationId: Long, val date: String, val time: String)

    private var nextId = 1L
    private val medications = linkedMapOf<Long, StoredMedication>()
    private val schedules = mutableMapOf<Long, MutableList<MedicationSchedule>>()
    private val occurrences = linkedMapOf<DoseKey, DoseOccurrence>()
    private val intake = mutableListOf<IntakeRecord>()
    private var preparation: Preparation? = null

    private val model = DemoCatalog.demoModel

    init {
        if (!empty) {
            val medication = createMedication(CreateMedication("Lek demonstracyjny", JsonPrimitive(1), "TABLET"))
            replaceSchedule(
                medication.id,
                ReplaceSchedule(DEMO_DATE, setOf(1), listOf(ScheduleTime("08:00", TimeOfDay.MORNING))),
            )
        }
    }

    fun medications(): List<Medication> = medications.values.map(StoredMedication::summary)

    fun createMedication(request: CreateMedication): Medication {
        val fields = validateMedication(request)
        val medication = Medication(nextId++, fields.name, MedicationStatus.ACTIVE)
        medications[medication.id] = StoredMedication(medication, fields)
        return medication
    }

    fun updateMedication(id: Long, request: UpdateMedication): Medication {
        val current = medications[id] ?: missing("Unknown demo medication")
        val fields = validateMedication(
            CreateMedication(
                name = request.name,
                doseAmount = request.doseAmount,
                doseUnit = request.doseUnit,
                startDate = request.startDate,
                endDate = request.endDate,
                strengthValue = request.strengthValue,
                strengthUnit = request.strengthUnit,
                form = request.form,
            ),
        )
        if (current.request == fields) return current.summary

        val updated = current.summary.copy(name = fields.name)
        medications[id] = StoredMedication(updated, fields)
        removePendingDoses(id, DEMO_DATE)
        return updated
    }

    fun updateMedicationStatus(id: Long, request: UpdateMedicationStatus): Medication {
        val current = medications[id] ?: missing("Unknown demo medication")
        if (current.summary.status == request.status) return current.summary

        val updated = current.summary.copy(status = request.status)
        medications[id] = current.copy(summary = updated)
        if (request.status != MedicationStatus.ACTIVE) removePendingDoses(id, DEMO_DATE)
        return updated
    }

    private fun validateMedication(request: CreateMedication): CreateMedication {
        require(request.name.isNotBlank() && request.doseUnit.isNotBlank())
        require(request.doseAmount.isPositiveDecimal())
        require(request.strengthValue?.isPositiveDecimal() != false)
        val start = dayOf(request.startDate)
        require(request.endDate?.let { dayOf(it) >= start } != false)
        return request.copy(name = request.name.trim())
    }

    fun replaceSchedule(medicationId: Long, request: ReplaceSchedule): MedicationSchedule {
        if (medicationId !in medications) missing("Unknown demo medication")
        val start = dayOf(request.validFrom)
        require(request.validUntil?.let { dayOf(it) >= start } != false)
        require(request.days.isNotEmpty() && request.days.all { it in 1..7 })
        require(request.times.isNotEmpty() && request.times.all { timeFormat.matches(it.time) })
        require(request.times.map(ScheduleTime::time).toSet().size == request.times.size)

        val versions = schedules.getOrPut(medicationId) { mutableListOf() }
        val latest = versions.lastOrNull()
        if (latest != null && latest.validFrom == request.validFrom && latest.validUntil == request.validUntil &&
            latest.days == request.days && latest.times == request.times
        ) {
            return latest
        }
        versions.removeAll { it.validFrom >= request.validFrom }
        versions.lastOrNull()?.let { previous ->
            if (previous.validUntil == null || previous.validUntil >= request.validFrom) {
                versions[versions.lastIndex] = previous.copy(validUntil = dateOf(start - 1))
            }
        }
        removePendingDoses(medicationId, request.validFrom)

        return MedicationSchedule(
            nextId++, medicationId, request.validFrom, request.days, request.times, request.validUntil,
        ).also(versions::add)
    }

    private fun removePendingDoses(medicationId: Long, from: String) {
        occurrences.entries.removeAll { (key, dose) ->
            key.medicationId == medicationId && key.date >= from && dose.status == DoseStatus.PENDING
        }
    }

    fun dailyPlan(organizerId: Long, date: String): List<DoseOccurrence> {
        requireOrganizer(organizerId)
        val day = dayOf(date)
        return plan(day, day)
    }

    fun weeklyPlan(organizerId: Long, weekStart: String): List<DoseOccurrence> {
        requireOrganizer(organizerId)
        val first = dayOf(weekStart)
        return plan(first, first + 6)
    }

    fun startPreparation(organizerId: Long, request: StartPreparation): Preparation {
        requireOrganizer(organizerId)
        val first = dayOf(request.periodStart)
        val last = dayOf(request.periodEnd)
        require(last in first..(first + 6))
        if (preparation?.status == PreparationStatus.IN_PROGRESS) conflict("Preparation already in progress")

        val days = (first..last).map(::isoWeekday).toSet()
        val sessionId = nextId++
        return Preparation(
            sessionId, organizerId, request.periodStart, request.periodEnd, PreparationStatus.IN_PROGRESS,
            model.compartments.filter { it.dayOfWeek in days }.map { definition ->
                CompartmentProgress(nextId++, definition.id, CompartmentStatus.UNCHECKED)
            },
        ).also { preparation = it }
    }

    fun currentPreparation(organizerId: Long): Preparation {
        requireOrganizer(organizerId)
        return preparation ?: missing("No demo preparation")
    }

    fun markCompartment(preparationId: Long, compartmentId: Long, request: UpdateCompartment): CompartmentProgress {
        val session = inProgress(preparationId)
        val index = session.compartments.indexOfFirst { it.compartmentDefinitionId == compartmentId }
        if (index == -1) missing("Unknown compartment for this session")
        val hasDoses = plan(dayOf(session.periodStart), dayOf(session.periodEnd))
            .any { it.compartmentDefinitionId == compartmentId }
        when (request.status) {
            CompartmentStatus.PREPARED -> if (!hasDoses) conflict("Compartment has no planned doses")
            CompartmentStatus.INTENTIONALLY_EMPTY -> if (hasDoses) conflict("Compartment has planned doses")
            CompartmentStatus.UNCHECKED -> Unit
        }
        val progress = session.compartments[index].copy(
            status = request.status,
            checkedAt = demoNow.takeUnless { request.status == CompartmentStatus.UNCHECKED },
            note = request.note,
        )
        val compartments = session.compartments.toMutableList()
        compartments[index] = progress
        preparation = session.copy(compartments = compartments)
        return progress
    }

    fun completePreparation(id: Long): Preparation {
        val session = inProgress(id)
        val plannedCompartments = plan(dayOf(session.periodStart), dayOf(session.periodEnd))
            .mapNotNull(DoseOccurrence::compartmentDefinitionId).toSet()
        if (session.compartments.any { compartment ->
                val expected = if (compartment.compartmentDefinitionId in plannedCompartments) {
                    CompartmentStatus.PREPARED
                } else {
                    CompartmentStatus.INTENTIONALLY_EMPTY
                }
                compartment.status != expected
            }
        ) {
            conflict("Check every compartment against the current plan before completing preparation")
        }
        return session.copy(status = PreparationStatus.READY, completedAt = demoNow).also { preparation = it }
    }

    fun recordIntake(id: Long, request: RecordIntake): IntakeRecord {
        val (key, dose) = occurrences.entries.firstOrNull { it.value.id == id } ?: missing("Unknown demo dose")
        if (dose.status != DoseStatus.PENDING) conflict("Demo dose is not pending")
        val status = when (request.status) {
            IntakeStatus.TAKEN -> DoseStatus.TAKEN
            IntakeStatus.SKIPPED -> DoseStatus.SKIPPED
        }
        occurrences[key] = dose.copy(status = status)
        return IntakeRecord(nextId++, id, request.status, request.occurredAt, demoNow, request.note).also(intake::add)
    }

    fun intakeHistory(from: String, to: String): List<IntakeRecord> {
        require(dayOf(to) >= dayOf(from))
        val doseIds = occurrences.values.filter { it.scheduledDate in from..to }.map(DoseOccurrence::id).toSet()
        return intake.filter { it.doseOccurrenceId in doseIds }
    }

    private fun plan(first: Long, last: Long): List<DoseOccurrence> {
        for (day in first..last) {
            val date = dateOf(day)
            val weekday = isoWeekday(day)
            for (medication in medications.values) {
                val summary = medication.summary
                if (summary.status != MedicationStatus.ACTIVE) continue
                val request = medication.request
                if (date < request.startDate || (request.endDate != null && date > request.endDate)) continue
                val schedule = schedules[summary.id]?.lastOrNull {
                    date >= it.validFrom && (it.validUntil == null || date <= it.validUntil)
                } ?: continue
                if (weekday !in schedule.days) continue

                for (time in schedule.times) {
                    val key = DoseKey(summary.id, date, time.time)
                    occurrences.getOrPut(key) {
                        val compartment = model.compartments.firstOrNull {
                            it.dayOfWeek == weekday && it.timeOfDay == time.timeOfDay
                        }
                        DoseOccurrence(
                            nextId++, summary.id, summary.name, DEMO_ORGANIZER_ID, compartment?.id, date,
                            time.time, request.doseAmount, request.doseUnit, DoseStatus.PENDING,
                        )
                    }
                }
            }
        }
        val dates = dateOf(first)..dateOf(last)
        return occurrences.values.filter { it.scheduledDate in dates }
            .sortedWith(compareBy(DoseOccurrence::scheduledDate, DoseOccurrence::scheduledTime, DoseOccurrence::id))
    }

    private fun inProgress(id: Long): Preparation {
        val session = preparation?.takeIf { it.id == id } ?: missing("Unknown demo preparation")
        if (session.status != PreparationStatus.IN_PROGRESS) conflict("Demo preparation is not in progress")
        return session
    }

    private fun requireOrganizer(id: Long) {
        if (id != DEMO_ORGANIZER_ID) missing("Unknown demo organizer assignment")
    }
}
