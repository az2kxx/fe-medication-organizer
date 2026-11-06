package com.elcomp.medicationorganizer.features.organizers.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class CompartmentTextTest {
    @Test
    fun physicalLabelsKeepCompartmentsWithTheSameDayAndTimeDistinct() {
        val first = compartmentText("A", "Poniedziałek", "Rano", rowHasDay = true)
        val second = compartmentText("B", "Poniedziałek", "Rano", rowHasDay = true)

        assertEquals("A", first.label)
        assertEquals("Rano", first.context)
        assertEquals("A, Poniedziałek, Rano", first.description)
        assertNotEquals(first.description, second.description)
    }

    @Test
    fun dayIsVisibleContextOnlyWhenTheRowHeadingDoesNotShowIt() {
        assertEquals("Poniedziałek, Rano", compartmentText("A", "Poniedziałek", "Rano", rowHasDay = false).context)
        assertEquals("Wieczór", compartmentText("3", null, "Wieczór", rowHasDay = false).context)

        val labelOnly = compartmentText("noc", null, null, rowHasDay = false)
        assertNull(labelOnly.context)
        assertEquals("noc", labelOnly.description)
    }
}
