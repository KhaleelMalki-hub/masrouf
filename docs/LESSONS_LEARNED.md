# Lessons Learned

Append-only ledger of mistakes made in this repo and the rule that follows each.
**Read this at the start of work and apply every rule it contains.** Append a new entry
whenever a mistake is detected (a user correction, a CI failure on code just written, a
false closure claim, a wrong tool path, a credential-boundary push, a duplicate, a hung
wait). Append only — never edit historical entries; corrections add a new entry that
references the old one.

## Format

### YYYY-MM-DD — <one-line title>
**Mistake:** … **Why:** … **Rule:** … **How to apply:** … **Source:** …

## Lessons

<!-- oldest first; append new entries at the bottom -->

### 2026-08-31 — A test pinned the defect it was named after
**Mistake:** `CapturePipelineTest.alrajhi credit card settlement` asserted
`BILL_PAYMENT` for a message the fixture's own doc comment called "Credit card
settlement payment". The assertion held the bug in place for as long as the fixture
existed, and 43 settlements were counted as spending on top of the purchases that had
already built the card balance.
**Why:** The fixture was written by reading what the app *did*, not by deciding what
the message *means*. A green test then made the behaviour look intended.
**Rule:** When a fixture's name or doc comment describes the real-world event, the
assertion must agree with that description, not with current behaviour. If they
disagree, the assertion is the thing to change. Read a fixture's prose before trusting
the test that consumes it.
**How to apply:** Any time a test is the reason to believe behaviour is correct — and
whenever adding a fixture whose name states what the message is.
**Source:** session 2026-08-31, `OwnMoneyTest`

### 2026-08-31 — SQL LIKE cannot count Arabic families
**Mistake:** Estimated that 83 rows would move out of `BILL_PAYMENT`; 97 moved. The
14 unaccounted rows were card settlements the bank spells `إئتمانية` (two hamzas)
where the query looked for `ائتمانية`, plus a `سداد فاتورة | بطاقة:2383;فيزا`
template that no hand-written LIKE had enumerated.
**Why:** `LIKE` compares raw code points. The app never does: every match runs on
`ArabicText.foldForMatching`, which collapses أ/إ/آ→ا, ى→ي, ة→ه and strips
diacritics and punctuation. A LIKE-based survey is therefore a different question
from the one the app asks, and it always undercounts.
**Rule:** Never size or scope an Arabic-matching change with `LIKE` on raw text.
Estimate with the folding the app uses, and confirm the real figure by diffing the
database before and after — the classifier is the only authority on what it matches.
**How to apply:** Any corpus survey, migration sizing, or "how many rows are
affected" question touching Arabic message bodies.
**Source:** session 2026-08-31, before/after diff of the device database

### 2026-08-31 — Teaching the classifier does not fix the history
**Mistake:** Assumed a rule added to `IntentClassifier` would correct stored rows on
the next launch, because the app re-reads the SMS inbox at every launch.
**Why:** `TransactionDao.insert` uses `OnConflictStrategy.IGNORE` against a unique
fingerprint. Re-reading a message that is already stored is a no-op by design — the
dedup that stops double-counting also stops re-classification. Thirty-two rows still
said `BILL_PAYMENT` months after the rule that would have caught them was added.
**Rule:** A classifier or gate change fixes only messages that have not arrived yet.
Anything already stored needs a maintenance pass in `MasroufApp.runMaintenance`,
gated on `Preferences.maintenanceVersion`. The pass must call the classifier rather
than carry its own copy of the wordings, or the two lists will drift.
**How to apply:** Every change to `IntentClassifier`, `MessageGate`, or any rule that
decides what a stored row means. Ask "what happens to the rows already in the
database?" before calling the change done.
**Source:** session 2026-08-31, maintenance pass 4 (`retypeOwnMoney`)

### 2026-08-31 — gradlew has no JDK on this machine's default PATH
**Mistake:** `./gradlew :core:test` failed with "Unable to locate a Java Runtime",
which reads like a broken project rather than a broken shell.
**Why:** No system JDK is installed; the JDK is Homebrew's `openjdk@21` and nothing
puts it on `PATH` or sets `JAVA_HOME`.
**Rule:** Prefix every Gradle invocation with
`export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home`
in the same command, since the shell does not persist between calls.
**How to apply:** Any `./gradlew` command in this repo.
**Source:** session 2026-08-31

