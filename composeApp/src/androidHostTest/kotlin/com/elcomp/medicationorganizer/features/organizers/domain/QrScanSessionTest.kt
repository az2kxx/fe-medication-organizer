package com.elcomp.medicationorganizer.features.organizers.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class QrScanSessionTest {
    @Test
    fun onlyTheFirstDetectionStartsALookupUntilRestart() {
        val session = QrScanSession()

        assertEquals(QrScanDecision.Lookup("ELCOMP:ELC-7X3"), session.accept("  ELCOMP:ELC-7X3 \n"))
        assertFalse(session.accepting)
        repeat(30) { assertEquals(QrScanDecision.Ignore, session.accept("ELCOMP:ELC-7X3")) }
        assertEquals(QrScanDecision.Ignore, session.accept("ELCOMP:ELC-1X4"))

        session.restart()
        assertTrue(session.accepting)
        assertEquals(QrScanDecision.Lookup("ELCOMP:ELC-1X4"), session.accept("ELCOMP:ELC-1X4"))
    }

    @Test
    fun unusableValuesEndTheScanAsInvalidInsteadOfStartingALookup() {
        for (raw in listOf("", "   ", "ELCOMP:\u0000ELC", "A".repeat(MAX_QR_VALUE_LENGTH + 1))) {
            val session = QrScanSession()
            assertEquals(QrScanDecision.Invalid, session.accept(raw), "raw=$raw")
            assertEquals(QrScanDecision.Ignore, session.accept("ELCOMP:ELC-7X3"))
        }
    }

    @Test
    fun urlsStayOpaqueIdentifiers() {
        val url = "https://example.com/open?product=ELC-7X3&next=javascript:alert(1)"
        assertEquals(url, normalizeQrValue(url))
        assertEquals("A".repeat(MAX_QR_VALUE_LENGTH), normalizeQrValue("A".repeat(MAX_QR_VALUE_LENGTH)))
        assertNull(normalizeQrValue("\t\n"))
    }
}
