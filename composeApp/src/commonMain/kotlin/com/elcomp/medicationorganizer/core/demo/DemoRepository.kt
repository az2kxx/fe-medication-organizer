package com.elcomp.medicationorganizer.core.demo

import com.elcomp.medicationorganizer.core.network.ApiClient
import io.ktor.http.encodeURLQueryComponent
import kotlin.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive

const val DEMO_DATE = "2026-11-02"
const val DEMO_ORGANIZER_ID = 1L
const val DEMO_MODEL_ID = 1L

/** Provisional demo payloads and routes. AppServices exposes this repository only in MOCK. */
class DemoRepository internal constructor(private val api: ApiClient) {
    suspend fun organizerModel(modelId: Long = DEMO_MODEL_ID): OrganizerModel =
        api.get("/api/organizer-models/$modelId")

    suspend fun medications(): List<Medication> = api.get("/api/medications", requiresSession = true)

    suspend fun createMedication(request: CreateMedication): Medication =
        api.post("/api/medications", request, requiresSession = true)

    suspend fun replaceSchedule(medicationId: Long, request: ReplaceSchedule): MedicationSchedule =
        api.put("/api/medications/$medicationId/schedule", request, requiresSession = true)

    suspend fun dailyPlan(date: String = DEMO_DATE, organizerId: Long = DEMO_ORGANIZER_ID): List<DoseOccurrence> =
        api.get(
            "/api/plan/daily?date=${date.encodeURLQueryComponent()}&organizerId=$organizerId",
            requiresSession = true,
        )

    suspend fun weeklyPlan(weekStart: String = DEMO_DATE, organizerId: Long = DEMO_ORGANIZER_ID): List<DoseOccurrence> =
        api.get(
            "/api/plan/weekly?weekStart=${weekStart.encodeURLQueryComponent()}&organizerId=$organizerId",
            requiresSession = true,
        )

    suspend fun startPreparation(request: StartPreparation, organizerId: Long = DEMO_ORGANIZER_ID): Preparation =
        api.post("/api/users/me/organizers/$organizerId/preparations", request, requiresSession = true)

    suspend fun currentPreparation(organizerId: Long = DEMO_ORGANIZER_ID): Preparation =
        api.get("/api/users/me/organizers/$organizerId/preparations/current", requiresSession = true)

    suspend fun markCompartment(
        preparationId: Long,
        compartmentId: Long,
        request: UpdateCompartment,
    ): CompartmentProgress = api.patch(
        "/api/preparations/$preparationId/compartments/$compartmentId",
        request,
        requiresSession = true,
    )

    suspend fun completePreparation(preparationId: Long): Preparation =
        api.post("/api/preparations/$preparationId/complete", requiresSession = true)

    suspend fun recordIntake(occurrenceId: Long, request: RecordIntake): IntakeRecord =
        api.post("/api/dose-occurrences/$occurrenceId/intake", request, requiresSession = true)

    suspend fun intakeHistory(from: String = DEMO_DATE, to: String = from): List<IntakeRecord> =
        api.get(
            "/api/intake?from=${from.encodeURLQueryComponent()}&to=${to.encodeURLQueryComponent()}",
            requiresSession = true,
        )
}

@Serializable
data class OrganizerModel(val id: Long, val name: String, val compartments: List<CompartmentDefinition>)

@Serializable
data class CompartmentDefinition(
    val id: Long,
    val positionIndex: Int,
    val dayOfWeek: Int,
    val timeOfDay: TimeOfDay,
)

@Serializable
data class CreateMedication(
    val name: String,
    val doseAmount: JsonPrimitive,
    val doseUnit: String,
    val startDate: String = DEMO_DATE,
    val endDate: String? = null,
    val strengthValue: JsonPrimitive? = null,
    val strengthUnit: String? = null,
)

@Serializable
data class Medication(val id: Long, val name: String, val status: String)

@Serializable
data class ReplaceSchedule(
    val validFrom: String,
    val days: Set<Int>,
    val times: List<ScheduleTime>,
    val validUntil: String? = null,
)

@Serializable
data class ScheduleTime(val time: String, val timeOfDay: TimeOfDay? = null)

@Serializable
enum class TimeOfDay { MORNING, NOON, EVENING, NIGHT, OTHER }

@Serializable
data class MedicationSchedule(
    val id: Long,
    val medicationId: Long,
    val validFrom: String,
    val days: Set<Int>,
    val times: List<ScheduleTime>,
    val validUntil: String? = null,
)

@Serializable
data class DoseOccurrence(
    val id: Long,
    val medicationId: Long,
    val medicationName: String,
    val userOrganizerId: Long,
    val compartmentDefinitionId: Long?,
    val scheduledDate: String,
    val scheduledTime: String,
    val doseAmountSnapshot: JsonPrimitive,
    val doseUnitSnapshot: String,
    val status: DoseStatus,
)

@Serializable
enum class DoseStatus { PENDING, TAKEN, SKIPPED }

@Serializable
data class StartPreparation(val periodStart: String, val periodEnd: String)

@Serializable
data class Preparation(
    val id: Long,
    val userOrganizerId: Long,
    val periodStart: String,
    val periodEnd: String,
    val status: PreparationStatus,
    val compartments: List<CompartmentProgress>,
    val completedAt: Instant? = null,
)

@Serializable
enum class PreparationStatus { IN_PROGRESS, READY }

@Serializable
data class UpdateCompartment(val status: CompartmentStatus, val note: String? = null)

@Serializable
data class CompartmentProgress(
    val id: Long,
    val compartmentDefinitionId: Long,
    val status: CompartmentStatus,
    val checkedAt: Instant? = null,
    val note: String? = null,
)

@Serializable
enum class CompartmentStatus { UNCHECKED, PREPARED, INTENTIONALLY_EMPTY }

@Serializable
data class RecordIntake(val status: IntakeStatus, val occurredAt: Instant? = null, val note: String? = null)

@Serializable
enum class IntakeStatus { TAKEN, SKIPPED }

@Serializable
data class IntakeRecord(
    val id: Long,
    val doseOccurrenceId: Long,
    val status: IntakeStatus,
    val occurredAt: Instant?,
    val recordedAt: Instant,
    val note: String? = null,
)
