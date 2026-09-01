# Handoff — 2026-08-31

State of the app and what is still open, so a new session can continue without
re-deriving any of it. Read `CLAUDE.md` first for commands and rules, and
`LESSONS_LEARNED.md` beside this file — every rule in it was paid for.

## Where things stand

- `main` at 120 commits. All tests green: **289** in `:core`, **171** in `:app`,
  **9** instrumented (`:app:connectedDebugAndroidTest`).
- **`connectedDebugAndroidTest` uninstalls the app and deletes its database.**
  It has already cost the owner's phone once. Use the `masrouf35` emulator, or
  back up first — the procedure is written beside the command in `CLAUDE.md`.
- Installed on the phone (Pixel 8 Pro, serial `38091FDJG00C4X`). The phone drops
  off adb often; check `adb devices` before installing.
- Database schema version 6. One-off repairs are a set in `MasroufApp.Repair`,
  each stamped with the version that introduced it, taken as a union and run once
  in declaration order. `CURRENT_MAINTENANCE_VERSION` is **15**.
- Real data on the phone: ~22,014 transactions, ~2,190 unfiled, and the owner's
  own learned merchant rules (34 and growing — he files one whenever a shop the
  shipped list cannot name comes up).

  A figure that moves with use does not belong in prose. Read the current ones:

  ```bash
  for f in masrouf.db masrouf.db-wal masrouf.db-shm; do
    adb exec-out run-as sa.masrouf.app cat "databases/$f" > "local.${f#masrouf.}"
  done
  sqlite3 local.db "SELECT (SELECT COUNT(*) FROM transactions) rows,
    (SELECT COUNT(*) FROM transactions WHERE category_id IS NULL) unfiled,
    (SELECT COUNT(*) FROM merchant_rules) rules;"
  ```

## Personal values live outside the repository

The repo is public. The owner's names and his cards' credit limits are in
`local.properties` (gitignored) and reach the code through `BuildConfig` as
`OWNER_NAMES` and `CARD_LIMITS`. Absent, `AccountOwner` matches nobody and no card
shows a ceiling. **A fresh clone needs those two lines to reproduce his results**;
the format is documented in `AccountOwner.configure` and `CreditCards.configure`.

## What this session did (2026-08-31)

Found by reading the owner's real 22,000-message history, and by a five-dimension
review council. August 2026 read as 168,864 riyals spent; 42,564 of it was real.

Money that was never spending: credit-card settlements on both legs (the card paid
and the card charged), SADAD payments whose biller is one of his own cards,
transfers he sent himself, and monthly statement notices that are not transactions
at all. Machine withdrawals that named the card were filed as purchases because
`سحب+بطاقة` was tested before the ATM rules.

Amounts: the extractor could not see a four-figure amount written without a comma,
so 439 records stored a balance where an amount belonged — and one message, whose
bank sent a floating-point artefact, stored 91,999,999,999,999 riyals.

Credentials: four bodies holding live one-time codes were stored as confirmed
transactions, the gate having never known those two wordings.

Added: `travel` and `bonus` categories, an income destination with a navigation
bar, credit-card tiles that say `المتبقي من الحد` and mark a stale reading, and
the party's real name in place of an account number on 1,300 rows.

## Facts the owner confirmed (do not re-ask)

- `Ammar` via Al Rajhi = café (weekly, 24 SAR); `AMMAR` via barq = bakery
  (rule `AMMAR@barq` → groceries).
- `BR-…` = Baskin Robbins. `Fourth frame EST` = بنشر (tyre shop).
- `Tamra Capital` = investment house (out of spending).
- `Elaf Comp` = National Water Company; `MS.21535` = car servicing;
  `Wizebutter` = groceries; `SINDI` = men's thobes; `ZED AL ZA` = sandwiches;
  `DISTINCTI` = Kitchen Trends; `LOUBA W HEKAYA` = toys; `AlJoumaa2` = the
  hypermarket renamed; `SUREPay SNB` = مغسلة (car wash, filed as transport).
- **New this session:** `Maan Hama` = الخزائن المبتكرة (fitted cabinets, shopping);
  `LAURE` = perfumes; `Time-race`/`tap*Time` = car parts, Haval (transport);
  `ONTIME PL` = watches; `Tiqmo` = his own wallet (a top-up, not spending);
  `NTERNATIO`/`Internati` = a domestic-labour recruiter (fees — he filed it by
  hand, because no keyword can reach it safely);
  `Samira Ayed AlKulaithami` = استوديو المغربي, a portrait photographer — the shop
  is registered in its owner's name, so nothing in the message says studio (filed
  by hand as services/personal care, which is what a service performed for you is;
  shopping is a thing you take away).
- SADAD biller codes: 255 = AlRajhi cards, 016 = AlAhli cards, 207 = STC Pay.
  All three are his own, so a payment to them is not spending.
- Cards: 2383 (AlRajhi, credit), 8134 (AlRajhi, credit, now settled), 9994
  (Emirates NBD, credit), 5763 (AlRajhi, mada), 1887 (AlAhli, mada), 8202 (D360,
  mada), 7285/2166/9941 (three separate barq cards). **7404 is cancelled.**
  7536 and 3761 appear only as the funding card in a barq top-up and their issuer
  is unknown — a digital card from a bank he was trying.
- He settles the AlRajhi card from the Emirates NBD card and the reverse.
- Salary arrives as "ايداع رواتب", 19,491 SAR around the 26th. Bonuses arrive as
  transfers from `امانة العاصمة المقدسة` — his employer.

