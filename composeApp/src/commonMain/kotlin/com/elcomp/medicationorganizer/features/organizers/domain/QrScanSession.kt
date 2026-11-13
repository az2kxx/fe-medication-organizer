package com.elcomp.medicationorganizer.features.organizers.domain

/** Longest value the backend can store in `organizer_models.qr_code_value`. */
const val MAX_QR_VALUE_LENGTH = 255

sealed interface QrScanDecision {
    /** Start exactly one model lookup for [code]. */
    data class Lookup(val code: String) : QrScanDecision

    /** The value cannot be a product identifier, e.g. it is empty, too long or contains control characters. */
    data object Invalid : QrScanDecision

    /** A result for this scan is already being handled, so the frame is dropped. */
    data object Ignore : QrScanDecision
}

/**
 * Turns the stream of camera detections into at most one decision per scan. The camera keeps reporting the same
 * code on every frame, so only the first detection counts until [restart] is called. The QR value is treated
 * purely as a product identifier: it is never parsed as a URL or opened.
 */
class QrScanSession {
    var accepting: Boolean = true
        private set

    fun accept(raw: String): QrScanDecision {
        if (!accepting) return QrScanDecision.Ignore
        accepting = false
        return normalizeQrValue(raw)?.let(QrScanDecision::Lookup) ?: QrScanDecision.Invalid
    }

    fun restart() {
        accepting = true
    }
}

fun normalizeQrValue(raw: String): String? =
    raw.trim().takeIf { value -> value.length in 1..MAX_QR_VALUE_LENGTH && value.none(Char::isISOControl) }