### 2026-08-31 — One movement, two messages, only one of them fixed
**Mistake:** Filed the credit-card settlement family as done after correcting the
card that was *paid*. The card that *paid it* sends its own message —
`شراء إنترنت | بطاقة: فيزا الائتمانية XX9994 | لدى: SADAD payment` — which names no
destination and reads as an ordinary online purchase. 109,000 riyals stayed in the
spending total on the other side of a fix that was reported as complete.
**Why:** The fix was scoped to the wording that named the defect (`سداد`), not to the
*event*. A transfer between two of the owner's own accounts always generates two
messages from two banks, and correcting either one alone leaves the movement
half-counted.
**Rule:** When a fix concerns money moving between two places the user owns, find
both legs before calling it done. Ask "which other account sent or received this, and
what did *its* bank say?" — then go looking for that message in the corpus.
**How to apply:** Any change to how transfers, settlements, wallet top-ups, or
own-account movements are classified.
**Source:** session 2026-08-31, maintenance pass 5

### 2026-08-31 — A merchant name is not a destination
**Mistake:** Nearly wrote a rule making every `SADAD payment` an own transfer. It
would have erased 27 genuine utility bills from 2017–2019 that carry the same
merchant on the same rail.
**Why:** SADAD is a payment *rail*, not a payee. The merchant field says how the
money travelled, not where it went, so the same string covers an electricity bill and
a credit-card settlement.
**Rule:** Before keying a rule on a merchant name, check what else in the history
carries that same name. For a payment rail (SADAD, STC Pay, Apple Pay, a bank's own
transfer service) the name is never sufficient on its own — find a second signal in
the message, and verify the split over the whole corpus with correct Arabic folding.
**How to apply:** Any merchant-keyed classification rule.
**Source:** session 2026-08-31, `OwnMoneyTest`

### 2026-08-31 — Wrote an inference into a comment as though it were a fact
**Mistake:** Recorded in `CardIssuers` that barq's 7285, 2166 and 9941 were "three
fragments of one balance rather than three cards". They are three separate cards.
The comment was an inference from their overlapping date ranges, written in the same
voice as the entries around it that came from the owner directly.
**Why:** The file's other entries were owner-stated facts, so an inference placed
among them inherited their authority. Nothing in the wording said which was which.
**Rule:** In a comment, mark the provenance of anything not verified: "confirmed by
the owner", "read off the template", "inferred from X, unverified". A future reader
cannot tell a fact from a guess once both are written as prose, and a guess in a
comment outlives the session that made it.
**How to apply:** Any explanatory comment on a constant whose values come from
outside the code — user statements, external systems, observed data.
**Source:** session 2026-08-31, corrected in the same session by the owner

### 2026-08-31 — A greedy rule two lines too early
**Mistake:** `Rule(PURCHASE, listOf("سحب", "بطاق"))` was tested before the two ATM
rules, so every machine withdrawal that names the card it was made with -
`سحب نقدي بالريال - صراف الأهلي | بطاقة مدى *2907` - was filed as a card purchase.
107 withdrawals worth 193,452 riyals, over nine years, in the app's oldest rule set.
**Why:** The rule was written for one real message (`سحب ... بطاقة 9552* من SHBABIK
RESTAURANT`) and placed where it worked for that one. Its tokens are a strict subset
of the withdrawal messages' tokens, so it could only ever win against them - which
the ordering comment above it asserted was correct without checking.
**Rule:** In a first-match rule list, a new rule whose token set is a SUBSET of an
existing rule's must go after it, never before. Check both directions when placing
one: what this rule will now claim, and what will now be claimed from it. Then count
the affected rows in the real corpus, both the set that moves and the set that must
not.
**How to apply:** Every addition or reordering in `IntentClassifier.RULES`.
**Source:** session 2026-08-31, `CashOutTest`, maintenance pass 6

### 2026-08-31 — A regex may begin its match inside another number
**Mistake:** `CURRENCY_AFTER` had no boundary before its number, so when a bank sent
its own floating-point artifact - `الصرف المتبقي 21684.91999999999999 SAR` - the
engine slid past the digits that could not be followed by SAR and matched
`91999999999999 SAR` from inside the balance. An 8,315-riyal deposit was stored as
ninety-two trillion, and every incoming total in the app was that number.
**Why:** A regex will start anywhere that makes the rest of the pattern fit. Without
an anchor, "a number next to a currency" also means "the tail of a number next to a
currency". `BARE_DECIMAL` in the same file already had the lookarounds; the other
two patterns were written later and did not.
**Rule:** Any pattern that extracts a number from free text needs a boundary on both
ends - `(?<![\d,])(?<!\d\.)` before, `(?![\d,])(?!\.\d)` after. Not `(?![\d.,])`: a
bare full stop is a sentence ending, and blocking it loses every English message
that puts the amount last.
**How to apply:** Every numeric extraction pattern, in this repo and any other.
**Source:** session 2026-08-31, `AmountVsBalanceTest`, maintenance pass 10

