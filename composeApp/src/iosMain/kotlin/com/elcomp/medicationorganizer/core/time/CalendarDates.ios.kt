package com.elcomp.medicationorganizer.core.time

import platform.Foundation.*

actual fun currentLocalDate(): String {
    val calendar = NSCalendar(calendarIdentifier = NSCalendarIdentifierGregorian)!!
    calendar.timeZone = NSTimeZone.localTimeZone
    val parts = calendar.components(NSCalendarUnitYear or NSCalendarUnitMonth or NSCalendarUnitDay, NSDate())
    return "${parts.year.toString().padStart(4, '0')}-${parts.month.toString().padStart(2, '0')}-${parts.day.toString().padStart(2, '0')}"
}
