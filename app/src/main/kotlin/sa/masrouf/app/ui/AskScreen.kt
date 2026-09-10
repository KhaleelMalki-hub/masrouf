package sa.masrouf.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
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
import sa.masrouf.core.model.CardKind
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
    cardBanks: Map<String, String>,
    cardKinds: Map<String, CardKind>,
    salary: Money?,
    contentPadding: PaddingValues,
    onQuestionChanged: (String) -> Unit,
    onAsk: () -> Unit,
    onRefile: (Transaction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val examples = listOf(
        R.string.ask_example_fuel_month,
        R.string.ask_example_coffee_year,
        R.string.ask_example_largest,
        R.string.ask_example_count,
        R.string.ask_example_merchant,
    )

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
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
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { onAsk() },
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
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (example in examples) {
                            val text = stringResource(example)
                            AssistChip(
                                onClick = {
                                    onQuestionChanged(text)
                                    onAsk()
                                },
                                label = { Text(text) },
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
            item { AnswerHeadline(state.answer, currencyLabel) }

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

            items(state.answer.rows, key = { it.id }) { transaction ->
                TransactionRow(
                    transaction = transaction,
                    currencyLabel = currencyLabel,
                    cardBanks = cardBanks,
                    cardKinds = cardKinds,
                    salary = salary,
                    onRefile = { onRefile(transaction) },
                )
            }

            if (state.answer.moreRows > 0) {
                item {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.ask_more_rows, state.answer.moreRows),
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
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = PANEL_PADDING),
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
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                Text(
                    text = stringResource(R.string.ask_count_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Measure.LARGEST -> {
                Text(
                    text = (answer.largest?.amount ?: Money.ZERO).forDisplay(currencyLabel),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
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
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
        }
    }
}

/** "Spending · petrol · last month", built from the query rather than from the text. */
@Composable
private fun answerSubtitle(answer: AskAnswer): String {
    val flow = stringResource(
        if (answer.query.flow == Flow.INCOME) R.string.ask_answered_income
        else R.string.ask_answered_spending,
    )
    val period = periodLabel(answer)
    return listOf(flow, period).filter { it.isNotBlank() }.joinToString(" · ")
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
            stringResource(R.string.period_last_n_days, days)
        }
    }
}
