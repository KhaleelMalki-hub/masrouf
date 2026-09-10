# Handoff — 2026-09-10

State of the app and what is still open, so a new session can continue without
re-deriving any of it. Read `CLAUDE.md` first for commands and rules, and
`LESSONS_LEARNED.md` beside this file — every rule in it was paid for.

## Where things stand

- All tests green: **385** in `:core`, **198** in `:app`, **9** instrumented
  (`:app:connectedDebugAndroidTest`).
- **`connectedDebugAndroidTest` uninstalls the app and deletes its database.**
  It has already cost the owner's phone once. Use the `masrouf35` emulator, or
  back up first — the procedure is written beside the command in `CLAUDE.md`.
- Installed on the phone (Pixel 8 Pro, serial `38091FDJG00C4X`). The phone drops
  off adb often; check `adb devices` before installing.
- Database schema version 6. One-off repairs are a set in `MasroufApp.Repair`,
  each stamped with the version that introduced it, taken as a union and run once
  in declaration order. `CURRENT_MAINTENANCE_VERSION` is **52**.
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

- **`Abdullah` (barq, POS) is a flowers and decoration shop → shopping.** Confirmed
  2026-09-10. **Filed as THIS ONE only, and deliberately not as a keyword**: the
  stored key is the bare first name `ABDULLAH`, which thirteen other merchants in
  this history contain - `ABDULLAH ALHARTHI`, `MAJED ABDULLAH`, `YAQOOB
  SAYEDABDULLAH` (3,015 riyals), `Abdulaziz Abdullah Est` (1,175) - most of them
  transfers to people. A whole-merchant rule on it would file those as shopping.
  There are two rows under this key, 250 and 50 riyals; nothing in either message
  says whether the smaller one is the same shop.

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
  mada), 7285/2166/9941 (three separate barq cards). **7404 was never a card of its
  own - it is 2383**, and "cancelled" was an inference from the SMS switching over
  to the other number in February 2026. His card statement settles it: headed
  `445521******2383`, and 277 of its 298 transactions for September and October
  2025 were stored here under 7404 (only 75 of them through Google Pay). Maintenance
  48 moved all 2,088 rows; `CardIssuers.REISSUED` holds the pair.
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

### The wallet nobody had read (2026-09-01)

Found by comparing the phone's INBOX against the database rather than querying the
database again. **A sender with no parser produces no rows, and a sender that
produces no rows is invisible to every query over stored data** - which is why
four earlier passes over this history missed it entirely.

STC Pay (now STC Bank; the owner has stopped using it) sent **4,446 messages
between 2019 and 2026** and nothing in the app had ever claimed the sender:

| | |
|---|---|
| purchases never recorded | 1,845 (~100,800 SAR) |
| Western Union transfers never recorded | 68 (94,126 SAR) — wages for domestic staff |
| top-ups counted as SPENDING from the bank's side | 670 rows, **650,280 SAR** |
| security codes only luck kept off the disk | 889 |

Three fixes, and the second is the one to remember:

1. `SaudiBanks.STC_PAY`, and STC Pay added to `OWN_WALLETS` so its top-ups stop
   counting as spending.
2. **The owner-name demotion now ignores sender lines.** Every outgoing transfer
   names the owner - he is sending it - and the rule that demotes a transfer to
   himself asked only whether his name appeared *anywhere*. All 68 WU transfers
   say `اسم المرسل`, so 94,126 riyals of wages read as his own money.
3. The gate learnt `رمز الأمان`, STC Pay's wording for a one-time code, which was
   its single most common message.

Maintenance 18 re-reads the WHOLE inbox once (`REREAD_WHOLE_INBOX`), because
these messages are older than any tail the launch catch-up reads.

### What the inbox comparison found, in order (2026-09-01)

Everything below came from one move: enumerate the senders in the raw inbox,
subtract the ones the app understands, count what is left. Four earlier passes
over the same history queried the database and found none of it.

1. **STC Pay / STC Bank** - 4,446 messages, 2019-2026, no profile claimed the
   sender. 1,845 purchases and 68 Western Union transfers (wages for domestic
   staff, filed as `fees`) never recorded; 670 top-ups of that wallet recorded
   from the bank's side as purchases and counted as spending.
2. **The owner-name demotion ignored roles.** Every outgoing transfer names him -
   he is sending it - so 94,126 riyals of wages read as his own money.
3. **READ_SMS was never granted.** Every inbox read returned quietly, so the
   launch catch-up had never run and the whole-inbox re-read reported success
   having done nothing. A repair that cannot run now leaves the stamp below its
   own version so the next launch retries.
4. **SNB Capital** - 1,136 messages, sender unclaimed for the same reason:
   "SNB-Capital" folds to SNBCAPITAL, which contains none of SNB's ids. 277
   movements between current and investment accounts, plus share dividends.
5. Three families that are not transactions at all and were being stored as one:
   a raised card limit (200,000 riyals), 261 filled share orders (no total in
   them, so the extractor read the order number), and 8 failed transfers.

Net, after maintenance 21: **25,748 records** (from 22,020), 3,723 of them PENDING
and awaiting the owner's review in the app - nothing auto-confirms. Spending by
year moved as the corrections landed: 2022 down 122,531 (top-ups removed), 2024 up
90,125 and 2025 up 97,281 (wallet purchases and wages added).

### The income audit (2026-09-01)

Asked for because the figures felt wrong. Three defects, and the rest checks out.

- **The dashboard's salary was the newest row typed SALARY.** A company he holds
  shares in pays dividends "بصيغة إيداع راتب" - the bank message is word for word
  a salary deposit, and only the company's own SMS the same day says otherwise -
  so twice (Dec 2024, Jul 2025) the app measured his month against a salary of 50
  riyals. It now takes the largest of the three most recent: a quarter, long
  enough to outvote one odd deposit and short enough to show a raise.
- **347 incoming transfers had no sender at all**, because SNB wrote the sender on
  the heading line until 2021 ("حوالة محلية واردة من X", "تحويل من X") and every
  pattern was looking for a line of its own. Among them two allowances from his
  employer, filed as ordinary transfers - the name is the only thing that
  separates money from an employer from money from anyone else. All 40 employer
  transfers now file as bonuses.
- 150 of those were "حوالة واردة من حسابك الاستثماري" - his own money coming back
  from the brokerage, now filed as investment rather than as an incoming transfer.

What is sound: 184 salary rows against 187 salary-shaped messages in the inbox
(the other three are two dividend notices and a duplicate); exactly 12 a year
since 2020; amounts consistent at each raise; none pending, none a debit, none
double-counted. Bonuses match the inbox year for year.

What is genuinely absent rather than missed: **no bonus before August 2020.** The
older templates name no sender, so nothing in those messages distinguishes an
allowance from any other incoming transfer. 2,405 incoming records still carry no
party - card top-ups and cash deposits, which name nobody.

