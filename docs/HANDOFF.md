# Handoff — 2026-08-31

State of the app and what is still open, so a new session can continue without
re-deriving any of it. Read `CLAUDE.md` first for commands and rules, and
`LESSONS_LEARNED.md` beside this file — every rule in it was paid for.

## Where things stand

- `main` at 115 commits. All tests green: **287** in `:core`, **163** in `:app`,
  **9** instrumented (`:app:connectedDebugAndroidTest`).
- **`connectedDebugAndroidTest` uninstalls the app and deletes its database.**
  It has already cost the owner's phone once. Use the `masrouf35` emulator, or
  back up first — the procedure is written beside the command in `CLAUDE.md`.
- Installed on the phone (Pixel 8 Pro, serial `38091FDJG00C4X`). The phone drops
  off adb often; check `adb devices` before installing.
- Database schema version 6. One-off repairs are a set in `MasroufApp.Repair`,
  each stamped with the version that introduced it, taken as a union and run once
  in declaration order. `CURRENT_MAINTENANCE_VERSION` is **13**.
- Real data on the phone: ~22,014 transactions, ~2,190 unfiled, 34 learned
  merchant rules of the owner's own.

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

## Open items

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

## Known gaps

- `WEST` is three unrelated merchants and has no rule on purpose.
- Statement import is not wired into the app; see the note in `CLAUDE.md` about
  reconciling a whole file inside one lock before it is.
- Instrumented tests run on a device only, and there is no CI.
