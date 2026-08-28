package sa.masrouf.app.ui

import sa.masrouf.core.model.Transaction
import java.time.format.DateTimeFormatter

/**
 * The day a transaction belongs to, for display.
 *
 * An explicit pattern rather than a localised formatter. `ofLocalizedDate` renders
 * its digits in the locale's own numerals, which in Arabic would print ٢٨/٠٨/٢٦
 * beside an amount printed 125.00 - the same split this app already decided
 * against for the pending count. A pattern with no text fields formats identically
 * in both languages and always in ASCII.
 *
 * The day itself comes from [Transaction.calendarDay], which resolves the instant
 * in Riyadh. Formatting the instant directly here would let the device's timezone
 * decide which day a purchase belongs to.
 */
private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yy")

fun Transaction.dayLabel(): String = calendarDay.format(DAY_FORMAT)
