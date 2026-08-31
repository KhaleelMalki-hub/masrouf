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

