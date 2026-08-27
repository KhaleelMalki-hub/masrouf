package sa.masrouf.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The single source of truth for "what day did this happen on".
 *
 * Saudi Arabia is UTC+03:00 with no daylight saving, so a purchase made at
 * 01:30 on the 5th in Riyadh is still the 4th in UTC. Letting each screen ask
 * the device for "today" means a transaction can appear under two different
 * days depending on where the phone thinks it is - and monthly totals stop
 * adding up. Every date decision in the app goes through here.
 *
 * The calendar is Gregorian throughout. Hijri display is a presentation concern
 * and, if it is ever added, is a formatting layer on top of these values, never
 * a second storage format.
 */
object RiyadhTime {

    val ZONE: ZoneId = ZoneId.of("Asia/Riyadh")

    /** The calendar day an instant falls on, in Riyadh. */
    fun localDate(instant: Instant): LocalDate = instant.atZone(ZONE).toLocalDate()

    /** The first instant of a Riyadh calendar day. */
    fun startOfDay(date: LocalDate): Instant = date.atStartOfDay(ZONE).toInstant()

    /** The instant immediately after the last one belonging to a Riyadh calendar day. */
    fun endOfDayExclusive(date: LocalDate): Instant = startOfDay(date.plusDays(1))

    /** Interprets a wall-clock date-time read out of a bank message as Riyadh local time. */
    fun toInstant(local: LocalDateTime): Instant = local.atZone(ZONE).toInstant()

    /** Interprets a bare date as Riyadh midnight. Used when a statement row carries no time. */
    fun toInstant(date: LocalDate): Instant = startOfDay(date)
}