### 2026-08-31 — A guard that fires late is worse than no guard
**Mistake:** Added `المتبقي` to the disqualifying prefixes so a credit limit could
not be read as an amount. Two tests went red: for those messages the balance was
the ONLY candidate, so disqualifying it turned a wrong amount into no capture at
all. The real defect was upstream - the extractor could not see the true amount,
because `BARE_DECIMAL` capped the integer part at three digits unless commas were
present, and `مبلغ 8500` has neither.
**Why:** The fix was aimed at the symptom that was visible (a balance winning)
rather than at why it had no competition (the amount was invisible).
**Rule:** When a wrong candidate is being chosen, ask what the right candidate is
and whether the code can see it at all, BEFORE suppressing the wrong one.
Suppression with nothing to replace it converts a wrong answer into a missing one,
and a missing transaction is not obviously better than a wrong figure.
**How to apply:** Any ranking or scoring change - parsers, matchers, classifiers.
**Source:** session 2026-08-31, `AmountExtractor.AMOUNT_LABEL`

### 2026-08-31 — Simulate a risky repair over the whole corpus first
**Practice worth keeping, not a mistake.** Before changing how amounts are read,
both the old and the new logic were run over all 22,037 stored message bodies and
the results diffed: 1,287 amounts changed, 275 messages became readable, 0 became
unreadable, and a sample of every changed family was inspected by hand. The first
attempt showed 159 losses, which is what led to the `مبلغ` label signal; the second
showed 16, all one-time passwords, which is what led to the trailing-dot boundary.
Neither would have been found by unit tests written from the same imagination that
wrote the patch.
**Rule:** A change to how stored data is interpreted gets simulated against the real
corpus, old versus new, with the losses inspected individually - before it is
written into the code, not after.
**How to apply:** Parser, extractor, classifier and migration changes.
**Source:** session 2026-08-31

### 2026-08-31 — A keyword short enough to match a truncation matches everything else too
**Mistake:** A domestic-labour recruiter arrives from one terminal as "NTERNATIO" -
the card network cut the FIRST letter, and `MerchantMatch`'s truncation rule only
forgives a missing tail. Added "NTERNATIO" as a keyword to reach it. Because
keywords of four characters or more match as substrings, it then also matched every
"INTERNATIONAL ..." in the history and took a creative agency, a regions firm and
Alshaya to fees. An existing test caught it; the comment two lines above the rule
had warned about the same thing.
**Why:** Reaching a truncated name means shortening the keyword, and a shorter
keyword is a substring of more names. The two goals are in direct opposition and
the trade was not looked at before the keyword was written.
**Rule:** Before adding a merchant keyword, run it against the whole merchant list
and read what else it takes. If reaching one spelling costs a keyword that captures
unrelated companies, do not add it - the app already has per-merchant filing by the
user, which is exact and permanent. Record the ambiguity in a test so the next
person does not retry the same keyword.
**How to apply:** Every addition to `CategoryGuess.RULES`.
**Source:** session 2026-08-31, `OwnerNamedMerchantsTest`

### 2026-08-31 — A fixture that makes a gap look covered
**Mistake:** Believed the gate caught activation codes, because a fixture named
`SNB_ACTIVATION_CODE` exists and passes. It holds one bank's Arabic wording
("لا تشارك رمز التفعيل"). A different bank's English template - "Requested an
activation code 2470 for One Time Bill Payment / Amount: 500 SAR" - shares neither
phrase, and three of those sat stored as confirmed bill payments. A fourth wording,
"رمز مؤقت", authorised a 25,000-riyal transfer and was stored as that transfer.
**Why:** A green test for a phrase reads as coverage of the concept. The fixture's
NAME described the concept; its CONTENT was one instance of it.
**Rule:** A gate marker is coverage of one spelling, never of an idea. For any
safety-critical marker list, sweep the real corpus for the CONCEPT - every word a
bank might use for a code, in both languages, case-sensitively - and check each hit
against the live gate. Name fixtures after the wording they carry, not the category
they belong to, so the next reader cannot mistake one for the other.
**How to apply:** Every change to `MessageGate`, and any review that asserts "no
credential reaches storage".
**Source:** session 2026-08-31, council security review, maintenance pass 12

### 2026-08-31 — A blanket guard where the reasons differ
**Mistake:** `purgeRejectedBodies` deleted any row the gate now rejects, for any of
its three reasons, ignoring whether the user had filed it - while every other
maintenance pass in the same file guarded `category_source = MANUAL`. The obvious
fix, adding the same guard, would have been wrong: it would have let a
one-time-password body survive on disk because the user had once categorised the
row.
**Why:** The guard question is not "does this pass touch user decisions" but "what
is this rejection FOR". A credential is a safety fact and outranks a filing; a
marketing marker is a judgement about meaning and does not.
**Rule:** When adding a protective guard to a bulk operation, split by the REASON
the row was selected before deciding what the guard protects. A guard that is right
for one reason can be exactly wrong for another.
**How to apply:** Any bulk delete or rewrite driven by a classifier with more than
one verdict.
**Source:** session 2026-08-31, `PurgeGuardTest`

