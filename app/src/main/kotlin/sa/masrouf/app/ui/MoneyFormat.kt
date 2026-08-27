package sa.masrouf.app.ui

import sa.masrouf.core.money.Money

/**
 * Formats an amount for display.
 *
 * ASCII digits in both locales. Saudi bank apps and statements print Western
 * digits, and the parsers in `:core` normalise everything to them, so showing
 * Arabic-Indic digits here would make the app's own display the only place in the
 * user's financial life using a different numeral set.
 *
 * The amount is placed before the currency word in logical order. In an Arabic
 * (right-to-left) layout the bidi algorithm then renders the number on the right,
 * which is where an Arabic reader starts - no per-locale string is needed.
 */
fun Money.forDisplay(currencyLabel: String): String = "${toPlainString()} $currencyLabel"
