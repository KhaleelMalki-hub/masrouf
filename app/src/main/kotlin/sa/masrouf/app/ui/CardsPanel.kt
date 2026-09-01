package sa.masrouf.app.ui

import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.key
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.app.data.CardBalance
import sa.masrouf.core.capture.BalanceReader
import sa.masrouf.core.money.Money
import java.time.Duration
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
    // Ordered by bank, in the order the owner named, and within a bank by the card
    // number so the row is the same every launch. It arrives ordered by most recent
    // activity, which reshuffles the row whenever a card is used - and a row that
    // moves is a row you have to read rather than recognise.
    val open = orderedCards(cards)
    if (open.isEmpty()) return

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.cards_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        // One height for every tile, set by the tallest content any of them can
        // carry: left to wrap, a card showing a limit stands taller than one whose
        // bank sends no figure, and a row at four different heights reads as a
        // fault rather than as a difference in what is known.
        //
        // A Row measuring its own tallest child, not a LazyRow at a height typed by
        // hand. A fixed height clips instead of growing and was wrong twice - at
        // 132dp when a card gained its ceiling, and at 150dp when "مسددة بالكامل"
        // and its limit wrapped to two lines. There are at most a dozen cards, so
        // laziness buys nothing and costs the intrinsic measurement that makes the
        // row size itself.
        // ...and costs the one thing LazyRow did give: a scroll that starts
        // where Arabic starts. `horizontalScroll` opens at offset 0, which is the
        // LEFT edge in either direction, so in RTL the row opened at its far end.
        // `reverseScrolling` moves the zero to the other edge - declaratively, so
        // it survives recreation. The first fix scrolled there in a LaunchedEffect
        // instead, and a theme change put the row back at the wrong end: a saved
        // scroll position is restored, but an effect that already ran is not re-run.
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState(), reverseScrolling = isRtl)
                .height(IntrinsicSize.Max)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (card in open) {
                key(card.last4) { CardTile(card = card, currencyLabel = currencyLabel) }
            }
        }
    }
}