### 2026-08-31 — A guard that reads as present and does nothing
**Mistake:** Added `(?![*\d])` to a pattern shaped `^الى\s*:?\s*(?![*\d])(.+)$` to stop
an account number being read as a party. It did not work: with a plain `?` the
engine hands the colon back to satisfy the lookahead, matches from the colon, and
captures ":3016". The guard was in the source, was reviewed, and had no effect.
**Why:** A negative lookahead only constrains the position the engine happens to
be at. Any optional or greedy quantifier before it gives the engine somewhere else
to stand.
**Rule:** A lookahead guard placed after `\s*`, `?` or any other optional token
needs possessive quantifiers - `\s*+`, `?+` - or the guard is decorative. Prove it
with a test that feeds the exact string it is meant to refuse, never by reading it.
**How to apply:** Every regex guard in `SaudiBanks` and `AmountExtractor`.
**Source:** session 2026-08-31, council code-logic review, `NamedCounterpartyTest`

### 2026-08-31 — Fixed one profile of four and reported the family fixed
**Mistake:** Gave SNB's counterparty patterns the account guard and called the
2,014-row repair done. `reparseStoredBodies` tries EVERY bank profile and keeps
whichever reads the most, so D360's unguarded patterns claimed other banks' bodies
and wrote the account number straight back into the field the repair had cleared.
710 of the 2,014 came back, and the commit message said otherwise.
**Why:** The fix was scoped to the profile whose template produced the example.
The mechanism that consumes these patterns is profile-agnostic, and that was not
carried into the fix.
**Rule:** When several implementations of one interface are all tried and the best
answer wins, a guard belongs on all of them or on the chooser. Fixing one and
measuring the result on the same data that motivated it will show success.
**How to apply:** Any change to one `BankProfile`'s patterns.
**Source:** session 2026-08-31, maintenance pass 13

### 2026-08-31 — A four-letter keyword is a substring of somebody
**Mistake:** Added `"REEFI"` for a linens shop and `"FLYIN"` for flyin.com.
`MerchantMatch` takes any keyword of four characters or more as a substring, so
they also matched "Al Saj Al Reefi Restau" (29 rows of a restaurant → shopping) and
"Flying Tiger Copenhagen" (a stationery chain → travel). The refile pass committed
both to the database before anyone looked. The file's own comment claimed the new
keywords had been "checked against the whole merchant list"; these two were not.
**Why:** The check was done for the travel stems and the claim was written once,
over the whole block. Later additions inherited a sentence nobody re-earned.
**Rule:** Run every new keyword against the full distinct-merchant list before
adding it, one at a time, and read what else it takes. الريفي is an ordinary Arabic
word - no stem of it can be safe. Where a short keyword is unavoidable, order it
after the specific names it would otherwise swallow and rely on the exact-glued
pass to protect them.
**How to apply:** Every addition to `CategoryGuess.RULES`.
**Source:** session 2026-08-31, council code-logic review

### 2026-08-31 — A true figure with no age reads as a current one
**Mistake:** A card tile showed a remaining-allowance figure far below the card's
ceiling in September. The figure was correct — on 2 April 2026, the last day that
card sent a message. The owner had paid the card off since, saw the tile, and asked
why the app thought he still owed. The date was on the tile, in the faintest style
available, and read as a footnote rather than as a caveat.
**Why:** The app renders what it last heard, and every other number beside it is
from today. Correctness was checked; currency was not, because the value is not
wrong at any point — it just stops describing now.
**Rule:** Any figure read from an external source and cached needs its age in the
same glance as its value, and past a threshold the wording must change, not only
the timestamp. "حتى 02/04" is a fact; "آخر خبر منها 02/04" is a caveat, and only
the second gets read.
**How to apply:** Any surface showing a last-known reading — balances, limits,
rates, anything the app does not compute itself.
**Source:** session 2026-08-31, owner's report on card 8134

### 2026-08-31 — Personal facts had no home but the source file
**Mistake:** The owner's name went into `AccountOwner` and his cards' credit limits
into `CardsPanel`, both production source, in a PUBLIC repository - and the same
figures were quoted again in the comments beside them. The project's own Privacy
section already forbade a real name in a fixture; nobody had asked what a
production constant was.
**Why:** The values are needed at runtime and there was no other place to put
them, so they went where the code that reads them lives. "It has to work" quietly
outranked a rule that had only ever been stated about test data.
**Rule:** A fact about a real person belongs in `local.properties` (gitignored) and
reaches the code through `BuildConfig`, with an empty default that makes the
feature know less rather than fail. Tests configure placeholders and assert the
parser. A comment quoting the value is the same leak as the value.
**How to apply:** Any constant sourced from the user rather than from the domain -
names, limits, account identifiers, thresholds tuned to one person.
**Source:** session 2026-08-31, council security review

