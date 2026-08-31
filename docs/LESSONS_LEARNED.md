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
**Mistake:** The card tile showed "المتبقي من الحد 10,000 من 41,000" for card 8134
in September. The figure was correct — on 2 April 2026, the last day that card sent
a message. The owner had paid the card off since, saw the tile, and asked why the
app thought he owed 31,000. The date was on the tile, in the faintest style
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

