package sa.masrouf.app.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import sa.masrouf.core.ask.AskSort
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import sa.masrouf.app.R
import sa.masrouf.app.ui.AddExpenseViewModel.AskState
import sa.masrouf.core.ask.AskAnswer
import sa.masrouf.core.ask.Flow
import sa.masrouf.core.ask.Measure
import sa.masrouf.core.ask.PeriodLabel
import sa.masrouf.core.ask.Subject
import sa.masrouf.core.ask.Topic
import sa.masrouf.core.model.Transaction
import sa.masrouf.core.money.Money

/**
 * A question, and the figure it comes to.
 *
 * The screen's job is to leave no room for the reader to misread which question was
 * answered. Every answer states its own subject and period back - "petrol · last
 * month" above the number - because a figure alone is a figure the reader cannot
 * check, and this app's whole argument is that its numbers can be checked.
 *
 * Four states, and three of them are not "a number". "I did not understand", "no
 * shop by that name" and "nothing in that period" are different sentences, and
 * collapsing any of them into "0 riyals" would be the app stating something untrue
 * with a straight face.
 *
 * The rows underneath are the same [TransactionRow] the history uses, with the same
 * tap: the answer is not a summary of the records, it IS the records.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AskScreen(
    question: String,
    state: AskState,
    currencyLabel: String,
    cards: CardLookup,
    salary: Money?,
    contentPadding: PaddingValues,
    /**
     * Hoisted, like the income list's. The destinations swap inside an
     * `AnimatedContent`, which keeps no state of its own, so an unhoisted list
     * went back to the top on every switch - the answer survived in the view model
     * and the reader's place in it did not.
     */
    listState: LazyListState,
    onQuestionChanged: (String) -> Unit,
    onAsk: () -> Unit,
    onRefile: (Transaction) -> Unit,
    onSortBy: (AskSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    val examples = listOf(
        // First on purpose. Filing is what this app is actually used for, and this
        // is the only entry point to the work that is not "guess which month".
        R.string.ask_example_unfiled,
        R.string.ask_example_fuel_month,
        R.string.ask_example_coffee_year,
        R.string.ask_example_largest,
        R.string.ask_example_count,
        R.string.ask_example_merchant,
    )

    val focus = LocalFocusManager.current

    LazyColumn(
        state = listState,
        // `enableEdgeToEdge` means the window no longer resizes for the keyboard,
        // so `adjustResize` in the manifest does nothing and the Scaffold's insets
        // carry the bars only. Without this the answer draws behind the keyboard
        // and no amount of scrolling clears it. The entry sheet was fixed for the
        // same reason and says so.
        modifier = modifier.fillMaxWidth().imePadding(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = question,
                onValueChange = onQuestionChanged,
                modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_PADDING),
                label = { Text(stringResource(R.string.ask_hint)) },
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search,
                ),
                // The question is committed; nothing more is being typed, and the
                // answer this screen exists to show would otherwise land under a
                // keyboard covering half of it.
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = {
                        focus.clearFocus()
                        onAsk()
                    },
                ),
            )
        }

        // The examples are not decoration. The vocabulary this understands is closed
        // and finite, and showing it is the honest way to say so - a blank box
        // promises anything and then refuses most of it.
        if (state is AskState.Unasked || state is AskState.NotUnderstood) {
            item {
                Column(
                    modifier = Modifier.padding(horizontal = PANEL_PADDING),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state is AskState.NotUnderstood) {
                        Text(
                            text = stringResource(R.string.ask_not_understood),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Text(
                            text = stringResource(R.string.ask_not_understood_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = stringResource(R.string.ask_examples_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() },
                    )
                    // Vertical spacing as well as horizontal: these labels are
                    // twenty-odd characters and always wrap, and wrapped chips with
                    // no vertical gap touch each other.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (example in examples) {
                            val text = stringResource(example)
                            AssistChip(
                                onClick = {
                                    focus.clearFocus()
                                    onQuestionChanged(text)
                                    onAsk()
                                },
                                label = { Text(text) },
                                // M3's chip is 32dp. Every other chip in this app
                                // raises the floor to 48 and says why; this screen
                                // was written after those and inherited none of it.
                                modifier = Modifier.heightIn(min = 48.dp),
                            )
                        }
                    }
                }
            }
        }

        if (state is AskState.Thinking) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(PANEL_PADDING),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.ask_thinking),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        if (state is AskState.Answered) {
            // No figure over an empty answer. A display-size 0.00 above "no shop by
            // that name" is the screen stating something untrue in its largest type,
            // which is the thing this file's own documentation forbids.
            if (!state.answer.isEmpty) {
                item { AnswerHeadline(state.answer, currencyLabel) }
            }

            if (state.answer.isEmpty) {
                item {
                    Text(
                        text = stringResource(
                            if (state.answer.subjectSeenEver) R.string.ask_nothing_here
                            else R.string.ask_no_such_merchant,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PANEL_PADDING)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Over the rows, not under them: the complaint that produced it was
            // "I scrolled and never found it", and a control below a two-hundred-row
            // list is a control nobody reaches. Shown only when the order can
            // actually change something, which two rows cannot.
            if (state.answer.rows.size > 1) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PANEL_PADDING, vertical = 4.dp)
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (option in AskSort.entries) {
                            val selected = state.answer.sort == option
                            FilterChip(
                                selected = selected,
                                onClick = { onSortBy(option) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (option) {
                                                AskSort.NEWEST -> R.string.ask_sort_newest
                                                AskSort.LARGEST -> R.string.ask_sort_largest
                                            },
                                        ),
                                    )
                                },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Filled.Check, contentDescription = null) }
                                } else {
                                    null
                                },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .semantics { role = Role.RadioButton },
                            )
                        }
                    }
                }
            }

            items(state.answer.rows, key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    currencyLabel = currencyLabel,
                    cards = cards,
                    salary = salary,
                    onRefile = { onRefile(transaction) },
                )
            }

            if (state.answer.moreRows > 0) {
                item {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = PANEL_PADDING))
                    Text(
                        text = stringResource(R.string.ask_more_rows, state.answer.moreRows.toString()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(PANEL_PADDING),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * The figure, and the question it answers, in that order of size.
 *
 * The second line is not a subtitle - it is what makes the first line checkable.
 */
@Composable
private fun AnswerHeadline(answer: AskAnswer, currencyLabel: String) {
    val spokenCurrency = stringResource(R.string.currency_spoken)
    Column(
        // Merged and announced as one thing. On the three inner Texts the live
        // region read out "6196.18" - no subject, no unit, no period - which is
        // precisely the bare figure this screen exists not to show.
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PANEL_PADDING)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = answerSubtitle(answer),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        when (answer.query.measure) {
            Measure.COUNT -> {
                Text(
                    text = answer.count.toString(),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    text = stringResource(R.string.ask_count_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Measure.LARGEST -> {
                val largest = answer.largest?.amount ?: Money.ZERO
                Text(
                    text = largest.forDisplay(currencyLabel),
                    style = MaterialTheme.typography.displaySmall,
                    // U+20C1 has no spoken name in any engine yet, so the figure is
                    // read with the currency written out.
                    modifier = Modifier.semantics { contentDescription = largest.forSpeech(spokenCurrency) },
                )
                Text(
                    text = stringResource(R.string.ask_largest_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Measure.TOTAL, Measure.LIST -> {
                Text(
                    text = answer.total.forDisplay(currencyLabel),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics {
                        contentDescription = answer.total.forSpeech(spokenCurrency)
                    },
                )
            }
        }
    }
}

/**
 * "Spending · petrol · last month", built from the QUERY rather than from the text
 * the user typed.
 *
 * The subject is the load-bearing third of it. Without it, "how much on petrol this
 * month" and "how much at Amazon this month" print an identical line over two
 * different figures, and the line whose entire job is to say which question was
 * answered answers neither.
 */
@Composable
private fun answerSubtitle(answer: AskAnswer): String {
    val flow = stringResource(
        if (answer.query.flow == Flow.INCOME) R.string.ask_answered_income
        else R.string.ask_answered_spending,
    )
    return listOf(flow, subjectLabel(answer), periodLabel(answer))
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/**
 * What the question was about, in the reader's language.
 *
 * A merchant is the only one that is the user's own text rather than the app's, so
 * it is bidi-isolated: a shop whose name starts with digits - "21192 CENTREPOINT"
 * is a real one here - reorders in an Arabic sentence otherwise.
 */
@Composable
private fun subjectLabel(answer: AskAnswer): String = when (val subject = answer.query.subject) {
    is Subject.Everything -> ""
    is Subject.OfCategory -> stringResource(subject.category.labelRes)
    is Subject.OfTopic -> stringResource(
        when (subject.topic) {
            Topic.FUEL -> R.string.topic_fuel
            Topic.COFFEE -> R.string.topic_coffee
            Topic.DELIVERY -> R.string.topic_delivery
            Topic.PHARMACY -> R.string.topic_pharmacy
        },
    )
    is Subject.AtMerchant -> subject.keyword.bidiIsolated()
    is Subject.Unfiled -> stringResource(R.string.subject_unfiled)
}

@Composable
private fun periodLabel(answer: AskAnswer): String {
    val period = answer.query.period
    return when (period.label) {
        PeriodLabel.ALL_TIME -> stringResource(R.string.period_all_time)
        PeriodLabel.TODAY -> stringResource(R.string.period_today)
        PeriodLabel.YESTERDAY -> stringResource(R.string.period_yesterday)
        PeriodLabel.THIS_WEEK -> stringResource(R.string.period_this_week)
        PeriodLabel.THIS_MONTH -> stringResource(R.string.period_this_month)
        PeriodLabel.LAST_MONTH -> stringResource(R.string.period_last_month)
        PeriodLabel.THIS_YEAR -> stringResource(R.string.period_this_year)
        PeriodLabel.LAST_YEAR -> stringResource(R.string.period_last_year)
        // The month and the year say themselves, in the reader's own calendar
        // formatting rather than as two ISO dates.
        PeriodLabel.NAMED_MONTH -> period.from?.monthLabel().orEmpty()
        PeriodLabel.NAMED_YEAR -> period.from?.year?.toString().orEmpty()
        PeriodLabel.LAST_N_DAYS -> {
            val days = period.from?.let { from ->
                period.toExclusive?.toEpochDay()?.minus(from.toEpochDay())?.toInt()
            } ?: 0
            stringResource(R.string.period_last_n_days, days.toString())
        }
    }
}