### 2026-08-31 — Ran a task documented as destructive against the owner's phone
**Mistake:** Ran `:app:connectedDebugAndroidTest` on the owner's personal device to
exercise a query against real SQLite. Gradle uninstalls the app when that task
finishes — unconditionally, with no flag to stop it — so the app and its database
vanished from his phone mid-session. He noticed before I did.
**Why:** `CLAUDE.md` says of that exact command "needs a device; it wipes the
emulator's app data". I read it at the start of the session and carried away
"emulator", so the warning attached to a machine that was not the one connected.
The backup I took beforehand shows the risk was recognised and then walked past:
taking a precaution is not the same as heeding a warning.
**Rule:** Before running any command a document calls destructive, name the target
out loud. If the target is the owner's own device and the command is not strictly
necessary there, do not run it — an emulator exists for this. Where it is
necessary: back up the database AND `shared_prefs` first, and rehearse the restore
before the command, not after.
**How to apply:** `connectedDebugAndroidTest`, `adb uninstall`, `pm clear`, `db
reset`, anything with `--rerun` against live data.
**Source:** session 2026-08-31, recovered from `backup_before_androidtest.db`

### 2026-08-31 — A test double that re-implements the thing under test
**Mistake:** `FakeDao` re-implemented the income aggregate in Kotlin - same
filters, same month key, same two conditional sums - so `IncomeSeriesTest` proved
the copy. A reviewer replaced the bonus column of the real `@Query` with a literal
zero and the entire suite stayed green: every figure on the income screen came
from a string nothing read back.
**Why:** A fake that returns canned rows is a stub; a fake that recomputes the
answer is a second implementation, and a test against it asserts that two pieces of
my own reasoning agree. Room's KSP accepts any valid SQL, so nothing else looks.
**Rule:** A test double may return data. It may not re-derive an answer the
production code derives. Where the logic lives in SQL, one test must reach real
SQLite - an instrumented Room test against an in-memory database - and it must be
proved by mutating the query and watching it go red.
**How to apply:** Every `@Query` carrying logic: aggregates, CASE expressions,
date arithmetic, anything beyond a plain select.
**Source:** session 2026-08-31, `IncomeQueryTest`, mutation reproduced and reverted

### 2026-08-31 — Recommended a pattern without checking the specification
**Mistake:** The owner asked why the navigation bar was not floating like Google
Photos. I answered confidently, refused floating for good reasons, and then
recommended hiding it on scroll instead — presenting that as the M3-compliant way
to get the height back. He agreed and it shipped. The first screenshot after it
showed one small drag taking the bar away, and with it the only route to the other
destination. M3 hides APP bars on scroll and never the navigation bar, which is
the difference between chrome you may dismiss and the way out of the screen.
**Why:** Two-thirds of the answer was checked — what a navigation bar is for, why
floating was wrong here — and the third part was invented to have something to
offer instead of "leave it". Confidence carried over from the checked parts.
**Rule:** When declining a request, do not substitute an alternative you have not
verified to the same standard as the refusal. "Leave it as it is" is a complete
answer. If an alternative is offered, say which part is checked and which is a
suggestion, and put it on a screen before calling it a recommendation.
**How to apply:** Any design answer that refuses one thing and proposes another.
**Source:** session 2026-08-31, reverted the same evening


### 2026-08-31 — A fixed height clips; it does not grow
**Mistake:** Gave a row of card tiles `Modifier.height(132.dp)` to make them uniform. A card that gained a credit limit clipped its last line; raised to 150dp, a card that gained "مسددة بالكامل" wrapped and clipped again.
**Why:** A hand-typed height encodes the tallest content at the moment it is typed. Every later feature that adds a line invalidates it, silently and only on the device.
**Rule:** Make sibling elements uniform by measuring them, not by typing a number: `Modifier.height(IntrinsicSize.Max)` on the parent. If that means giving up a lazy container, give it up - laziness buys nothing under a few dozen children.
**How to apply:** Any time "make these the same size" is the requirement.
**Source:** CardsPanel.kt, this session.

### 2026-08-31 — `horizontalScroll` starts at the left, in both directions
**Mistake:** Replaced a `LazyRow` with `Row` + `horizontalScroll` and the Arabic screen opened half-way into its first card.
**Why:** `LazyRow` starts at the start of the content in the current layout direction. `horizontalScroll` starts at scroll offset 0, which is the LEFT edge whichever direction the content runs, so under RTL it opens at the END of the list.
**Rule:** Swapping a lazy container for a scrolled one loses more than laziness. In an RTL app, scroll to `maxValue` on first composition, and verify on a device in Arabic - the compiler and every unit test are silent about it.
**How to apply:** Any `horizontalScroll` in an app with an RTL locale.
**Source:** CardsPanel.kt, this session.

