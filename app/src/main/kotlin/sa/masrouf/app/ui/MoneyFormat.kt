package sa.masrouf.app.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import sa.masrouf.app.R
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

/**
 * An amount, drawn as it should be read and announced as it should be said.
 *
 * There was a `forSpeech` and two call sites. The other ten amounts in the app -
 * every figure on the income screen, the card tiles, the legend, the strip's
 * tooltip, an answer - were drawn straight and announced as bare numbers with
 * nothing saying of what. The riyal sign is younger than every speech engine, so a
 * screen reader meeting it says nothing at all: "six thousand one hundred and
 * ninety six point one eight", and the listener has to assume the unit.
 *
 * One composable rather than a description at each site, for the reason the
 * formatter itself is one function: the next amount added to a screen should get
 * this without anyone remembering to ask for it.
 */
@Composable
internal fun MoneyText(
    amount: Money,
    currencyLabel: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = 1,
) {
    val spoken = stringResource(R.string.currency_spoken)
    Text(
        text = amount.forDisplay(currencyLabel),
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = amount.forSpeech(spoken) },
    )
}
