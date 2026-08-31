package sa.masrouf.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.app.data.CardBalance
import sa.masrouf.core.capture.BalanceReader
import sa.masrouf.core.money.Money
import java.time.Instant

/**
 * Each card, and the last thing its messages said was left.
 *
 * Read off the messages alone, so it costs no connection and no account and
 * nothing leaves the device - and so it is only as current as the last message,
 * which each card says under its number.
 *
 * Only the cards in [ActiveCards] are shown. Twelve years of messages mention 26
 * cards and most are closed; a balance from 2018 on a card that no longer exists
 * is not information. A list of what is open is shorter and stays true as cards
 * are opened and closed, where a list of what is closed only grows.
 *
 * Two labels, never one. "الرصيد" is money in an account; "المتبقي من الحد" is what
 * a credit card will still let through, and it is not money the user has.
 */
@Composable
fun CardsPanel(
    cards: List<CardBalance>,
    currencyLabel: String,
    modifier: Modifier = Modifier,
) {
    val open = cards.filter { it.last4 in ActiveCards.LAST4 }
    if (open.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.cards_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(open, key = { it.last4 }) { card ->
                CardTile(card = card, currencyLabel = currencyLabel)
            }
        }
    }
}

@Composable
private fun CardTile(card: CardBalance, currencyLabel: String) {
    // bank_id is stamped from the sender, and only messages captured since that
    // feature existed carry one. Most of a twelve-year history predates it, so the
    // tile for a card whose last stamped message is old showed no issuer at all.
    // The owner's own statement fills the gap; it cannot go stale the way a guess
    // from an old template would.
    val mark = bankMark(card.bankId ?: CardIssuers.BANK_ID[card.last4])
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val tint = mark?.colour ?: MaterialTheme.colorScheme.outline
    val limit = CreditCards.LIMIT_HALALAS[card.last4]
    // A card's nature belongs to the card, not to whichever message happened to
    // arrive last. AlRajhi writes "رصيد" for a credit card exactly as it does for a
    // current account, so the reader files the figure as an account balance and the
    // tile said الرصيد 31,837.17 for a card carrying a 38,500 limit - money the
    // owner does not have. See [CreditCards].
    val isCredit = card.kind == BalanceReader.Kind.CREDIT_LIMIT.name || limit != null

    Card(
        modifier = Modifier.width(168.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
      Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = mark?.let { if (isArabic) it.labelAr else it.labelEn } ?: "",
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = card.last4,
                style = MoneyStyle.merge(MaterialTheme.typography.labelMedium),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        val halalas = card.halalas
        if (halalas != null) {
            Text(
                text = stringResource(if (isCredit) R.string.card_credit_left else R.string.card_balance),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.ofHalalas(halalas).forDisplay(currencyLabel),
                style = MoneyStyle.merge(MaterialTheme.typography.titleMedium),
            )
            // What is left means little without what it is left of. Shown only for
            // a limit the owner has stated, never a high-water mark guessed from
            // the messages: a card whose balance never reached its ceiling would
            // make the app understate the limit and overstate what has been used.
            if (limit != null) {
                Text(
                    text = stringResource(R.string.card_of_limit, Money.ofHalalas(limit).grouped()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // The bank never puts a figure in its messages. Saying so beats a
            // blank, which reads as the app having failed to read one.
            Text(
                text = stringResource(R.string.card_no_balance),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u2014",
                style = MoneyStyle.merge(MaterialTheme.typography.titleMedium),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Text(
            text = stringResource(R.string.card_as_of, Instant.ofEpochMilli(card.atMillis).dayLabel()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
    }
}

/**
 * The cards the user says are open, by their last four digits.
 *
 * Named by the user, not inferred: recency is not the same thing. A card can go
 * quiet for a year and still be in the wallet, and a closed card's last message
 * is often its most recent one - the closing statement.
 *
 * Three of these have never sent a message and will appear the day they do.
 */
object ActiveCards {
    val LAST4: Set<String> = setOf(
        "5763", "7536", "8134", "2383", "8202", "3761", "7285", "2166", "9941",
    )
}

/**
 * The owner's credit cards, and what each one's limit is.
 *
 * Which cards are credit cards cannot be read off a single message. AlRajhi's
 * templates for card 2383 say `رصيد:31837.17 SR` and never the word ائتمانية, so
 * [BalanceReader] - which decides from the keyword, correctly, because that is all
 * one message gives it - files the figure as an account balance. The tile then
 * showed it under "الرصيد", telling the owner he held 31,837 riyals when the number
 * was what a 38,500-riyal card would still let through.
 *
 * Being a credit card is a property of the card, so it is recorded per card, the
 * same way [ActiveCards] records which are open: stated by the owner, not inferred.
 * The limits are his own figures. Each is corroborated by the highest balance the
 * card's messages ever reported - 37,754 against 38,500, 41,010 against 41,000,
 * 97,000 against 97,000 - which is the check to repeat when one is added.
 *
 * A high-water mark is deliberately not used as a substitute for an owner-stated
 * limit: a card that has never been near its ceiling would report a limit far below
 * the real one, and the app would then overstate what had been spent on it.
 */
/**
 * Who issued each card, for the cards whose issuer is known.
 *
 * Only a fallback: a stamped `bank_id` always wins, because it came from the
 * sender of an actual message. This list covers the cards whose messages all
 * predate the stamp.
 *
 * Each entry is either the owner's own statement or read off the card's template
 * and confirmed by him. Two cards are deliberately absent: 7536 and 3761 appear
 * only as the funding card inside a barq wallet top-up, which names the card but
 * never the bank that issued it. A wrong bank on a tile is worse than none - it is
 * a confident label the owner would have no reason to doubt.
 */
object CardIssuers {
    val BANK_ID: Map<String, String> = mapOf(
        "2383" to "alrajhi",   // credit
        "5763" to "alrajhi",   // mada debit
        "8134" to "alrajhi",   // credit
        "1887" to "snb",       // mada debit
        "8202" to "d360",      // mada debit
        "9994" to "enbd",      // credit
        // The barq wallet. Three fragments of one balance rather than three cards:
        // barq writes a different one of them into each kind of message, so each
        // arrives looking like a card of its own.
        "7285" to "barq",
        "2166" to "barq",
        "9941" to "barq",
    )
}

object CreditCards {
    val LIMIT_HALALAS: Map<String, Long> = mapOf(
        "2383" to 38_500_00L,
        "8134" to 41_000_00L,
        "9994" to 97_000_00L,
    )
}
