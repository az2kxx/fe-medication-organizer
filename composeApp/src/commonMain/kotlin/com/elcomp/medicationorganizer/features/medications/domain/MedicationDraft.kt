package com.elcomp.medicationorganizer.features.medications.domain

import com.elcomp.medicationorganizer.core.demo.CreateMedication
import com.elcomp.medicationorganizer.core.demo.MedicationForm
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonUnquotedLiteral

internal data class MedicationDraft(
    val name: String = "",
    val doseAmount: String = "",
    val doseUnit: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val strengthValue: String = "",
    val strengthUnit: String = "",
    val form: MedicationForm? = null,
) {
    fun validate(): MedicationDraftValidation {
        val errors = mutableMapOf<MedicationField, MedicationFieldError>()
        val name = name.trim()
        val amount = doseAmount.trim()
        val unit = doseUnit.trim()
        val start = startDate.trim()
        val end = endDate.trim().ifEmpty { null }
        val strength = strengthValue.trim()
        val strengthUnit = strengthUnit.trim()
        val amountNumber = amount.positiveDecimal()
        val strengthNumber = strength.positiveDecimal()

        if (name.isEmpty()) errors[MedicationField.NAME] = MedicationFieldError.REQUIRED
        if (unit.isEmpty()) errors[MedicationField.DOSE_UNIT] = MedicationFieldError.REQUIRED
        if (amountNumber == null) {
            errors[MedicationField.DOSE_AMOUNT] =
                if (amount.isEmpty()) MedicationFieldError.REQUIRED else MedicationFieldError.POSITIVE_DECIMAL
        }
        if (strength.isNotEmpty() && strengthUnit.isEmpty()) {
            errors[MedicationField.STRENGTH_UNIT] = MedicationFieldError.REQUIRED
        }
        if (strengthNumber == null && (strength.isNotEmpty() || strengthUnit.isNotEmpty())) {
            errors[MedicationField.STRENGTH_VALUE] =
                if (strength.isEmpty()) MedicationFieldError.REQUIRED else MedicationFieldError.POSITIVE_DECIMAL
        }

        val startValid = start.isCalendarDate()
        if (!startValid) {
            errors[MedicationField.START_DATE] =
                if (start.isEmpty()) MedicationFieldError.REQUIRED else MedicationFieldError.INVALID_DATE
        }
        if (end != null) {
            if (!end.isCalendarDate()) {
                errors[MedicationField.END_DATE] = MedicationFieldError.INVALID_DATE
            } else if (startValid && end < start) {
                errors[MedicationField.END_DATE] = MedicationFieldError.END_BEFORE_START
            }
        }

        if (errors.isNotEmpty()) return MedicationDraftValidation(request = null, errors = errors)
        return MedicationDraftValidation(
            request = CreateMedication(
                name = name,
                doseAmount = checkNotNull(amountNumber),
                doseUnit = unit,
                startDate = start,
                endDate = end,
                strengthValue = strengthNumber,
                strengthUnit = strengthUnit.ifEmpty { null },
                form = form,
            ),
            errors = emptyMap(),
        )
    }
}

internal data class MedicationDraftValidation(
    val request: CreateMedication?,
    val errors: Map<MedicationField, MedicationFieldError>,
)

internal enum class MedicationField { NAME, DOSE_AMOUNT, DOSE_UNIT, START_DATE, END_DATE, STRENGTH_VALUE, STRENGTH_UNIT }

internal enum class MedicationFieldError { REQUIRED, POSITIVE_DECIMAL, INVALID_DATE, END_BEFORE_START }

private val decimalFormat = Regex("(?:[0-9]+(?:[.,][0-9]+)?|[.,][0-9]+)")
private val dateFormat = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

@OptIn(ExperimentalSerializationApi::class)
private fun String.positiveDecimal(): JsonPrimitive? {
    if (!decimalFormat.matches(this) || none { it in '1'..'9' }) return null
    val decimal = replace(',', '.')
    val integer = decimal.substringBefore('.').trimStart('0').ifEmpty { "0" }
    val fraction = decimal.substringAfter('.', "")
    val literal = if (fraction.isEmpty()) integer else "$integer.$fraction"
    return JsonUnquotedLiteral(literal)
}

private fun String.isCalendarDate(): Boolean {
    if (!dateFormat.matches(this)) return false
    return try {
        Instant.parse("${this}T00:00:00Z")
        true
    } catch (_: IllegalArgumentException) {
        false
    }
}