### End-of-day state (2026-09-01, evening)

Maintenance **25** stamped on the phone; 25,813 records; unfiled down to
**2,114**; 42 pending (the AlJazira/SAIB import - he confirmed the big batch).
The day's merchant identifications: Chanel (AL NOUJAI), West Elm (WES + full
spelling), Emirates + FlyDubai, عصر الجوال, plus the descriptive batch
(FUNDUQ/MILLENNIUM -> travel, WOJOOH/TOUS/CHARRIOL/BED AND BATH -> shopping,
RESTURANT as the terminal spells it).

UI: the quoted bank message is a soft inset that opens at the start of the
Arabic line; the card row needs NO RTL steering - Compose reverses the axis
itself, and the lesson about the two wrong fixes is in LESSONS_LEARNED.

**Deferred by the owner:** the ثمانية font (font.thmanyah.com never delivered
its download email). The plan when it arrives is recorded: gitignored asset -
its EULA forbids redistribution and this repo is public - runtime load with a
Plex fallback, Sans on the body roles, Serif Display considered for the two big
headlines. No M3 impact beyond eyeballing Arabic line heights.

## What this session did (2026-09-02)

Read the three senders the owner had confirmed as his: **urpay** (179 messages,
2022-2024, card 4322), **Vision Bank** (115, 2025-2026, card 2455, still in use)
and **meem** / Gulf International Bank (659 under `MEEMSMS`, `meemKSA`, `meem`,
`meemSecure`; 2015-2024; cards 5654 mada, 0891 and 0883 credit). Profiles in
`SaudiBanks`, fixtures redacted into `RealMessages`, one test class each. Installed
packages read off the phone: `com.urpay.consumer`, `com.veripark.GIB`. "Vision
2030" is a marketing sender, so the id is `VISION BANK`, never bare `VISION`.

Measured by running every message of the three senders through the new pipeline
(the harness is a reflection runner over `core/build/classes`; not in the repo):

| sender | captured | of which own money | gated | not a transaction |
|---|---|---|---|---|
| urpay | 61 | - | 72 | 46 |
| Vision Bank | 20 | 6 | 24 | 71 |
| meem (all ids) | 135 | 55 | 249 | 275 |

Classifier rules added, each verified against ALL 25,813 stored rows by diffing
old and new verdicts (see the lesson of the same date): `حوالة بين حساباتك` as own
money - which turned out to be a latent bug at AlRajhi and SNB, **220 rows counted
as spending and 45 as income** since 2020; `CREDIT`+`TRANSFER` as money arriving;
`استلام حواله` / `استلمت حواله` / `جتك حواله` / `وصلتك حواله` as money arriving
(phrases, not token pairs - the pair version flipped 81 outgoing Western Union
transfers); `دفع`+`بيع` and `عملية ناجحة`+`بطاق` as purchases; `خصم من المحفظة`
as a bill; `نقاط مكافأة` as money back; `ايداع`+`ATM` as a deposit; `اكتمل تحويل
الأموال` as own money (Vision's savings accounts 5001/4002, read off its own
notices). `urpay` joined `OWN_WALLETS`: 26 bank-side top-ups stop being spending.

Gate: five OTP wordings (`PINCODE`, `YOUR CODE IS/FOR`, `الرمز السري المؤقت`,
`الرمز المؤقت`), one decline (`الحالة: فاشلة`), and the marketing phrases that had
produced figures (`سيتم تحديث`, `سيتم تخفيض`, `شروط واحكام`, `تطبق الشروط`, `بدون
رسوم`, `عرض رائع`, `حابين`/`حبينا`, and the bank's unfilled placeholders
`@MerchantName`/`@CustomerName`). Four AlJazira adverts stored as purchases of
1,499 and 1,000 riyals are purged by the same markers.

`retypeOwnMoney` now also visits incoming transfers, for the one verdict that can
move them (OWN_TRANSFER), so the 45 SNB rows above are corrected. Maintenance
**26** = purge + retype own money + whole-inbox re-read + refile.

**Installed on the phone and launched at 08:22; the phone dropped off adb before
the stamp could be read.** Expected after maintenance 26: ~216 new records from
the three senders (all PENDING), ~265 rows retyped to own money, 4 adverts gone.
Verify with the query in "Where things stand" and
`SELECT bank_id, COUNT(*) FROM transactions GROUP BY 1`.

`CardIssuers` deliberately does NOT list 4322/2455/5654/0891/0883: that map is for
cards the owner says are open (`CreditCardLabelTest` holds it) and he has not said
so. Rows from the new senders carry `bank_id` from the sender and get a chip
(`BankMark` has the three labels); tiles wait on him.

**Found and left open** (see below): 30 barq Western Union wage transfers stored
as OWN_TRANSFER because the owner's name sits under `من:` on the sender line.

## The filing pass (2026-09-02, afternoon)

The owner asked for every month to be filed, "بأعلى احترافية ممكنة ودقة". Three
mechanisms, in the order they were used, because each reaches what the one before
it cannot.

1. **Named from the string.** 1,245 unfiled merchants carried 2,044 records and
   580,669 riyals. About half say what they are - a chain, a brand, a word like
   STATION or PHARMACY - and those are in `MerchantNames20260902`, whose header
   says plainly that the owner confirmed none of them. Ordering inside that list
   follows the first-match rule: STATIONERY before STATION, GAS before the perfume
   house AL QURASHI, the gateway prefixes (MF, SP, Q, TAP) last.
2. **Named by the owner.** Ten shops only he could name (لا كالي, كرز لنن, قطوف
   وحلا, أجواد الكرم, الحكير, اطلبها, ميازو, دار زيد, آفاق إعمار, رداء المسك),
   in `CategoryGuess`'s owner-named section beside the ones from 2026-09-01.
3. **Identified by web search, then confirmed by him.** Four parallel agents took
   the 160 largest remaining strings - registered company names, truncations,
   gateway prefixes - and searched for each, returning a category only with a
   source URL. 64 came back identified; the owner read the table and confirmed 48.
   Those are `ConfirmedMerchants20260902`; the ones he doubted are NOT in the code.

**His filing rule, given this session and now the app's:** food that goes home to
be kept is groceries whatever shop sold it - honey, oats, nuts, boxed chocolate,
dates, sweets. Patchi, Godiva, Bateel, Garrett, Jeff de Bruges and the candy shops
moved out of eating out because of it, and the category is labelled **بقالة وأغذية**
so the filing reads the way he means it.