### 2026-09-01 — A field pattern is a bet on what ends a field
**Mistake:** Every merchant and party pattern in the app was anchored with `(?m)^`, and normalisation folded carriage returns into spaces. 588 records - 330,211 riyals - were stored with an amount and no party, unfileable, while the name sat in the body untouched.
**Why:** "One field per line" was read off the senders that use newlines. Four others end a field with a carriage return, the literal characters `^M`, a pipe, or a run of padding spaces, and a line-anchored pattern fails on all of them silently - there is no error, just a null where a name belongs.
**Rule:** Normalise every field boundary a sender uses into one boundary, once, before parsing - do not teach each pattern a new separator. And when a whole family of records is missing the same field, suspect the separator before the pattern.
**How to apply:** Any parser reading labelled fields out of machine-written text.
**Source:** ArabicText.FIELD_BREAK, maintenance pass 15.

### 2026-09-01 — Measure a parser change on the real corpus before writing it
**Mistake:** None this time, and that is the point: the fix was simulated over all 12,748 stored bodies first, and the first two versions of it were wrong - one filed 58 cashback notices as purchases at "بطاقة **3396", another truncated existing merchant names by treating two spaces as a boundary.
**Why:** A regex that satisfies the sample you wrote it against tells you nothing about the 12,000 you did not. The corpus is on the device and costs one query to read.
**Rule:** Before changing an extractor, run old and new over every stored body and count three things: GAINED, CHANGED, LOST. Ship when gained is large, changed is explainable line by line, and lost is understood - not when the new test passes.
**How to apply:** Any change to a pattern that has already run against real data.
**Source:** the 337-gain / 89-change / 2-loss measurement behind pass 15.

### 2026-09-01 — A sender with no parser is invisible to every query over the database
**Mistake:** Four separate deep passes over this history - all of them querying the stored transactions - reported the history as complete. A whole wallet, STC Pay, had sent 4,446 messages between 2019 and 2026 and produced not one row, because no profile claimed the sender. The owner asked "why did none of this show up earlier", and he was right to.
**Why:** Every question was asked of the data that made it in. Missing data has no rows to be counted, no null to be found, no anomaly to be spotted - it is absent, and absence looks exactly like "nothing happened".
**Rule:** To find what a capture pipeline is missing, compare the SOURCE against the store, never the store against itself. Enumerate the senders in the raw inbox, subtract the ones the app understands, and count what is left - by message, by amount, by year.
**How to apply:** Any ingestion pipeline, at the first sign that a total looks low or a history looks quiet.
**Source:** STC Pay, 650,280 riyals miscounted and ~195,000 never recorded.

### 2026-09-01 — Whose name it is decides what the name means
**Mistake:** An outgoing transfer that mentions the account holder was demoted to "money between his own accounts". Every outgoing transfer mentions him - he is the sender - so 68 wage transfers to domestic staff abroad, 94,126 riyals, left his spending entirely.
**Why:** The rule tested for the name anywhere in the body, which conflated the two roles a name can play. It survived because the templates it was written against happened to name him only as the beneficiary.
**Rule:** When a rule keys on an identity, key it on the identity IN A ROLE - beneficiary, sender, payer - and drop the lines that carry the other role before matching. An identity check with no role is a coin flip that agrees with you at first.
**How to apply:** Any rule that reads "if this names X".
**Source:** IntentClassifier.withoutSenderLines.

### 2026-09-01 — `\b` does not exist between an Arabic letter and a colon
**Mistake:** The sender-line guard `^\s*(?:اسم\s+المرسل|...)\b` matched nothing at all, so the fix above did nothing until a debug print showed it.
**Why:** Java defines a word boundary over `[A-Za-z0-9_]`. Between an Arabic letter and a colon both sides are non-word, so there is no boundary and `\b` fails - silently, as a guard that reads as present and does nothing. The same shape as the lookahead defect recorded earlier in this file.
**Rule:** In a mixed-script regex, put `\b` only on the Latin alternatives. Anchor Arabic ones with an explicit lookahead for what actually follows, and print the match result once before believing a guard works.
**How to apply:** Any regex over Arabic text that reaches for a word boundary.
**Source:** IntentClassifier.SENDER_LINE.

### 2026-09-01 — A permission check that returns quietly is a feature that does not exist
**Mistake:** Every path that reads the SMS inbox checked READ_SMS and returned silently when it was not granted. It was not granted on the owner's phone, so the launch catch-up documented as "a miss costs one launch" had never run once, and a one-off repair that re-reads the whole inbox stamped itself complete having imported nothing.
**Why:** The check was written as a guard against crashing, not as a report of a capability the feature needs. Nothing downstream could tell "there was nothing to import" from "I was not allowed to look".
**Rule:** A precondition failure must be visible in whatever records the work: leave the retry stamp unset, surface the state, or fail. Never let a no-op mark itself done - it cannot be retried, because nothing knows it did not happen.
**How to apply:** Any permission, credential, or connectivity check that guards work with a persistent completion marker.
**Source:** MasroufApp.rereadWholeInbox, maintenance 19.

