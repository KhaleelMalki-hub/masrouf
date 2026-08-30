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
    val mark = bankMark(card.bankId)
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val tint = mark?.colour ?: MaterialTheme.colorScheme.outline
    val isCredit = card.kind == BalanceReader.Kind.CREDIT_LIMIT.name

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