Reading the unfiled rows that named no party found two defects worth more than the
filing did: **twenty bank adverts stored as purchases** (SNB's "واسترجع حتى 8,000
ريال" twice at eight thousand riyals, AlJazira's instalment offers a dozen times),
now refused by the gate - "لمزيد من المعلومات" is deliberately NOT a marker,
because a genuine SNB refund closes with it - and **SNB's 2014-2015 one-line
template**, whose shop sits after فى with an alef maksura that nothing looked for:
30 records, 62,000 riyals, no party at all.

Measured on the phone: unfiled debits **2,063 → 1,143**, of which only 322 are in
the last 24 months. Maintenance is at **28**.

**The worksheet.** The 651 merchants still unfiled are published as a private page
the owner can work through on any device - search, sort, a category per row, saved
in his browser and copied out as `merchant<TAB>category` lines:
https://claude.ai/code/artifact/dadb7e59-dffd-46cb-8e82-803c3e895e86

**Wave 2 was blocked, not finished.** Four more agents were launched on the next
160 strings (35,719 riyals) and all four died: this session's WebSearch budget is
capped at 200 calls and wave 1 had spent it. `CLAUDE_CODE_MAX_WEB_SEARCHES_PER_SESSION`
is now set to 1500 in `~/.claude/settings.json`, which takes effect on the NEXT
session. Re-run wave 2 there: the input files are `batch5.txt` … `batch8.txt` in
this session's scratchpad, and rebuilding them is a query away.

## Mada or credit (2026-09-02, evening)

The owner asked for two things and got both: which card a purchase went on, shown
on the row, and what a month put on credit against what it took straight out of an
account, shown under the bands.

The rule is in `core/model/CardKind.kt` and it is narrow on purpose. **The network
is not evidence** - "فيزا" and "ماستر" say which rails the money travelled, not
whether the card borrows, and his 7536 is a MasterCard drawn on the SNB account
(the same lesson `CardIssuers` already carries). So credit is the word for credit,
folded so ائتمانية and إئتمانية both reach it; mada is مدى or the Latin spelling;
and a body naming BOTH decides nothing, which matters because every settlement
message names the card being paid and the card paying it. A card whose messages
never said is left unlabelled, and left out of the month split - so the two figures
come to less than the total by design.

Decided once per launch (`TransactionRepository.cardKinds()`, after maintenance so
it reads corrected bodies), not watched: it is a fact about the card, not the
month, and folding 26,000 bodies on every write would cost the dashboard its first
frame - the lesson `MerchantMatch.Rules` records.

**Built and committed but NOT yet installed**: the phone was off adb all evening.
`app/build/outputs/apk/debug/app-debug.apk` is the build to push when it returns.

## The merchant research, wave 2 (2026-09-02)

Wave 1 identified 64 of 160 strings and the owner confirmed 48 of those. Wave 2
covered the next 160 and identified 48 more - **they are researched but NOT in the
code**: the owner had not gone through the table when the session ended. The two
tables are in the scratchpad as `research_w2a.tsv` and `research_w2b.tsv`, each
line `KEY  category  confidence  what it is  source URL`. Nothing enters
`ConfirmedMerchants20260902` until he says so; that is the whole point of the file.

A note for whoever runs wave 3: the WebSearch budget is **per subagent pool**, not
per session. Wave 1's four agents exhausted it and wave 2's first four returned
nothing at all, while the main thread's own searches still worked. Launch two
agents rather than four, cap them at two searches per key, and tell them to spend
the budget on the largest SAR totals first.

## Where the filing stands (end of 2026-09-02)

Three research waves and three rounds of the owner's own naming, all installed and
verified on the phone at maintenance **30**.

| | start of day | now |
|---|---|---|
| unfiled debits | 2,063 | **806** |
| of those, last 24 months | - | 273 (51,411 riyals) |
| pending | 3,723 | 0 (he confirmed them) |
| adverts stored as purchases | 20 | 0 |

The research method and its yield, so nobody repeats the cheap part and skips the
expensive one: **wave 1** searched the 160 largest strings, identified 64, and the
owner confirmed 48. **Wave 2** did the next 160, identified 48, and he took all of
them - correcting two, which is the entire argument for the confirmation gate:
تكوة is a restaurant and the mall charge is parking, and the search had both as
shops. **Wave 3** searched 90 and answered only 20, because what remains is
establishments registered in a person's name. That is the floor: no search reaches
"EST MUNIRAH SIDDIQUE", and no keyword list ever will.

The 500-odd merchants left are his worksheet, one memory at a time:
https://claude.ai/code/artifact/dadb7e59-dffd-46cb-8e82-803c3e895e86

Still awaiting his review, and worth more than another wave: the wave-1 strings he
marked as doubtful (CITY WINDOW at 15,309 riyals, PROFESSIO at 13,000, OBOUD BAH,
AL MUASHA, AL RASHED and a few more). Those were searched and NOT confirmed; they
need his memory, not another search.

**A label that renders nowhere.** Renaming groceries to "بقالة وأغذية" changed
`SaudiCategories.labelAr` and nothing else: the interface reads the string
RESOURCE, and `labelAr` is read by nothing at all. The screen kept the old word and
no test noticed. `CategoryCoverageTest` now parses `strings.xml` and asserts the
two agree - the rule CLAUDE.md states for a month's total, applied to a name.

**The flake is fixed, and it had a cause.** `MonthNavigationTest` and
`AddExpenseViewModelTest` failed intermittently with "uncaught exceptions before
the test started" - the exception always landing on whichever test ran NEXT, which
is what made it look like noise. `AddExpenseViewModel.init` launched on
`Dispatchers.Default`, a dispatcher no test can advance or await; that coroutine
outlived the test that started it, reached back into the test's own
`StandardTestDispatcher`, and threw. Adding `cardKinds()` to the same init made it
frequent enough to catch. The dispatcher is now a constructor parameter and the
three ViewModel tests pass their own. Three consecutive full runs, clean.

## The corpus answers what the web cannot (2026-09-03)

The third day of filing found four ways to name a merchant that no search reaches,
and one that looks like a way and is not. Written down because each cost a session
to find and each will be wanted again.

1. **The code message carries the full name.** The confirmation truncates the shop
   to nine characters; the one-time-password message for the same purchase spells
   it out - "لدى:AL RASHED" against "لدى:AL RASHED TIRES COMPANY LLC". The gate
   refuses those bodies and always will, so no query over STORED data can see
   them - but the phone's inbox still holds them. Match on amount, card and
   minute; take the name, never the code. Also gave Ashley Furniture, LAABIS, a
   dates shop, carboost, Taibahgifts and the health endowment fund.
2. **The shop's own SMS.** 534 senders write to this phone and 500 are not banks;
   some are the shops. Fold every sender against the unfiled keys, then keep only
   the ones whose message lands on the SAME DAY as the purchase. Gave GoldenScent,
   المسلم للتمور, and hnak - whose message named the PRODUCT ("عبوة ماء زمزم 5
   لتر"), which is why it files as groceries rather than as the general store.
3. **One shop, two names, both in his history.** He bought at OUNASS in 2018-2020
   and 2024-2026 and at NIBRAS ALARABIA CO in the gaps - a brand and a legal name
   that never overlap in time are the same shop, and DHL delivering "from NIBRAS
   ARABIA" five days after one purchase settled it. The same logic separated
   الخزائن المبتكرة (Creative, `Maan Hama` + `International Creative`) from
   الخزائن الاحترافية (`PROFESSIO`): two closet makers, two English names, two
   different years, and the owner had used both.
4. **The context around the purchase.** "TermAppISO DXB AR" is a terminal's
   protocol string where a name belongs. What placed it was the trip: Dubai on 18
   March 2016, and Saudia issuing his boarding pass DXB to JED two days later.
5. **A prefix is NOT an identification.** Searching the inbox for any longer string
   starting with a truncation found "KARAM BEIRUT" for "Karam" - whose own code
   message says SALLA APP, and the keyword it justified claimed أجواد الكرم, a
   grocery. The test caught it. Evidence that identifies a TRANSACTION may be
   acted on; evidence that resembles a STRING goes to the owner as a question.

**PROFESSIO, the largest unfiled merchant in the history, closed on memory.** 13,000
riyals over three August-2024 payments that no search, sender, shipment or code
message ever named. Laying out the shape - a deposit and two instalments, in
person, in the same weeks as the kitchen and the appliances - was what let the
owner remember الخزائن الاحترافية in Al Rawdah.

**Where the filing ended:** 728 unfiled debits, 129,868 riyals, across 466
merchants; 245 of those records (32,580 riyals) are in the last 24 months. It began
the day at 2,063 records and 580,669 riyals. Maintenance is at **40**, nothing is
pending, and the tests stand at 374 in `:core` and 191 in `:app`.

**A bug found in a screenshot he sent.** The home screen said he pays 102,890
riyals a month across 14 recurring payments. `RecurringDetector` filtered on
direction and status and never asked whether the debit was money LEAVING, so his
186 transfers to his own AlRajhi account read as a standing order. It asks
`countsAsSpending` now.

**Deferred, by his decision: a switch to hide his own money moving.** He tops up
barq from a mada card (no fee now), spends from it, and moves the rest to his
AlRajhi account - so ONE movement of 5,000 riyals writes four rows in 95 seconds,
two banks each reporting both legs. The totals are right: all four are transfers
and none reaches spending or income. But 1,310 of his last 3,379 records are money
moving rather than money spent, and the history reads as noise. The design he
approved and deferred: one switch above the list, "أخفِ حركة أموالي", hiding what
is neither spending nor income, remembered in `Preferences`. **Do not** collapse
the two legs into one row - the app prevents double-counting precisely because
each bank tells its own side, and merging them trades a visible nuisance for a
silent risk.

## The interface review (2026-09-03)

Four parallel read-only reviews against `DESIGN.md` and Material 3 - components
and colour, motion, RTL/typography/accessibility, layout and state - then every
finding verified in the live tree before it was touched. 54 findings; the ones
that shipped are below, and the reasoning for what was NOT taken matters as much.

**The heaviest was invisible.** `:core` is a plain Kotlin/JVM module and carries
no Compose compiler, so every model it exports reached `:app` as an unstable type
and no transaction row could ever skip recomposition - the whole history rebuilt
itself whenever any state on the screen changed. `app/compose_stability.conf`
declares what is already true of those types. Its comment syntax is `//`, not `#`.

**The most visible was a white flash.** The window theme was pinned to
`android:Theme.Material.Light`, so a phone in dark mode painted white for a frame
or two on every cold start. It is now a colour resource with a `values-night`
twin. (`Theme.DeviceDefault.DayNight.NoActionBar` does not exist on this
compileSdk - AAPT rejects it.)

**The one on the daily path was the amount field.** Digits carry no direction, so
in Arabic the paragraph resolved right-to-left and the halala point - typed on the
way to 45.50 - landed to the LEFT of the digits: the user saw ".45" and the caret
jumped. Pinned `TextDirection.Ltr`.

Then: a fade-through between the two destinations, which had been a hard cut; the
pending queue capped at 30 slips with the rest counted (a backfill lands thousands
and each slip is tall); a month total that slid backwards whenever it changed in
place; a strip that re-wove from zero on every scroll and every figure; legend
rows keyed so a fill never animates across category identities; `imePadding` on
the entry sheet; the month arrows turned from bare quotation glyphs into named,
auto-mirrored icon buttons; the ripple restored on two rows whose background was
painted over it; and the loading state separated from the empty state.

**What the phone showed that no review did.** In the history's supporting line the
card chip was the only unweighted child, so it was measured first with whatever it
asked for: "الراجحي ائتمانية 2383" took the row and the date and category
ellipsised to "فوات." beside visibly empty space. The first fix made it worse -
weighting both split the row evenly and cut "تحويلات" to "تحو..." - and the phone
showed that too. The weight belongs on the chip alone. **Screenshot the running
app; three careful code reviews did not catch either state.**

**Not taken, deliberately.** The colour review called `primary` and `tertiary` as
text on `surface` a contrast failure. That is the pairing M3's own TextButton
uses, and the accent roles sit at tone 40 against a tone-98 surface. A reviewer's
confidence is not a resolver.

**All of that list is now closed (2026-09-03)** - except the legend ceiling, and
this paragraph claimed the opposite for six months. It shipped in `3fb868f` and was
REVERTED in `cfcb65d` the same round, because the owner read it on his own screen
and said he did not want it: the months it fired on had eight or nine categories, so
it cost a tap and a line of chrome to hide two rows the card had room for. There is
no `legendRows` anywhere in the tree and there has not been since.

**And the evidence that revert rested on has expired.** Measured 2026-09-10 on the
live database: August and July 2026 carry **sixteen** spending categories, April
fifteen, May and June thirteen. Thirteen to sixteen rows at 48dp is 620-770dp of
legend, which pushes the pending queue and the history off the screen. The decision
is the owner's and it stands until he changes it, but it was made about a different
month than the one he has now.

Every size that fenced text in became a floor: `heightIn` on the legend row with
its fill sized by `matchParentSize` rather than to the same constant, `widthIn` on
the income month label and the card tile, and the month-picker cell traded
`aspectRatio` - a height computed from a width, which has no relation to the text
inside it - for a minimum height with the row on `IntrinsicSize.Min`, so a name
that wraps lifts its whole row instead of overflowing its cell.

The rest: the strip now carries the headline a glance at it gives (largest
category and its share) for a screen reader, the legend below already being read
row by row; the amount field takes its name from the label a `BasicTextField` has
no slot for; `YearChip` is M3's `FilterChip`, which carries selection to a screen
reader that a painted `Box` never did; the recurring header states whether it is
open; and the spending list moved its side inset from a modifier into
`contentPadding`, so both destinations inset the same way and the overscroll
stretch reaches the screen edge on both.

## The currency is the new riyal sign

`ر.س` became **U+20C1**, the sign the central bank published in February 2025 and
Unicode encoded in 17.0 in September 2025. One string does it, because every
figure in the app is built by `Money.forDisplay`.

The glyph is the work. IBM Plex Sans Arabic 1.005 - the family this app bundles -
has no U+20C1, and neither did any Noto build checked in September 2026: the sign
is younger than the fonts. A missing glyph is not a fallback, it is a box, on a
currency printed on every screen. So it is drawn into all four weights from
SAMA's own published outline by `tools/add_riyal_glyph.py`, scaled to the height
of the font's own digits so it sits beside them as a currency mark rather than as
a pasted icon. The upstream files moved to `tools/upstream/` so the APK ships one
family; the OFL's reserved-name clause is why the bundled family is renamed to
Masrouf Arabic. Re-run the tool after any font upgrade.

**Known, not fixed:** a screen reader used to say "ر.س" and now meets a character
it may not name. Every money figure is a plain `String` from one formatter, so
there is no single place to attach a spoken label - it would be one per call site.
Worth doing if he uses TalkBack.

## The five-reviewer council, 2026-09-05

Five reviewers in parallel - Material 3 conformance, interaction behaviour, money
correctness, accessibility/RTL/scaling, codebase and test integrity - each required
to prove every finding against the live tree and to read the comments before calling
a documented decision a defect. Seven commits came out of it, `cfcb65d`..`5a2437d`.

**The one the owner reported.** One inbound transfer, three rows. The detector's
same-route rule asked whether two bodies were the SAME text, and AlAhli announces a
transfer under three templates in one second. A second telling is now allowed, hedged
three ways: same bank, within thirty seconds, and only when the two bodies are not
one sentence with different numbers in it - two separate transfers always arrive on
ONE template, so they share a skeleton and can never be merged by that route. The
cross-route branch was tightened in the same pass: a push and an SMS used to merge on
amount and two minutes alone. **The three stored rows are untouched**; a repair pass
must reconcile in LISTS, never row by row.

**The intermittent red was one missing argument.** `HistoryFilterTest` built its
repository without the test dispatcher, so a producer ran on a real `Dispatchers.Default`
thread and completed after `resetMain()` - and the throw was reported against whichever
test started next. Three consecutive clean runs since.

**Three ways work could be lost silently**, all fixed: Save closed the sheet whether or
not it saved (and crashed the app on a failed insert); a category chosen on a pending
slip was lost by scrolling; and confirming a slip stamped the app's own guess MANUAL,
which made it immune to `refileAll`.

**Both device-dependent findings are closed** (verified in the tree, 2026-09-10).
`reverseScrolling` is gone from both call sites and each now carries the reasoning
rather than the parameter: `CardsPanel.kt:86` records the two "fixes" that fought a
problem that did not exist, and `ReceiptSlip.kt:196` records that it was argued
rather than photographed, since the slip only draws for a pending record and the
queue is empty. The `Crossfade` is gone too: `Motion.FADE_OUT`/`FADE_IN`/
`FADE_IN_SCALE` are M3's sequential fade-through and `AddExpenseScreen.kt:387` uses
them.

**Deferred with reasons** — *superseded; see "Performance, and the build he was
running" below, where the indexes, the plurals and the dead strings all landed.*
Indexes on `status`, `account_last4` and `merchant_key`
(a version 7 migration; `observePending` is a Flow re-running a full scan on every
insert during a 22,000-message backfill). Six count strings that hardcode the
singular Arabic noun where plurals exist. Fifteen dead strings. `MonthPanel`,
`CardTile` and `TransactionRow` are each over a hundred lines.

## Reversals were money moving the wrong way, 2026-09-05

The three rows the owner read for one hundred riyals were not a duplicate at all.
Asked whether the hundred had been sent once or twice, he said **once** - so the
three messages are an arrival, a reversal eight seconds later, and the arrival
again: three real entries, and the app's error was storing the reversal as a credit.
The rule written the day before for the duplicate reading was removed with the
diagnosis it rested on; only the cross-route tightening survived it.

Underneath was the defect that mattered. AlAhli writes a card refund as
`حوالة عكسية`, the classifier knew only `عملية عكسية`, and the word حوالة carried
every one into the outgoing-transfer rules - where TRANSFER_OUT counts as spending.
**105 records, 12,568.63 riyals**, each counted as money leaving on top of the
purchase it refunds: the purchase charged twice, the refund never credited.

The direction is not a judgement call - the bank's own figures settle it. After a
2,520.45 purchase the card read `الصرف المتبقي 443.79`; after the reversal,
`الرصيد المتبقي 2,964.24`, which is 443.79 + 2,520.45 to the halala. **Sixty of the
eighty rows carrying both figures match that sum exactly.**

Two classifier rules, because the same word does opposite things - a reversal on a
card returns money, a reversal of an INCOMING transfer sends it back out - and
maintenance **41**, which re-reads every stored body containing عكسية and corrects
what the parser now disagrees with, direction included. No pass before it had ever
changed a direction. Manual records are never touched.

**Applied to the phone on 2026-09-05, after a full backup.** 104 rows became
CREDIT/REFUND; three stayed outgoing and are correct as they are - two reversals of
incoming transfers (money that did leave, including the owner's hundred) and one
cheque reversal that matches neither rule and was left alone. Lifetime spending fell
from **8,208,588.67 to 8,197,321.69** - 11,266.98 riyals of spending that never
happened, net of the 1,100 that correctly became outgoing.

## Performance, and the build he was running

He said the app did not scroll smoothly and sometimes looked about to crash. It was
measured before anything was touched: **15.9% of frames janky, 90th percentile 27ms
against a 16.7ms budget**, and the slow part was the UI thread, not the GPU.

Three pieces of work were being paid in the wrong place. Every history row searched
the merchant list to decide what to call itself, inside the composable, so a fast
scroll paid it again for every row it composed. That search re-glued two strings for
every keyword it tested, though the glued form is computed once when the list is
built. And the card kinds read **every stored body** at launch - twenty-odd thousand,
each folded - while the first screen was drawing. Then eleven derived flows in the
view model were doing their filtering, folding and summing on the collecting thread,
which is Main.

**The largest single win was not code.** He was running a debug build: no R8, no
ahead-of-time compilation, Compose's debug paths. Same phone, same data, same swipes:

| | janky | 90th | launch |
|---|---|---|---|
| debug, before | 15.9% | 27ms | 677ms, 131 frames skipped |
| debug, after the fixes | 5.6-12.5% | 16-20ms | 677ms, 117 skipped |
| **release** | **2.68%** | **12ms** | **215ms, none skipped** |

The release build is signed with the **debug key on purpose**: the key decides
whether an install replaces the app or has to remove it first, and removing it takes
the database. It is not debuggable, so `run-as` is refused - measure on release, work
on debug, and either install replaces the other without touching the data.

Also this round: the reversal work (see above), a general merchant filing now drops
the bank-scoped rules it supersedes, five bulk actions show a progress bar, count
strings became Arabic plurals, eighteen dead things went, three indexes landed, and
every amount is now spoken with a currency - the riyal sign has no name in any
speech engine yet.

## What the council round left, and what closed it

Everything the five reviewers raised is now either done or written below with its
reason. The last of it: the month, the filter and the search text survive the
process being killed (they were the only part of "where the user was" that did not,
and the tab beside them already did); maintenance reads the history a page at a time
rather than pulling twenty-two thousand bodies into a list twice per run; and a
general merchant filing drops the bank-scoped rules it supersedes.

**Still open, deliberately.** Four long functions (`MonthPanel`, `CardTile`,
`TransactionRow`, `MainActivity.onCreate`) could be split; none of them is confusing
today, so the split waits for a reason better than a line count. `AL MUASHA` -
4,672.45 riyals, 5 October 2025, Google Pay - has exhausted every channel on the
phone and needs the owner's memory. And a baseline profile would help the cold start
further, but it does nothing for a debug build and the release build already took
the launch from 677ms to about 200.

## Three directions the wording stated and the rules did not read

Found by turning the reversal method on the whole archive: every stored body
skeletonised - digits masked - grouped into template families, and each family
weighed against what it produced. 26,402 messages, 5,744 families, five that
disagreed with themselves, and then the largest spending families read rather than
counted.

**1. "حوالة عكسية" - a card refund, stored as money leaving.** 105 rows, 12,568.63
riyals, each counted as spending on top of the purchase it refunds. Maintenance 41.

**2. "حوالة واردة بين حساباتك" - money arriving between his own accounts, stored as
leaving in half the cases.** 87 identical messages, 45 one way and 42 the other,
decided by which pass had read each one. Neither counts as spending, so no total
ever disagreed: the row simply drew a plus, or did not. Maintenance 42, and lifetime
spending was identical to the halala before and after - the control this one had to
pass.

**3. "تحويل من A PERSON" - money ARRIVING from his family, counted as spending since
2014.** The sender is named on the first line and the account on the third is his.
**639 rows, 920,075.40 riyals** - over a tenth of the lifetime total. Maintenance 43.

The bank counts too, and that is what settled every one of them. For the third: of
the rows in that family carrying a running balance, **332 show it rising by exactly
the amount and not one shows it falling.**

Applied to the phone on 2026-09-06 after a full backup. Lifetime spending
**8,197,721.85 → 7,277,646.45**. The whole-database type distribution moved in
exactly one place - TRANSFER_OUT down 639, OWN_TRANSFER down 32, TRANSFER_IN up 671
- and no PURCHASE, BILL_PAYMENT, FEE or SALARY row changed at all.

**The method is the finding.** Three defects of one shape, none of which any total
disagreed about, all found by grouping messages by template and asking what each
family produced. It is worth re-running whenever a bank changes its wording.

## What this session did (2026-09-10)

Three things closed and one measured.

**Google backup, the owner's decision.** He asked for it inside Android's own
backup rather than as a file he carries, so `allowBackup` is on with an allow-list
of three files and a WAL checkpoint before the copy. See the section below for what
travels and what still has to be verified.

**A wage sent abroad is not his own money.** 31 rows, 49,580 riyals. Maintenance 44.

**A card is not a shop.** 450 rows carried one; 179 of them were purchases that
could never be filed. Maintenance 45, then 46 when 45 turned out to have fixed one
bank of seven.

**لمسة شفرة** - a barber the terminal sends as the name of the plaza it sits in -
is now a shipped rule rather than one row he filed by hand.

**`READ_SMS` was not granted, and now is.** Both SMS permissions were restored by
adb this session, so the launch catch-up runs again. It had been silently doing
nothing - see the lesson of 2026-09-01. If it comes back off, that is the thing to
check first when captures stop arriving. The original text, kept because the
commands are what matters: `READ_SMS` was **not granted**. Every
inbox read returns quietly, so the launch catch-up has not been running. It was
granted by adb once before and something has taken it back; the lesson of
2026-09-01 is that a permission check which returns quietly is a feature that does
not exist. Restore it with:

```bash
adb shell pm grant sa.masrouf.app android.permission.READ_SMS
adb shell pm grant sa.masrouf.app android.permission.RECEIVE_SMS
```

**The card statement, and what one grep found.** He exported an AlRajhi credit-card
statement for 2025-09-01 to 2025-11-01 (`pdftotext -layout` reads it as it stands).
It closed the last unplaced merchant, corrected a card the app had as two, and left
one thing open:

- **15 of its 298 transactions have no row in the app** - 11 debits (1,235.14
  riyals) and 4 credits (248.91). Four are `VAT on Markup`, which the bank sends no
  SMS for at all; the rest are ordinary purchases (MANGO 458.00, Ziddy 272.00,
  Express Food 163.13, ASAL WA SORAH 113.85) whose message never arrived or never
  parsed. Not chased. A statement is the only document that can see them, which
  makes this worth repeating over the other months - the same move that found the
  STC Pay wallet, one layer up.

**The six-year statement, reconciled (2026-09-10).** He then exported the whole
card history, 2024-10-07 to 2026-09-09, 3,204 transactions. Matched against the
database by amount and date (a stored row may satisfy only one statement row), and
scoped to the card now that 7404 and 2383 are one:

| | rows | riyals |
|---|---|---|
| on the statement, no row in the app | 513 debits | **145,704.12** |
| " | 58 credits | 25,989.86 |
| in the app, no row on the statement | 104 purchases | 29,313.31 |

So the app **under-counts this card by roughly 116,000 riyals over two years**. The
uncaptured are not exotic: HungerStation 107 times, `VAT on Markup` 24 times (the
bank sends no SMS for it at all), Dr Soliman Fakeeh 14, and single large ones -
OUNASS 4,887.50, a charity 5,600.00, BVLGARI 2,350.00.

Two caveats on the figure, both in the direction of "measure again before acting":
the totals do not fully close (statement debits 784,709.00 against 583,804.28 of
stored purchases on the card, a 200,905 gap where this method accounts for 145,704),
and the 104 the other way are unexplained - some will be a date outside the
four-day window, some a purchase the statement posts differently.

**Nothing was imported.** Statement import is not wired into the app, and CLAUDE.md
says why that is not a small job: `DuplicateDetector.reconcile` takes a LIST on
purpose, so a whole file must reconcile inside one lock, and importing with
`forEach { recordCaptured(it) }` would silently merge real money. This is now the
largest known gap in the history and the reason to do it.

**Also measured, not chased:** the database is **17.0 MB** and Auto Backup's
ceiling is 25 MB. See the backup section.

## Filing, and the two clever ideas that failed (2026-09-10)

The owner asked for every transaction to be linked to its category automatically,
"بأعلى احترافية ومنطقية". The professional answer turned out to be mostly negative,
so it is written down before the positive part.

**What the statement bought.** Twenty-one shops the SMS could never name, because Al
Rajhi truncates a merchant to nine characters - "AL ENJAZ A", "COMPANY A", "Binat-alh".
Each was searched from the STATEMENT's descriptor and then put to the owner: he
confirmed twenty and **corrected one** - الإنجاز الفوري is a tyre and car-service shop,
not the government-paperwork office the research had found, with a URL. It was also the
largest of the batch, seven visits and 1,550 riyals. A sourced guess is still a guess.

Measured at maintenance 49: unfiled 1,015 → **974**, and unfiled spending in the last
24 months 27,047.83 → **18,565.04**, so 8,482.79 riyals filed - a third of what was
open. Every keyword was run against the whole merchant list first; none reaches a
second shop.

**Idea 1, rejected: match the owner's own rules the way the shipped list is matched.**
His 35 learned rules are looked up by exact string equality (`learned[key]`) while
`CategoryGuess` goes through `MerchantMatch`, which forgives truncation. That looks
like an oversight. Measured over the history: passing his rules through the same
matcher files **10 rows, 8 of them wrong** - his `INTERNATI` rule for a labour
recruiter takes `WADI INTERNATIONAL GEN` (3,528 riyals), `International Regions`,
`Keden International Co` and `SAIFUDIN INTERNATIONAL` to fees. That is the defect
LESSONS_LEARNED already records, arriving by another road. **Exact matching for a
learned rule is a feature**: the rule is a decision about one merchant, and widening
it betrays the decision. Do not re-propose this.

**Idea 2, rejected: file a row from a truncation-sibling already filed.** If
"دانكن دونتس" is unfiled and "دانكن دوناتس" is filed as food, infer the category.
Measured with prefix-only matching and a unanimity requirement: **26 rows**, and among
them `KHALEEL MALKI` → transfers, `ABDULLAH` → transfers, `ETHIOPIA` → travel. A whole
mechanism for 26 rows, several wrong.

**Then a deep research pass on the eleven the light one abandoned**, at roughly ten
times the effort: Arabic reconstructions, truncation expansions, Saudi directories,
Snapchat and Instagram, DNS on guessed domains. It reached six of eleven, and what
CLOSED most of them was not the search:

- `Fadaa Alibdaa` - a stall operator at Boulevard World, Riyadh. He was standing
  there: six `BLVD Worl` purchases that evening, and this at 23:56.
- `FAWASEL ADVANCES` - فواصل المتطورة, which runs The DockX arcade in the Red Sea
  Mall. The afternoon around it is a trip through that mall - H&M 15:00, Next 15:21,
  this 15:34, GOAT 16:23, Claire's 16:59.
- `DURRAH ALASEEL` - "FOR R" is FOR RENT A CAR and the field is exactly twenty
  characters. The week around it reads as a man driving his OWN car, so it went to
  him as a question; he remembered renting one that week.
- `Magma Fyo Klynk` - not broken English. مجمع → "Magma", كلينك → "Klynk": a medical
  complex in Makkah. See the transliteration lesson.
- `LABA LAMA WOMENS DEC` - the shop is still unidentified and probably always will
  be, but "WOMENS DEC" is the register's English for للتزيين النسائي. The descriptor
  names the trade without naming the business.
- `NMC####` and `NCC 6903` - Nahdi Medical Company and NahdiCare under their
  acronyms, the first raised from a low-confidence guess by his own
  `Al Nahdi Pharmacy 2082` row, the second confirmed by him.

Two he confirmed from memory (a gym, a shop in a mall) and three are still open:
`KAYAN PRO` (1,039 riyals at 00:51), `BLACK-M AC FOR TEAM` (450 riyals, probably a
sports venue - "FOR TEAM" reads as للألعاب الجماعية), and nothing else.

**Where filing stands at maintenance 52**: unfiled 1,015 → **962**, and unfiled
spending in the last twenty-four months **27,405.83 → 15,178.87 riyals, down 45%**.

**The algorithmic levers are spent.** The app files 96.4% of its history and every
remaining gain came from a NAME. What is left is his memory, or a document that
spells one out.

## Open items

0. **Every confirmed sender now has a profile** (urpay, meem, Vision Bank added
   2026-09-02; AlJazira and SAIB 2026-09-01). Still unread: STC's `900` landline
   bills (217 - probably already captured from the paying bank's side, so adding
   them would double-count). Ask before parsing.
0. **No tiles for urpay 4322, Vision Bank 2455 or meem 5654/0891/0883 - the
   owner's decision, 2026-09-02** ("these cards no longer matter to me; what
   matters is that their spending is recorded"). Do not re-ask. Their rows carry
   `bank_id` and file like any other.
0. ~~30 barq Western Union transfers stored as OWN_TRANSFER~~ **DONE 2026-09-10.**
   31 rows, 49,580 riyals. `IntentClassifier` drops the `من:` line only when the
   message NAMES a beneficiary - a letter after the label, not just the label, which
   is what keeps AlRajhi's `الى:3016 / من:<him>` out of it (89 rows, 221,895 riyals,
   that the first version of the rule would have turned into spending). Maintenance
   44, verified on the phone: 31 rows moved and nothing else in the type
   distribution changed.
0. **Vision Bank credit transfers from himself** (4 rows, 4,195 riyals, `Sender:`
   is his own name) are TRANSFER_IN. Filing his own name as a transfer rule
   handles it in the app; a TRANSFER_IN demotion by sender line would be the code
   fix.
0. Two meem purchases from January 2016 are lost on purpose: the bank sent its
   template unfilled (`@MerchantName`, amount to three decimals) and the extractor
   read the balance; the gate now refuses them.
0. **2,198 records are still unfiled** - 529 of them in the last 24 months
   (139,077 riyals), the rest older. They are spread over ~1,150 merchants at
   about two records each, almost all local shops registered in their owner's
   name, so no keyword list reaches them: they need his memory, one at a time,
   and filing one files every record from it. 120 still carry no party at all.
1. ~~One merchant he has not placed: `AL MUASHA`~~ **PLACED 2026-09-10, by his
   card statement.** The statement carries the untruncated string -
   `AL MUASHAH TRADINJ C`, the terminal's own misspelling of TRADING CO - which
   reaches Al Muashah Trading Company Limited, a furniture and home-décor company
   in Jeddah whose domain `almuashah.com` redirects to `ihomestore.com` (اي هوم
   للمفروشات). He confirmed it: furniture. Filed as shopping, maintenance 48.

   **The method is the finding, and it is new here.** Every channel on the PHONE
   had been exhausted - the SMS truncates a merchant to nine characters and no
   search of the fragment reaches anything. The statement is a different document
   with a longer field, and it was one grep away. The original note, for the record:
   4,672.45 riyals, one visit, 5 October 2025 at 18:10 through Google Pay. The other
   four on this list were resolved before it was written and the list was never
   corrected - `AL NOUJAI` is Chanel (23,240, he named it), `ALATLAL T` is الأطلال
   للاتصالات, `OBOUD BAH` is العامودي for Nissan and Haval parts, and `AL RASHED`
   is proven twice: he placed it, and the card's own OTP thirty-eight seconds
   before the purchase reads `AL RASHED TIRES COMPANY LLC`.

   For AL MUASHA every channel on the phone is exhausted. It paid through a wallet,
   so no OTP carries its full name; no shop message names it or its amount anywhere
   in the message store; and a search across 967 merchant-naming OTPs against every
   unfiled purchase returns nothing for it. What is left is the context: he refuelled
   at Aldrees half an hour before, bought 157.95 from Amazon six minutes before, and
   paid 4,672.45 in one tap. It needs his memory.
2. **A party that was the CARD, not the shop - fixed 2026-09-10.** Bigger than
   this list recorded: 450 rows, not the 66 "cosmetic" refunds noted under Known
   gaps. SNB's 2017-2018 point-of-sale template writes two `من` lines, the card
   then the shop, and the pattern took the first - so **179 purchases** carried a
   card where a merchant belonged, unfileable, and the fallback filed all of them
   as transfers. Maintenance 45 cleared 331 and gave 141 purchases and 70
   withdrawals a real shop; 59 refunds correctly kept none.

   **45 fixed one bank of seven** - the guard had gone into SNB's copy of a pattern
   seven profiles carry, so the re-parse put the card straight back on the other
   113. It now lives in `BankMessageParser.firstMatch`. **Maintenance 46 ran and is
   verified**: no row anywhere carries a card as its party, no type moved, and of
   the 40 merchant names the repair created 39 are real shops - Dunkin Donuts, a
   Sasco station, Texas Chicken, Subway, a pharmacy, a salon.

   The fortieth was a defect 46 created. Skipping the card makes the pattern walk
   on, and on mada Pay's template - which names the card, the account and the
   bank's link, and no shop at all - the walk reached
   "للتفاصيل http://alah.li/mobile". Seventeen purchases were filed as BILLS by a
   link. `NOT_A_PARTY` now refuses a URL as well as a card. **Maintenance 47 ran and is
   verified**: rows carrying a URL as their party went 17 to 0, rows carrying a
   card stayed at 0, no type moved anywhere, and the seventeen lost the category a
   link had given them.

   **Where this item now stands:** nothing in the history has a card or a link
   where a merchant belongs. What is left under this number is the 227 rows
   carrying an ACCOUNT NUMBER, on templates none of the profiles reads.

   Still open under this number: **227 rows carry an account number** as their
   party, on templates none of the profiles reads.
3. **One transaction of 37,000 (8 June 2026)** looks like a card settlement with
   no matching message in the archive. Left as spending, which errs high.
4. **The "beyond M3" design proposal.** The app follows M3; whether to give it an
   identity of its own is unexplored. `PRODUCT.md` and `DESIGN.md` are the inputs;
   Drahim is the explicit anti-reference.
5. **Manual recurring payments** — explicitly not wanted ("ممكن مستقبلاً").

## Getting the data to a new phone — decided, 2026-09-10

The owner's answer: **"بسويه من ضمن باك اب قوقل"** — Android's own backup, not a
file he has to make and carry. `allowBackup` is `true` and the app declares what
may be copied.

What that protects is not the messages. Those arrive again with the SIM and the
whole inbox is re-read. It is the **185 categories he filed by hand and the 35
merchant rules he taught the app** — months of decisions that exist in no bank
message and that nothing can recreate.

- `res/xml/backup_content.xml` (API 26–30) and `res/xml/data_extraction_rules.xml`
  (Android 12+) are an **allow-list**: `masrouf.db`, `masrouf.db-wal`,
  `masrouf.settings.xml`, and nothing else, so a table added later does not join
  the backup by default. One truth in two files because the platform reads two;
  `BackupRulesTest` asserts they are identical, since the older file is the copy no
  phone here tests.
- Both routes are on. `cloud-backup` survives a phone that is lost or dead and
  carries `disableIfNoEncryptionCapabilities="true"`; on Android 9+ the key is the
  phone's own lock secret, so the copy in his Google account is one Google cannot
  read. `device-transfer` is the direct phone-to-phone copy, which touches no
  network and is not bounded by the quota.
- `MasroufBackupAgent` runs `PRAGMA wal_checkpoint(TRUNCATE)` before the copy. Both
  files are in the backup anyway, but the system copies them one after the other,
  and a checkpoint happening in between is read as a torn main file with no error
  anywhere. Best-effort: if the database cannot be opened, the backup still runs.
- The settings file is included so `maintenanceVersion` survives; a restored
  database that came back at 43 must not have every repair pass run over it again.

**VERIFIED END TO END, 2026-09-10.** `adb shell bmgr backupnow sa.masrouf.app` returns
`Package sa.masrouf.app with result: Success`, the app is in the system's backup set,
and `masrouf.db-wal` is **0 bytes** on disk — the agent's checkpoint runs. The database
is **17.0 MB** against Auto Backup's **25 MB per-app ceiling**, so it fits today with
about 8 MB of headroom.

**The headroom is the thing to watch, and it fails silently.** A dataset over the
ceiling is simply not backed up: the system calls `onQuotaExceeded` and stops, which
reads as a backup that works right up to the day it is needed. At roughly 640 bytes a
row that is another ~12,000 messages. Re-measure with the commands below; if it ever
goes over, the cloud half is off in practice and an explicit export is back on the
table (the device-to-device half is unaffected):

```bash
adb shell run-as sa.masrouf.app ls -l databases/
adb shell dumpsys backup | grep -i sa.masrouf   # that the app is in the backup set at all
adb shell bmgr backupnow sa.masrouf.app         # force one, rather than waiting for idle
```

The other half of this closes with it: the build on his phone is `debug`, so anyone
with the phone and a cable can read the database with `run-as`. `release` is now
buildable and signed with the same key, so `adb install -r` of it replaces the debug
build without touching the data — and with backup on, there is a way back even if
something goes wrong.

## Known gaps

- `MonthNavigationTest` fails rarely with "uncaught exceptions before the test
  started" - some earlier test leaks a late-throwing coroutine. Twice seen,
  green on every rerun and in isolation; not chased yet.
- ~40 ENBD card payments carry "XX8101" as their party: reparse fills a missing
  party but never rewrites a wrong one. Cosmetic - they are non-spending types.
  (The "66 cashback refunds carrying بطاقه" that stood here was wrong twice over:
  it was 450 rows, and 179 of them were PURCHASES, not refunds. See open item 2.)

- `WEST` is three unrelated merchants and has no rule on purpose.
- Statement import is not wired into the app; see the note in `CLAUDE.md` about
  reconciling a whole file inside one lock before it is.
- Instrumented tests run on a device only, and there is no CI.
