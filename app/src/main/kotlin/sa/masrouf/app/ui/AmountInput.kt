package sa.masrouf.app.ui

import sa.masrouf.core.money.Money
import sa.masrouf.core.text.ArabicText

/**
 * Turns what the user typed into the amount field into a [Money], or explains why
 * it cannot.
 *
 * Kept out of the composable so it can be tested without a device, because this is
 * the one place in the UI where a wrong answer becomes a wrong number in the
 * user's history rather than a wrong pixel.
 */
object AmountInput {

    sealed interface Result {
        data class Valid(val amount: Money) : Result

        /** Nothing typed yet. Not an error to show while the field is still empty. */
        data object Empty : Result

        /** Typed, but not an amount. Includes more precision than halalas can hold. */
        data object Invalid : Result
    }

    /**
     * An Arabic keyboard produces Arabic-Indic digits (`١٢٣`) and the Arabic
     * decimal separator (`٫`), neither of which [Money.parseOrNull] accepts. They
     * are converted here rather than being rejected, because to the user they are
     * simply the digits on their keyboard.
     *
     * Excess precision stays a rejection, not a rounding: `Money` refuses it for
     * the same reason a bank message with three decimals is treated as misread.
     */
    fun parse(typed: String): Result {
        val normalized = ArabicText.normalize(typed)
        if (normalized.isBlank()) return Result.Empty

        val amount = Money.parseOrNull(normalized) ?: return Result.Invalid
        // A minus sign parses fine, but direction carries the sign in this app and
        // a negative expense has no meaning the user could have intended.
        if (amount.isNegative) return Result.Invalid
        return Result.Valid(amount)
    }
}
