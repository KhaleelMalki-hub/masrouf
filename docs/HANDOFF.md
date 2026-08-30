# Handoff — 2026-08-30

State of the app and what is still open, so a new session can continue without
re-deriving any of it. Read `CLAUDE.md` first for commands and rules.

## Where things stand

- `main` at 102 commits, all tests green: 233 in `:core`, 114 in `:app`, 2
  instrumented migration tests (`:app:connectedDebugAndroidTest`, needs a device;
  it wipes the emulator's app data).
- Installed on the phone (Pixel 8 Pro, serial `38091FDJG00C4X`) at the head of
  `main`. The phone drops off adb often; check `adb devices` before installing.
- Database schema version 6. One-off data passes are stamped in
  `Preferences.maintenanceVersion` (currently 3); to add another, bump the
  number in `MasroufApp.runMaintenance`.
- Real data on the phone: ~22,040 transactions, 2,318 unfiled (10.5%), 32 learned
  merchant rules of the user's own.

## What was built this session (2026-08-28 → 30)

Categorisation from 34% to ~89.5%: type rules for merchant-less records, seven
new categories (housing, education, fees, services, entertainment, cash, income,
investment), ~300 merchant rules, learned rules (per merchant and per
merchant@bank), "let the app decide" undo, re-file-all, category provenance
(`category_source`). Bank + card on every row, recovered from the SMS fingerprint.
Card balances read off messages (`BalanceReader`, credit limit ≠ balance).
Recurring-payment detection (`RecurringDetector`, amount clusters per merchant).
Salary line (bank-announced by default, user-typed wins). Investment excluded from
spending via `Transaction.countsAsSpending`. Arabic/English merchant display
names. Material 3 pass: M3 components, shape tokens, M3 motion, Material You,
snackbar/progress/tooltips, ListItem rows, 48dp targets, predictive back.
Launch-time SMS catch-up (two days back) so a missed message costs one launch.

Defects found in the real data and fixed: 58 English OTPs stored as purchases;
a card-limit notice stored as a 200,000-riyal purchase; 122 salary deposits
("ايداع رواتب") typed as transfers; two purchases 69 s apart collapsed as one;
`Amazon SA` swallowed by the `AMAZON NO` rule; six short keywords matching
inside longer words; `Tamra Capital` filed as a restaurant.

## Open items, in the order the user wants them

1. **Remaining unfiled (2,318).** 762 have no merchant at all (transfers to
   people, withdrawals); 802 merchants have one transaction, 190 have two, 122
   have three or more (463 transactions). The 3+ set is the only part worth
   time. Method that worked: pull the phone DB with `-wal`, list unfiled
   merchants by count, look for a longer spelling of each key elsewhere in the
   history, then ask the user by name. Several were resolved only because the
   user recognised them (BR- = Baskin Robbins, Fourth frame = tyre shop).
2. **The "beyond M3" design proposal.** The app now follows the M3 guide fully;
   the user asked to review, later, whether to give it a visual identity of its
   own on top. Nothing designed yet. PRODUCT.md and DESIGN.md are the inputs;
   Drahim is the explicit anti-reference.
3. **Manual recurring payments** — explicitly *not* wanted now ("ممكن مستقبلاً").
   Do not propose manual-entry features unless asked. Context: حنين مقادمي's
   500-riyal transfer looks bimonthly in the data, not monthly as the user
   believes; the detector will not call it monthly.

## Facts the user confirmed (do not re-ask)

- `Ammar` via Al Rajhi = café (weekly, 24 SAR); `AMMAR` via barq = bakery
  (rule `AMMAR@barq` → groceries).
- `BR-…` = Baskin Robbins franchise descriptor.
- `Fourth frame EST` = بنشر (tyre shop), 3,680 SAR was a set of tyres.
- `Tamra Capital` = investment house (category investment, out of spending).
- `Elaf Comp` = National Water Company; `MS.21535` = Mobile Service car
  servicing; `Wizebutter` = groceries; `SINDI` = men's thobes; `ZED AL ZA` =
  sandwiches; `DISTINCTI` = Kitchen Trends (kitchen fitting, shopping);
  `LOUBA W HEKAYA` = toy shop; `AlJoumaa2` = the hypermarket under a new name;
  `SUREPay SNB` = مغسلة ربوة التميز (car wash, 30–35 SAR, filed as transport).
- Active cards (last four): 5763, 7536, 8134, 2383, 8202, 3761, 7285, 2166,
  9941 (9941 is an IBAN, not a card). Listed in `ActiveCards`.
- Salary arrives as "ايداع رواتب", 19,491 SAR around the 26th.

## Known gaps

- `WEST` is three unrelated merchants and has no rule on purpose.
- The emulator's DB is a copy from 2026-08-30 morning and is disposable; the
  phone is the source of truth.
- Instrumented tests run on the emulator only; no CI.
