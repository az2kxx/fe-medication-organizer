package com.elcomp.medicationorganizer.core.time

import java.util.Calendar
import java.util.GregorianCalendar

actual fun currentLocalDate(): String = GregorianCalendar().let {
    "${it.get(Calendar.YEAR).toString().padStart(4, '0')}-${(it.get(Calendar.MONTH) + 1).toString().padStart(2, '0')}-${it.get(Calendar.DAY_OF_MONTH).toString().padStart(2, '0')}"
}