### The card tiles (end of session)

The row is a `Row` with `horizontalScroll` at `IntrinsicSize.Max`, not a `LazyRow`:
a height typed by hand clipped twice, and a dozen cards make laziness worthless.
`horizontalScroll` opens at offset 0, which is the LEFT edge in both directions, so
in Arabic it opened half-way into the first card - hence the `LaunchedEffect` that
scrolls to `maxValue` under RTL. Order is `orderedCards`, asserted in
`CardOrderTest`: الراجحي, الأهلي, D360, برق, then by number.

8134 reads **41,000.00 · مسددة بالكامل**, matching his bank app. It was stale
because AlRajhi reversed its card field in April 2026 (`عبر:فيزا;8134` for
`عبر8134;فيزا`) and ten settlements stored their amount and balance attached to no
card at all. The pattern is in `SaudiBanks`, card-first so the network half is not
captured instead, and maintenance pass 14 re-read the stored bodies. A card whose
remaining allowance equals its ceiling is never marked stale: it owes nothing, and
a settled card sends no further message to refresh itself with.

### The party nobody could read (2026-09-01)

588 records - 330,211 riyals - were stored with no party at all, and unfileable:
a category is learned from a merchant. In nearly all of them the name was in the
body untouched. Every field pattern here is anchored to the start of a line, and
these senders end a field with something else, which `ArabicText.normalize` was
folding into a space: a carriage return (Emirates NBD's أثير), the two literal
characters `^M` (the same, already written in caret notation by something above
this app), a pipe (D360 and SNB's newer templates), or a run of five or more
padding spaces (AlRajhi 2015-2019, one line per transaction).

One fix in `ArabicText.FIELD_BREAK` plus two labels nothing looked for
(`اسم المتجر`, and a terminal id in front of the name) and the flat AlRajhi
template whose merchant sits unlabelled between the card and the date.

Measured on the phone, maintenance pass 15: **no party 588 → 120, unfiled
2,196 → 1,857.** The gate also learnt "رفض العملية", the active voice of a
refusal, which had stored a declined purchase as money spent.

## Open items

0. **1,857 records are still unfiled** - 529 of them in the last 24 months
   (139,077 riyals), the rest older. They are spread over ~1,150 merchants at
   about two records each, almost all local shops registered in their owner's
   name, so no keyword list reaches them: they need his memory, one at a time,
   and filing one files every record from it. 120 still carry no party at all.
1. **Five merchants he has not placed**, all on the cancelled 7404, all one-off
   except OBOUD BAH: `AL NOUJAI` (23,240), `OBOUD BAH` (6,175 over 7 visits),
   `AL MUASHA` (4,672), `ALATLAL T` (3,950), `AL RASHED` (3,114). Nothing in the
   messages narrows them further — the full text carries no city, branch or
   reference. They need his memory.
2. **107 rows still carry an account number as their party.** Down from 2,014;
   what remains uses templates none of the four bank profiles reads.
3. **One transaction of 37,000 (8 June 2026)** looks like a card settlement with
   no matching message in the archive. Left as spending, which errs high.
4. **The "beyond M3" design proposal.** The app follows M3; whether to give it an
   identity of its own is unexplored. `PRODUCT.md` and `DESIGN.md` are the inputs;
   Drahim is the explicit anti-reference.
5. **Manual recurring payments** — explicitly not wanted ("ممكن مستقبلاً").

## Open decision: getting the data to a new phone

The owner asked for this and is thinking about it. The findings are here so the
question does not have to be re-derived.

**Nothing is backed up today.** `allowBackup="false"`, and `dumpsys backup`
confirms `sa.masrouf.app` is absent from the backup set. A new phone starts empty.

What a phone change would cost, measured on the live database:

| | recoverable |
|---|---|
| 21,977 rows captured from SMS | only if the messages themselves transfer |
| 41 rows captured from notifications | no — notifications do not transfer |
| **185 categories he filed by hand** | no — not in any message |
| **35 learned merchant rules** | no — not in any message |

Those last two are the months of decisions that cannot be recreated: the employer
as bonuses, the cabinet maker, the portrait studio, the recruiter.

Two ways, and they are not equal:

1. `allowBackup="true"` — automatic, and sends twelve years of his financial
   history to Google's servers. It contradicts the first line of this project's
   own privacy rule, and the app does not even hold the INTERNET permission.
2. **An explicit export/import** — one file he makes, moves himself, and imports.
   Also covers a factory reset, a reinstall, and a different manufacturer. Nothing
   leaves his hand. Recommended, and the open question on it is whether the file
   should be encrypted with a passphrase: it is safest where it is most exposed,
   which is in transit between two devices, and the cost is a passphrase he must
   remember.

Related, and the same fix closes it: the build installed on his phone is `debug`
(`flags=[ DEBUGGABLE ... ]`, verified). Anyone with the phone, a cable and USB
debugging can read the whole SQLite file with `run-as` - no root, no exploit. A
signed `release` build closes that, and `release` currently has no signingConfig
so it cannot be built at all. Export/import is what makes a release build
practical: without it there would be no way to move the data.

Also unset: `android:dataExtractionRules`, which on Android 12+ governs
device-to-device transfer separately from cloud backup.

## Known gaps

- `WEST` is three unrelated merchants and has no rule on purpose.
- Statement import is not wired into the app; see the note in `CLAUDE.md` about
  reconciling a whole file inside one lock before it is.
- Instrumented tests run on a device only, and there is no CI.
