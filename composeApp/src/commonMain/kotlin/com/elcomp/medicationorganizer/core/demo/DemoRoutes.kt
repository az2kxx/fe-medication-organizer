package com.elcomp.medicationorganizer.core.demo

import com.elcomp.medicationorganizer.core.network.apiJson
import com.elcomp.medicationorganizer.features.organizers.data.AssignOrganizerRequest
import com.elcomp.medicationorganizer.features.organizers.data.UpdateOrganizerAssignment
import com.elcomp.medicationorganizer.features.organizers.data.UserOrganizer
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
                "GET /api/users/me/organizers" -> json(state.listAssignments())
                "GET /api/users/me/organizers/{id}" -> json(state.assignment(id()))
                "POST /api/users/me/organizers" -> json(state.assignOrganizer(request.decode()), HttpStatusCode.Created)
                "PATCH /api/users/me/organizers/{id}" -> json(state.updateAssignment(id(), request.decode()))
                "DELETE /api/users/me/organizers/{id}" -> {
                    state.removeAssignment(id())
                    respond("", HttpStatusCode.NoContent)
                }
                "GET /api/medications" -> json(state.medications())
                "POST /api/medications" -> json(state.createMedication(request.decode()), HttpStatusCode.Created)
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
private const val backupAssignmentId = 101L

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
    private var nextAssignmentId = backupAssignmentId + 1
    private val assignments = linkedMapOf(
        DEMO_ORGANIZER_ID to UserOrganizer(DEMO_ORGANIZER_ID, DEMO_MODEL_ID, "Moja kasetka", true, true, demoNow),
    )
    private val removedAssignments = mutableSetOf<Long>()
    private val medications = linkedMapOf<Long, StoredMedication>()
    private val medicationAssignments = mutableMapOf<Long, Long>()
    private val schedules = mutableMapOf<Long, MutableList<MedicationSchedule>>()
    private val occurrences = linkedMapOf<DoseKey, DoseOccurrence>()
    private val intake = mutableListOf<IntakeRecord>()
    private val preparations = mutableMapOf<Long, Preparation>()

    init {
        if (!empty) {
            assignments[backupAssignmentId] =
                UserOrganizer(backupAssignmentId, DEMO_MODEL_7X3_ID, "Kasetka zapasowa", true, false, demoNow)
            val medication = createMedication(CreateMedication("Lek demonstracyjny", JsonPrimitive(1), "TABLET"))
            replaceSchedule(
                medication.id,
                ReplaceSchedule(DEMO_DATE, setOf(1), listOf(ScheduleTime("08:00", TimeOfDay.MORNING))),
            )
        }
    }

    fun listAssignments(): List<UserOrganizer> = assignments.values.filter { it.id !in removedAssignments }

    fun assignment(id: Long): UserOrganizer =
        assignments[id]?.takeUnless { id in removedAssignments } ?: missing("Unknown demo organizer assignment")

    fun assignOrganizer(request: AssignOrganizerRequest): UserOrganizer {
        val model = DemoCatalog.model(request.organizerModelId) ?: missing("Unknown demo model")
        if (!model.active) conflict("Organizer model is withdrawn")
        val displayName = normalizeDisplayName(request.displayName)
        return UserOrganizer(nextAssignmentId++, model.id, displayName, true, false, demoNow)
            .also { assignments[it.id] = it }
    }

    fun updateAssignment(id: Long, request: UpdateOrganizerAssignment): UserOrganizer {
        val current = assignment(id)
        val displayName = normalizeDisplayName(request.displayName)
        if (request.isDefault && !request.active) conflict("Default organizer must be active")

        if (request.isDefault) {
            for (entry in assignments.entries) {
                if (entry.key != id && entry.value.isDefault) {
                    entry.setValue(entry.value.copy(isDefault = false))
                }
            }
        }
        return current.copy(displayName = displayName, active = request.active, isDefault = request.isDefault)
            .also { assignments[id] = it }
    }

    fun removeAssignment(id: Long) {
        assignments[id] = assignment(id).copy(active = false, isDefault = false)
        removedAssignments.add(id)
    }

    private fun normalizeDisplayName(value: String?): String? {
        require(value?.any(Char::isISOControl) != true)
        val name = value?.trim()
        require(name == null || name.length <= 32)
        return name?.takeUnless(String::isEmpty)
    }

    fun medications(): List<Medication> = medications.values.map(StoredMedication::summary)

    fun createMedication(request: CreateMedication): Medication {
        require(request.name.isNotBlank() && request.doseUnit.isNotBlank())
        require(request.doseAmount.isPositiveDecimal())
        require(request.strengthValue?.isPositiveDecimal() != false)
        val start = dayOf(request.startDate)
        require(request.endDate?.let { dayOf(it) >= start } != false)

        val medication = Medication(nextId++, request.name.trim(), "ACTIVE")
        medications[medication.id] = StoredMedication(medication, request)
        return medication
    }

    fun replaceSchedule(medicationId: Long, request: ReplaceSchedule): MedicationSchedule {
        if (medicationId !in medications) missing("Unknown demo medication")
        val start = dayOf(request.validFrom)
        require(request.validUntil?.let { dayOf(it) >= start } != false)
        require(request.days.isNotEmpty() && request.days.all { it in 1..7 })
        require(request.times.isNotEmpty() && request.times.all { timeFormat.matches(it.time) })
        require(request.times.map(ScheduleTime::time).toSet().size == request.times.size)

        if (medicationId !in medicationAssignments) {
            val organizer = assignments.values.firstOrNull { it.active && it.isDefault }
                ?: assignments.values.firstOrNull(UserOrganizer::active)
            organizer?.let { medicationAssignments[medicationId] = it.id }
        }
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
        occurrences.entries.removeAll { (key, dose) ->
            key.medicationId == medicationId && key.date >= request.validFrom && dose.status == DoseStatus.PENDING
        }

        return MedicationSchedule(
            nextId++, medicationId, request.validFrom, request.days, request.times, request.validUntil,
        ).also(versions::add)
    }

    fun dailyPlan(organizerId: Long, date: String): List<DoseOccurrence> {
        val day = dayOf(date)
        return plan(organizerId, day, day)
    }

    fun weeklyPlan(organizerId: Long, weekStart: String): List<DoseOccurrence> {
        val first = dayOf(weekStart)
        return plan(organizerId, first, first + 6)
    }

    fun startPreparation(organizerId: Long, request: StartPreparation): Preparation {
        val model = activeModel(organizerId)
        val first = dayOf(request.periodStart)
        val last = dayOf(request.periodEnd)
        require(last in first..(first + (model.daysCovered ?: 7) - 1))
        if (preparations[organizerId]?.status == PreparationStatus.IN_PROGRESS) {
            conflict("Preparation already in progress")
        }

        val days = (first..last).map(::isoWeekday).toSet()
        val sessionId = nextId++
        return Preparation(
            sessionId, organizerId, request.periodStart, request.periodEnd, PreparationStatus.IN_PROGRESS,
            model.compartments.filter { it.dayOfWeek == null || it.dayOfWeek in days }.map { definition ->
                CompartmentProgress(nextId++, definition.id, CompartmentStatus.UNCHECKED)
            },
        ).also { preparations[organizerId] = it }
    }

    fun currentPreparation(organizerId: Long): Preparation {
        requireActiveAssignment(organizerId)
        return preparations[organizerId] ?: missing("No demo preparation")
    }

    fun markCompartment(preparationId: Long, compartmentId: Long, request: UpdateCompartment): CompartmentProgress {
        val session = inProgress(preparationId)
        val index = session.compartments.indexOfFirst { it.compartmentDefinitionId == compartmentId }
        if (index == -1) missing("Unknown compartment for this session")
        val hasDoses = plan(session.userOrganizerId, dayOf(session.periodStart), dayOf(session.periodEnd))
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
        preparations[session.userOrganizerId] = session.copy(compartments = compartments)
        return progress
    }

    fun completePreparation(id: Long): Preparation {
        val session = inProgress(id)
        val plannedCompartments = plan(session.userOrganizerId, dayOf(session.periodStart), dayOf(session.periodEnd))
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
        return session.copy(status = PreparationStatus.READY, completedAt = demoNow)
            .also { preparations[session.userOrganizerId] = it }
    }

    fun recordIntake(id: Long, request: RecordIntake): IntakeRecord {
        val (key, dose) = occurrences.entries.firstOrNull { it.value.id == id } ?: missing("Unknown demo dose")
        if (dose.status != DoseStatus.PENDING) conflict("Intake already recorded for this dose")
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

    private fun plan(organizerId: Long, first: Long, last: Long): List<DoseOccurrence> {
        val model = activeModel(organizerId)
        for (day in first..last) {
            val date = dateOf(day)
            val weekday = isoWeekday(day)
            for (medication in medications.values) {
                val summary = medication.summary
                if (medicationAssignments[summary.id] != organizerId) continue
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
                        } ?: model.compartments.firstOrNull {
                            it.dayOfWeek == null && it.timeOfDay == time.timeOfDay
                        }
                        DoseOccurrence(
                            nextId++, summary.id, summary.name, organizerId, compartment?.id, date,
                            time.time, request.doseAmount, request.doseUnit, DoseStatus.PENDING,
                        )
                    }
                }
            }
        }
        val dates = dateOf(first)..dateOf(last)
        return occurrences.values.filter { it.userOrganizerId == organizerId && it.scheduledDate in dates }
            .sortedWith(compareBy(DoseOccurrence::scheduledDate, DoseOccurrence::scheduledTime, DoseOccurrence::id))
    }

    private fun inProgress(id: Long): Preparation {
        val session = preparations.values.firstOrNull { it.id == id } ?: missing("Unknown demo preparation")
        requireActiveAssignment(session.userOrganizerId)
        if (session.status != PreparationStatus.IN_PROGRESS) conflict("Preparation is already complete")
        return session
    }

    private fun requireActiveAssignment(id: Long): UserOrganizer =
        assignment(id).also { if (!it.active) conflict("Organizer assignment is inactive") }

    private fun activeModel(id: Long): OrganizerModelDetails =
        checkNotNull(DemoCatalog.model(requireActiveAssignment(id).organizerModelId))
}
