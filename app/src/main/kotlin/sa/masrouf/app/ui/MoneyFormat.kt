package sa.masrouf.app.ui

import sa.masrouf.core.money.Money
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Formats an amount for display.
 *
 * ASCII digits in both locales, and grouped. Saudi bank apps and statements print
 * Western digits, and the parsers normalise everything to them, so showing
 * Arabic-Indic here would make the app the only place in the user's financial life
 * using a different numeral set.
 *
 * The grouping is not decoration: a real month came out at 157767.42, which has to
 * be counted digit by digit to be read. 157,767.42 does not.
 *
 * Built on [Locale.ROOT] deliberately. A locale-aware formatter would switch to
 * Arabic-Indic digits under an Arabic locale, and some locales use a different
 * grouping separator entirely - both of which would undo the decision above.
 */
private val AMOUNT_FORMAT = DecimalFormat(
    "#,##0.00",
    DecimalFormatSymbols(Locale.ROOT),
).apply { roundingMode = RoundingMode.UNNECESSARY }

/**
 * The amount with its currency.
 *
 * The amount is placed before the currency word in logical order. In an Arabic
 * (right-to-left) layout the bidi algorithm then renders the number on the right,
 * which is where an Arabic reader starts - no per-locale string is needed.
 */
fun Money.forDisplay(currencyLabel: String): String = "${grouped()} $currencyLabel"

/**
 * The amount as it should be SAID, which is not how it is drawn.
 *
 * The riyal sign was encoded in 2025 and no speech engine has a name for it yet, so
 * every amount in the app was announced as a bare number - "six thousand one hundred
 * and ninety six point one eight", with nothing saying of what. The glyph stays on
 * screen; this is what a screen reader is given instead.
 */
fun Money.forSpeech(currencyName: String): String = "${grouped()} $currencyName"

/**
 * Just the digits, grouped.
 *
 * Rounding is set to UNNECESSARY on purpose: the value is already exact to the
 * halala, so if this ever had to round it would mean something upstream had
 * produced a fraction of a halala, and that should surface rather than be
 * quietly smoothed away.
 */
fun Money.grouped(): String = AMOUNT_FORMAT.format(toBigDecimal())
