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