@Composable
private fun CardTile(card: CardBalance, currencyLabel: String) {
    // The owner's statement first, the stamp second - the other way round until a
    // review found card 1887 labelled برق. Its own bank's messages stamp it snb;
    // a barq wallet top-up NAMES it as the funding card ("card number: **1887,
    // mada") and stamps that row barq, and the newest stamp wins. A wallet's
    // message names a card belonging to somebody else's bank, so the stamp is an
    // inference and the owner's statement is not.
    //
    // The stamp still fills every card he has not named, which is most of a
    // twelve-year history: bank_id exists only on messages captured since that
    // feature shipped.
    val mark = bankMark(CardIssuers.BANK_ID[card.last4] ?: card.bankId)
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    val tint = mark?.colour ?: MaterialTheme.colorScheme.outline
    val limit = CreditCards.limitHalalas[card.last4]
    val ageDays = Duration.between(Instant.ofEpochMilli(card.atMillis), Instant.now()).toDays()

    // A reading is old. Whether it is out of DATE is a different question, and the
    // threshold alone answered it wrongly.
    //
    // "Two statement cycles" was reasoned about a card in use. A card settled in
    // full and then left alone never sends another message, so it goes silent
    // forever - and the app was hiding a figure that was exactly right, while the
    // owner's bank app showed the same number.
    //
    // A remaining allowance equal to the ceiling means nothing is owed, and nothing
    // can change it but a purchase, which would send a message. So a full card is
    // never stale: the silence IS the confirmation. Anything short of the ceiling
    // is a debt that gets paid without the bank saying so, and that is what ages.
    val settledInFull = limit != null && card.halalas == limit
    val stale = ageDays > STALE_AFTER_DAYS && !settledInFull
    // A card's nature belongs to the card, not to whichever message happened to
    // arrive last. AlRajhi writes "رصيد" for a credit card exactly as it does for a
    // current account, so the reader files the figure as an account balance and the
    // tile called a credit card's remaining allowance الرصيد - money the
    // owner does not have. See [CreditCards].
    val isCredit = card.kind == BalanceReader.Kind.CREDIT_LIMIT.name || limit != null

    Card(
        modifier = Modifier
            .width(168.dp)
            .fillMaxHeight(),
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
        // A credit card's remaining allowance is only true until the next payment,
        // and this one had been settled in full for five months while the tile
        // still showed a fifth of its limit. The date said so in the faintest line
        // on the card and was read as a footnote, which is what a footnote is for.
        //
        // So past the threshold the figure is not shown at all. The app does not
        // know what is on that card today, and "—" is what it says everywhere else
        // it does not know. A number it cannot stand behind is worse than a blank:
        // the blank is questioned, the number is believed.
        val halalas = card.halalas?.takeIf { !stale }
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
                    text = stringResource(
                        if (settledInFull) R.string.card_settled else R.string.card_of_limit,
                        Money.ofHalalas(limit).grouped(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Two different silences, and they are not the same fact: this bank
            // has never sent a figure, or it sent one long enough ago that the app
            // will not stand behind it.
            //
            // The second says the app has not HEARD, not that the number is
            // unknowable. The owner read the first wording as the app claiming
            // nobody could know, and showed his bank's own app saying 41,000 - it
            // knows because it asks the bank; this app has no internet permission
            // and reads messages, so it can only ever know what was sent to it.
            Text(
                text = stringResource(
                    if (stale && card.halalas != null) R.string.card_balance_not_heard
                    else R.string.card_no_balance
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "\u2014",
                style = MoneyStyle.merge(MaterialTheme.typography.titleMedium),
                color = MaterialTheme.colorScheme.outline,
            )
        }
        // A figure the bank last mentioned months ago is not wrong, and it is not
        // current either. One tile showed a figure that was a fifth of its limit -
        // true on 2 April 2026, and still on screen in September, by which time the
        // owner had paid the card off. The date was already here, in the faintest
        // style on the tile, and it read as a footnote rather than as a caveat.
        //
        // So a stale reading says so in words and takes the ordinary label colour
        // instead of the outline, which is the one thing that makes a caveat get
        // read. The threshold is a card's own rhythm: a card in use reports within
        // a statement cycle, and two of those without a word is the point where the
        // figure stops describing now.
        Text(
            text = stringResource(
                if (stale) R.string.card_as_of_stale else R.string.card_as_of,
                Instant.ofEpochMilli(card.atMillis).dayLabel(),
            ),
            style = MaterialTheme.typography.labelSmall,
            color = if (stale) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
    }
    }
}

/**
 * Where each bank sits in the row, as the owner reads them.
 *
 * The owner's own order, not an alphabet and not an inference: AlRajhi first
 * because it carries the account his salary lands in, then AlAhli, then D360, then
 * barq. A bank not listed - a card whose issuer is unknown - goes last rather than
 * first, so an unnamed card never displaces a named one.
 *
 * Resolved the same way the tile resolves its label: the owner's statement first,
 * the message stamp second.
 */
/**
 * The open cards, in the order the owner reads them: his banks in the order he
 * thinks of them, and within a bank by number so a card never moves between
 * launches. A bank the list does not name sorts last rather than first - an
 * unknown issuer is the least interesting tile, not the most.
 *
 * Composition-free so it can be asserted; ordering was wrong on the screen twice
 * before it was a function.
 */
internal fun orderedCards(cards: List<CardBalance>): List<CardBalance> = cards
    .filter { it.last4 in ActiveCards.LAST4 }
    .sortedWith(compareBy({ it.bankOrder }, { it.last4 }))

private val BANK_ORDER = listOf("alrajhi", "snb", "d360", "barq", "aljazira", "saib", "enbd")

private val CardBalance.bankOrder: Int
    get() = BANK_ORDER.indexOf(CardIssuers.BANK_ID[last4] ?: bankId)
        .takeIf { it >= 0 } ?: BANK_ORDER.size

/** Two statement cycles. Past this, a balance describes the past. */
private const val STALE_AFTER_DAYS = 62L


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
        // The two banks he named on 2026-09-01: 9097 is his SAIB mada card, and
        // 3761 - listed above since before its issuer was known - is AlJazira's.
        "9097",
        // Both are in daily use and neither was being shown. 1887 is the owner's
        // AlAhli mada card - 427 messages, the most recent three days old - and
        // 9994 his Emirates NBD credit card, the one he settles the AlRajhi card
        // from. Named by him, like every other entry here.
        "1887", "9994",
    )
}

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
        // Named by the owner on 2026-09-01. 7536 is a MasterCard, but drawn on the
        // account rather than on credit - the network is not the question a tile
        // answers, the bank is.
        "3761" to "aljazira",  // mada debit
        "9097" to "saib",      // mada debit
        "7536" to "snb",       // mastercard, debit
        "2383" to "alrajhi",   // credit
        "5763" to "alrajhi",   // mada debit
        "8134" to "alrajhi",   // credit
        "1887" to "snb",       // mada debit
        "8202" to "d360",      // mada debit
        "9994" to "enbd",      // credit
        // Three separate barq cards, confirmed by the owner - not one wallet seen
        // three ways, which is what their overlapping date ranges first suggested.
        // They are funded from his own mada cards, and those top-ups are already
        // OWN_TRANSFER on both legs.
        "7285" to "barq",
        "2166" to "barq",
        "9941" to "barq",
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
 * was only what the card would still let through.
 *
 * Being a credit card is a property of the card, so it is recorded per card, the
 * same way [ActiveCards] records which are open: stated by the owner, not inferred.
 * The limits are his own figures. Each is corroborated by the highest balance the
 * card's messages ever reported: a remaining allowance can approach its ceiling
 * but never pass it, so a limit below one is a typo. That check belongs with the
 * figures, which is why the test asserts the PARSER and the owner keeps the
 * numbers.
 *
 * A high-water mark is deliberately not used as a substitute for an owner-stated
 * limit: a card that has never been near its ceiling would report a limit far below
 * the real one, and the app would then overstate what had been spent on it.
 *
 * The figures themselves are not here. A named person's credit limit is a fact
 * about him, this repository is public, and CLAUDE.md's Privacy section permits a
 * card's last four and nothing more. They are supplied at startup from
 * `local.properties`, which is gitignored; absent, no card shows a ceiling.
 */
object CreditCards {

    /**
     * Empty until configured. A card with no limit shows its remaining figure
     * without a ceiling - less information, and nothing invented.
     */
    @Volatile
    var limitHalalas: Map<String, Long> = emptyMap()
        private set

    /**
     * @param spec entries separated by `;`, each `last4:halalas`, as in
     *   `"1234:1230000 ; 5678:4560000"`. An entry that is not a four-digit card
     *   and a positive integer is dropped rather than guessed at: a mistyped limit
     *   would understate the ceiling and overstate what had been spent.
     */
    fun configure(spec: String) {
        limitHalalas = spec.split(';').mapNotNull { entry ->
            val (last4, halalas) = entry.split(':').map(String::trim).takeIf { it.size == 2 }
                ?: return@mapNotNull null
            val value = halalas.toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            last4.takeIf { it.length == 4 && it.all(Char::isDigit) }?.let { it to value }
        }.toMap()
    }
}