### 2026-09-01 — A message with no total will still hand you a number
**Mistake:** 261 filled share orders ("تم تنفيذ أمر شراء رقم .032011030003802 للرمز1180، الكمية 12، سعر التنفيذ 39.65") were stored as purchases of 74 halalas and similar. The amount extractor read digits out of the ORDER NUMBER.
**Why:** The parser's contract is "refuse to guess", and it does refuse when it finds nothing - but a message dense with identifiers always has something that looks like an amount. Refusal protects against absence, not against plausible noise.
**Rule:** When a new sender is added, group its stored rows by amount and look at the smallest and largest. An amount that is implausible for its category - sub-riyal purchases, a six-figure card limit - means a number was read out of an identifier, and the fix belongs in the gate, not the extractor.
**How to apply:** Immediately after any new parser's first import.
**Source:** SNB Capital order fills, maintenance 21.

### 2026-09-01 — Compose already reverses a horizontal scroll under RTL
**Mistake:** Twice "fixed" a card row that opened at the wrong end: first a LaunchedEffect scrolling to maxValue (broken by activity recreation restoring saved state without re-running effects), then `reverseScrolling = isRtl` — which double-flips what RTL already flips, and reliably opened the row at its far end.
**Why:** The original misplaced opening was a stale saved scroll offset surviving a change of container (LazyRow → Row), not a wrong zero point. `Modifier.horizontalScroll` is direction-aware: offset 0 is the START in the current layout direction.
**Rule:** Before steering a scroll position, reproduce the misplacement from a FRESH state (clear data or first install). A position restored from state is evidence about the past container, not about the current code — and a default that is already correct needs no help.
**How to apply:** Any scroll-position "fix" in an RTL app; any behaviour that only appears after an upgrade.
**Source:** CardsPanel.kt, three commits in one day.

### 2026-09-02 — A token pair is tested on the messages it was written for and paid for on the rest
**Mistake:** `{استلام, حوال}` → money arriving was written for meem's "تم إستلام حوالة داخلية" and passed every fixture. Run over the stored history it flipped 81 OUTGOING transfers to incoming: every Western Union body says "طريقة الاستلام" or "حساب المستلم: استلام عبر ويسترين يونيون", and barq's "تم استلام حوالتك" is the recipient's receipt of money the owner SENT.
**Why:** A token set has no adjacency. Two stems that mean one thing side by side mean nothing in particular three fields apart, and the first-match list guarantees the loose rule wins somewhere.
**Rule:** Prefer a contiguous phrase to a token pair when the meaning lives in the adjacency ("استلام حواله", not `{استلام, حوال}`), and re-run the whole stored corpus through the new rule set before committing: group rows by (bank, old verdict, new verdict) and read a body from every group. The diff is the review; fixtures cannot see cross-sender collisions.
**How to apply:** Every addition to `IntentClassifier.RULES` and every new `MessageGate` marker. The harness is a reflection runner over `core/build/classes` and a TSV export of `transactions`; it took ten minutes and found two regressions, one latent bug (265 "حوالة بين حساباتك" rows counted as spending or income), and four stored adverts.
**Source:** session 2026-09-02, urpay/Vision Bank/meem profiles.

### 2026-09-02 — The D360 possessive lesson has to be re-applied to every new guard
**Mistake:** Wrote Vision Bank's `^From\s*:\s*(?!\**\d)(.+)$`. On "From: ***5001" the engine gave back the space so the lookahead saw " ***5001", passed, and the party field read "5001". The file already records this defect at D360, with the fix (`\s*+`), two hundred lines up.
**Why:** The guard reads as present. Nothing fails; a number appears where a name should be, and only a corpus run over the new sender showed it.
**Rule:** Any `\s*` or `:?` between a label and a negative lookahead is possessive (`\s*+`, `:?+`), no exceptions, and a new profile is checked against the corpus for parties that are all digits or asterisks before it ships.
**How to apply:** Every new `BankProfile` pattern with a guard.
**Source:** SaudiBanks.VISION_BANK, caught by the corpus run.

### 2026-09-02 — "Confirmed as his" and "named as open" are two different facts
**Mistake:** Added urpay's, Vision Bank's and meem's cards to `CardIssuers` because each sender's own template named them. `CreditCardLabelTest` failed: that map is for cards the owner has said are OPEN, and he has not.
**Why:** The issuer was read off a message and is true; the map's contract is narrower than its name. The test that holds the contract was the only place it was written.
**Rule:** Before extending a map or list, find the test that constrains it and read its doc comment - that is the contract, not the field name. A fact that is true but not the fact the list holds does not belong in it.
**How to apply:** Any addition to `ActiveCards`, `CardIssuers`, `OWN_WALLETS` or another list whose membership carries a meaning beyond "known".
**Source:** CreditCardLabelTest, session 2026-09-02.

