package sa.masrouf.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * A monetary amount held as an integer number of halalas (1 SAR = 100 halalas).
 *
 * Money is never a `Double` in this codebase. Floating point cannot represent
 * 0.10 exactly, so summing a year of grocery receipts in `Double` drifts, and a
 * personal finance app whose monthly total is quietly wrong by riyals is worse
 * than no app at all.
 */
@JvmInline
value class Money private constructor(val halalas: Long) : Comparable<Money> {

    val isZero: Boolean get() = halalas == 0L
    val isNegative: Boolean get() = halalas < 0L

    operator fun plus(other: Money): Money = Money(Math.addExact(halalas, other.halalas))
    operator fun minus(other: Money): Money = Money(Math.subtractExact(halalas, other.halalas))
    operator fun unaryMinus(): Money = Money(Math.negateExact(halalas))

    fun abs(): Money = if (halalas < 0) Money(Math.negateExact(halalas)) else this

    fun toBigDecimal(): BigDecimal = BigDecimal.valueOf(halalas, MINOR_UNIT_SCALE)

    /** Plain machine-readable form, always 2 decimals, ASCII digits: `"87.50"`, `"-1234.00"`. */
    fun toPlainString(): String = toBigDecimal().toPlainString()

    override fun compareTo(other: Money): Int = halalas.compareTo(other.halalas)

    override fun toString(): String = toPlainString()

    companion object {
        const val MINOR_UNIT_SCALE = 2

        val ZERO = Money(0)

        fun ofHalalas(halalas: Long): Money = Money(halalas)

        /**
         * @throws ArithmeticException if [major] carries more precision than halalas
         *   can hold. Rounding is refused rather than applied silently: a bank
         *   message with three decimals means the format was misread, and inventing
         *   a rounded value would hide the bug behind a plausible number.
         */
        fun ofMajor(major: BigDecimal): Money =
            Money(major.setScale(MINOR_UNIT_SCALE).movePointRight(MINOR_UNIT_SCALE).longValueExact())

        fun ofMajor(major: String): Money = ofMajor(BigDecimal(major))

        /** Rounds half-up. Only for values that are known to be approximate already. */
        fun ofMajorRounded(major: BigDecimal): Money =
            Money(major.setScale(MINOR_UNIT_SCALE, RoundingMode.HALF_UP).movePointRight(MINOR_UNIT_SCALE).longValueExact())

        /**
         * Parses a numeric token that has already been through
         * [sa.masrouf.core.text.ArabicText.normalize] - ASCII digits, `.` decimal
         * separator, optional `,` thousands separators.
         *
         * @return null when the token is not a well-formed amount. Callers must
         *   handle null; there is no "best effort" fallback by design.
         */
        fun parseOrNull(token: String): Money? {
            val cleaned = token.trim().replace(",", "")
            if (!AMOUNT_TOKEN.matches(cleaned)) return null
            return try {
                ofMajor(BigDecimal(cleaned))
            } catch (_: ArithmeticException) {
                null
            }
        }

        /**
         * The integer part may be absent. AlRajhi writes a savings profit of four
         * halalas as ".04", and refusing that stored nothing where four halalas
         * belonged - or, read as "04", four riyals: the same digits, wrong by a
         * hundred. `BigDecimal` accepts a leading point, so only this guard stood
         * in the way.
         */
        private val AMOUNT_TOKEN = Regex("^-?(?:\\d+(?:\\.\\d{1,2})?|\\.\\d{1,2})$")
    }
}
