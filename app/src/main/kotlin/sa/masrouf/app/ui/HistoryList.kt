package sa.masrouf.app.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.core.model.CardKind
import sa.masrouf.core.model.Category
import sa.masrouf.core.model.Direction
import sa.masrouf.core.model.MerchantNames
import sa.masrouf.core.model.countsAsSpending
import sa.masrouf.core.model.SaudiCategories
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.money.Money

/**
 * The history list: one row per transaction, the filters above it, and the sheet
 * that files one.
 *
 * A seam of its own because everything here is about a stored row - reading one,
 * narrowing to a set of them, changing one's category. None of it knows about the
 * month panel or the entry sheet.
 */

/**
 * A captured record with the two things the user can do about it.
 *
 * The amount and the merchant are shown as the parser read them, because the
 * question being asked is whether that reading is right. There is no edit here on
 * purpose: a wrong amount is dismissed and typed in by hand, which keeps the one
 * number the user vouched for a number they actually entered.
 */
@Composable
fun SignedAmount(
    transaction: Transaction,
    currencyLabel: String,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val isCredit = transaction.direction == Direction.CREDIT
    Text(
        text = if (isCredit) {
            "+ ${transaction.amount.forDisplay(currencyLabel)}"
        } else {
            transaction.amount.forDisplay(currencyLabel)
        },
        style = style,
        color = if (isCredit) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}

@Composable
internal fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    currencyLabel: String,
    cardBanks: Map<String, String>,
    cardKinds: Map<String, CardKind> = emptyMap(),
    salary: Money?,
    onRefile: () -> Unit,
) {
    val category = SaudiCategories.byId(transaction.categoryId)
    val aboveSalary = salary != null && transaction.direction == Direction.DEBIT &&
        transaction.countsAsSpending && transaction.amount.halalas >= salary.halalas
    // The record's own bank first. Falling back to the card is not a guess: a card
    // belongs to one bank, so a record that names both answers it for every other
    // record on that card - including the years captured before the app recorded a
    // bank at all.
    val mark = bankMark(transaction.bankId ?: transaction.accountLast4?.let(cardBanks::get))

    ListItem(
        modifier = modifier
            // One node to a screen reader: the merchant, the amount, the day and the
            // category are one transaction, not four things to reassemble.
            .semantics(mergeDescendants = true) {}
            // The row is the way to refile it. A wrong category is the most common
            // thing a person wants to change about a transaction, and it should not
            // require finding a control.
            .clickable(onClick = onRefile),
        // Transparent so the list stays one surface; M3's default container would
        // paint every row and turn the history into a stack of cards.
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            // The category, as a glyph in a disc tinted with its own colour. This was
            // a 3dp coloured stripe down the edge of the row until the product doc
            // named side-stripe borders as the thing this app must not look like.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(bandColour(category).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = category.icon,
                    contentDescription = null,
                    tint = bandColour(category),
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        headlineContent = {
            Text(
                // The Arabic name when there is one. The bank's own descriptor is
                // still what is stored and searched; this only changes what a
                // person reads.
                text = MerchantNames.forMerchant(transaction.merchantRaw)?.forLocale()
                    ?: transaction.merchantRaw
                    ?: transaction.note
                    ?: stringResource(transaction.type.labelRes),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (aboveSalary) {
                    // One purchase worth a whole month's salary is the thing the
                    // user asked to have marked. A label, not a colour: colour
                    // already means category on this row.
                    Text(
                        text = stringResource(R.string.above_salary),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                if (mark != null || transaction.accountLast4 != null) {
                    CardMark(
                        mark = mark,
                        last4 = transaction.accountLast4,
                        kind = transaction.accountLast4?.let(cardKinds::get),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    // Date and category on one line: two facts about the same row,
                    // and stacking them would make a two-line row a three-line one.
                    text = category
                        ?.let { "${transaction.dayLabel()}  ·  ${stringResource(it.labelRes)}" }
                        ?: transaction.dayLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    // Last in the row and the first to give way. Without a weight
                    // the badge and the card chip are measured first and this
                    // collapsed to an ellipsis - the date and category, which are
                    // what the line is for.
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
        },
        // No delete control on the row. M3 lists carry at most one trailing element,
        // and a close glyph on every row took the width the category needed, so
        // the category read as "...". Deleting is in the row's own sheet, where
        // the row's name is on screen when the question is asked.
        trailingContent = { SignedAmount(transaction = transaction, currencyLabel = currencyLabel) },
    )
}

/** Which irreversible thing the user just asked for. */
internal sealed interface DestructiveAction {

    /** Remove a record from the history. A manual one can simply be typed again. */
    data class Delete(val transaction: Transaction) : DestructiveAction

    /**
     * Reject a captured record. Strictly worse than [Delete]: it also destroys
     * `rawText`, the original bank message, which is the one field the user cannot
     * reproduce from memory. It was previously the only unguarded one.
     */
    data class Dismiss(val transaction: Transaction) : DestructiveAction

    /**
     * Throw away every category the app filed and file again with current rules.
     *
     * Destroys no transaction, so it names no amount and no day. What it destroys
     * is the app's own filing across the whole history, which is why it is here and
     * not a menu item that acts on the first tap.
     */
    data object RefileAll : DestructiveAction
}

/**
 * Narrowing the month's list.
 *
 * With 22,000 records across the history, a list you cannot search is a list
 * nobody can answer a question with. The category chip is set by tapping a legend
 * row rather than by a second control, because the legend already names every
 * category and repeating them in a filter menu would be the same list twice.
 */
@Composable
internal fun HistoryFilters(
    query: String,
    onQueryChange: (String) -> Unit,
    activeFilter: HistoryFilter?,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            // The search bar's own shape in M3 is the full pill.
            shape = MaterialTheme.shapes.extraLarge,
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.fillMaxWidth(),
        )
        if (activeFilter != null || query.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                activeFilter?.let { filter ->
                    val name = when (filter) {
                        HistoryFilter.Unfiled -> stringResource(R.string.uncategorised)
                        is HistoryFilter.OfCategory -> stringResource(filter.category.labelRes)
                    }
                    Text(
                        text = stringResource(R.string.showing_category, name),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.heightIn(min = 48.dp),
                ) { Text(stringResource(R.string.filter_clear)) }
            }
        }
    }
}

/**
 * Refiling a merchant.
 *
 * The decision is about the merchant, not the row: a person deciding that ALDREES
 * is transport has decided it for all forty of them, and for next month's. Saying
 * so plainly is better than a checkbox nobody reads, and better than silently
 * doing it and surprising them.
 */
@Composable
internal fun RefileSheet(
    transaction: Transaction,
    onPick: (Category?, RefileScope) -> Unit,
    onForget: () -> Unit,
    onDelete: () -> Unit,
) {
    val current = SaudiCategories.byId(transaction.categoryId)
    val merchant = transaction.merchantRaw ?: stringResource(transaction.type.labelRes)
    var scope by remember { mutableStateOf(RefileScope.WHOLE_MERCHANT) }
    val bank = transaction.bankId?.let { bankMark(it) }
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.refile_title, merchant),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        if (transaction.merchantKey != null) {
            // How far the decision reaches. The whole merchant is the useful
            // default - one tap files forty rows - but a card network sends the
            // same word for a cafe and a bakery, and then the bank that announced
            // the purchase is the only thing that tells them apart. "This one" is
            // for the odd purchase that belongs to neither rule.
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                val options = buildList {
                    add(RefileScope.THIS_ONE to stringResource(R.string.refile_scope_one))
                    if (bank != null) {
                        add(RefileScope.THIS_BANK to stringResource(R.string.refile_scope_bank, if (isArabic) bank.labelAr else bank.labelEn))
                    }
                    add(RefileScope.WHOLE_MERCHANT to stringResource(R.string.refile_scope_all))
                }
                options.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = scope == value,
                        onClick = { scope = value },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
        CategoryChips(
            selected = current,
            onSelect = { onPick(it, scope) },
            edgePadding = 20.dp,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (transaction.merchantKey != null) {
                // The undo for a filing decision. Without it a choice made once -
                // often from a truncated name that looked like something else -
                // outranks every built-in rule for ever.
                TextButton(onClick = onForget, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.refile_forget))
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            // Destructive, so it sits apart from the filing controls and in the
            // error colour, and it still goes through the confirmation that names
            // the amount and the day.
            TextButton(
                onClick = onDelete,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(R.string.delete))
            }
        }
    }
}

/**
 * Which card, and whose.
 *
 * A tinted chip rather than another coloured bar: the row already carries the
 * category's colour down its leading edge, and a second colour system competing
 * with it would make both mean less. The bank's colour is inside a shape that is
 * clearly a label, so the two never read as the same kind of signal.
 *
 * The digits are kept even when the bank is known. Two cards from one bank is the
 * ordinary case, and "الراجحي" alone cannot tell them apart.
 */
@Composable
internal fun CardMark(mark: BankMark?, last4: String?, kind: CardKind? = null) {
    val colour = mark?.colour ?: MaterialTheme.colorScheme.onSurfaceVariant
    // Digits alone, with no leading dots to say "and four more". In a right-to-left
    // row the dots land on the wrong side of the number and read as part of it; the
    // chip is already unmistakably a tag, and does that work without them.
    val isArabic = LocalConfiguration.current.locales[0].language == "ar"
    // Bank, then kind, then digits: "الراجحي مدى 2383". The kind is here because a
    // few shops take mada and nothing else, so which card a purchase went on is a
    // fact the owner reads rows for - and it is only ever shown when the card's own
    // messages said it. See CardKinds.
    val text = listOfNotNull(
        mark?.let { if (isArabic) it.labelAr else it.labelEn },
        kind?.let { stringResource(it.shortLabelRes) },
        last4,
    ).joinToString(" ")

    Text(
        text = text,
        // Pinned to the layout direction rather than to the first strong character
        // in it. "الراجحي مدى 2383" begins with an Arabic letter and "D360 مدى
        // 2383" with a Latin one, so two rows of the same list put their digits at
        // opposite ends of the chip.
        style = MaterialTheme.typography.labelSmall.copy(
            textDirection = if (isArabic) TextDirection.Rtl else TextDirection.Ltr,
        ),
        color = colour,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colour.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/**
 * The merchant name in the language the interface is being read in.
 *
 * Not the device language: the app has its own switch, and someone reading the
 * English interface on an Arabic phone is asking for English.
 */
@Composable
@ReadOnlyComposable
private fun MerchantNames.MerchantName.forLocale(): String =
    if (LocalConfiguration.current.locales[0].language == "ar") ar else en


/** The short word for a kind of card, for the chip on a row. */
internal val CardKind.shortLabelRes: Int
    get() = when (this) {
        CardKind.MADA -> R.string.card_kind_mada_short
        CardKind.CREDIT -> R.string.card_kind_credit_short
    }