### 2026-09-02 — The name the app needs is in a message it must never store
**Mistake:** Spent three waves of web searches on merchant strings the terminal had
truncated to nine characters ("AL RASHED", "OBOUD BAH", "PROFESSIO"), and reported
the remainder as unreachable. They were not unreachable: the ONE-TIME-PASSWORD
message for the same purchase spells the merchant in full - "لدى:AL RASHED TIRES
COMPANY LLC" against the confirmation's "لدى:AL RASHED" - and every one of those
messages was sitting in the phone's inbox.
**Why:** `MessageGate` refuses OTP bodies and `purgeRejectedBodies` deletes them,
correctly, because they carry a credential. That made them invisible to every query
over STORED data, and the search for a fuller name never looked outside the
database. The same blind spot as "a sender with no parser produces no rows".
**Rule:** When a stored field is truncated, look for the same event in the RAW
inbox before concluding the information does not exist. Refusing to store a body is
not a reason to refuse to read one: pair a purchase with its code message on
amount, card and minute, and take the name - never the code.
**How to apply:** Any time a merchant, party or reference is short, truncated or
unreadable, and before commissioning research on what a string might mean.
**Source:** session 2026-09-02, `AL RASHED TIRES COMPANY LLC`.

### 2026-09-02 — A prefix is not an identification
**Mistake:** Having found that trick, took every unfiled truncation and searched the
inbox for a longer string starting with it. "Karam" turned up "KARAM BEIRUT", so a
`"KARAM" to FOOD` keyword was written - and it claimed أجواد الكرم, a grocery, which
the test caught. That purchase's own code message said SALLA APP.
**Why:** Two ways of reading a full name out of the inbox, and they are not equally
strong. Matching amount, card and minute identifies THAT purchase. Finding a longer
string that begins with the same nine characters identifies nothing at all - it is a
spelling coincidence, and in a corpus of 1,392 merchant strings there will be one.
**Rule:** Evidence that identifies a specific transaction may be acted on. Evidence
that merely resembles a string goes to the owner as a question. State which kind a
finding is when you record it.
**How to apply:** Any recovery of a truncated or abbreviated value from a second
source.
**Source:** session 2026-09-02, `CategoryGuess`, caught by `ConfirmedMerchants20260902Test`.

### 2026-09-02 — A brand's Arabic name is not a transliteration of its terminal string
**Mistake:** Swept the whole inbox for each unfiled merchant using the English
string and a guessed Arabic transliteration - for "CITY WINDOW", `سيتي ويندو`. It
found nothing, and the merchant was reported as unreachable. The shop writes itself
`سيتي دبليو` ("City W"), has its own SMS sender in that same inbox, and appears in a
bank's instalment offer beside three other furniture retailers. The owner named it
from memory; the evidence was there all along under a name no transliteration of
"CITY WINDOW" would ever produce.
**Why:** A card terminal sends a registered or legacy name; the brand markets itself
under another, and the Arabic form is a rendering of the BRAND, not of the string on
the receipt. Searching for one spelling asks about one spelling.
**Rule:** When sweeping a corpus for a merchant, search the distinctive TOKEN rather
than the whole string, and search the sender list as well as message bodies - a shop
that texts its customers is in the sender column under its own name. Treat a
no-result sweep as "this spelling is absent", never as "this shop is absent".
**How to apply:** Any corpus search for an entity that has both an English and an
Arabic public name.
**Source:** session 2026-09-02, سيتي دبليو / CITY WINDOW.

### 2026-09-03 — One shop, two names, both in the same person's history
**Mistake:** Sent a web search to find out what "NIBRAS ALARABIA CO" sells, got
"trades as Ounass" from a privacy policy, and offered the owner an answer he
rightly doubted - a legal name proves nothing about what HE bought.
**Why:** The corpus already held the answer twice over. He has purchases under
`OUNASS` in 2018, 2019, 2020, 2024 and 2026 and under `NIBRAS ALARABIA CO` in
2020 and 2022 - same channel, same kind of amount, interleaved years - and DHL
delivered a shipment "from NIBRAS ARABIA" five days after one of them. A card
terminal receives whichever name the merchant registered that year, so one shop
appears under both.
**Rule:** Before asking the web what a merchant is, ask the history whether the
same buyer used another name for it: group by amount pattern, channel and period,
and look for a second string that fills the gaps in the first one's timeline. A
brand and a legal name that never overlap in time are usually the same shop.
**How to apply:** Any unidentified merchant that appears in bursts with gaps.
**Source:** session 2026-09-03, NIBRAS ALARABIA CO / OUNASS.
