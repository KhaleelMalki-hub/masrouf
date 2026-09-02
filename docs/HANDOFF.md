# Handoff — 2026-09-02

State of the app and what is still open, so a new session can continue without
re-deriving any of it. Read `CLAUDE.md` first for commands and rules, and
`LESSONS_LEARNED.md` beside this file — every rule in it was paid for.

## Where things stand

- All tests green: **378** in `:core`, **178** in `:app`, **9** instrumented
  (`:app:connectedDebugAndroidTest`).
- **`connectedDebugAndroidTest` uninstalls the app and deletes its database.**
  It has already cost the owner's phone once. Use the `masrouf35` emulator, or
  back up first — the procedure is written beside the command in `CLAUDE.md`.
- Installed on the phone (Pixel 8 Pro, serial `38091FDJG00C4X`). The phone drops
  off adb often; check `adb devices` before installing.
- Database schema version 6. One-off repairs are a set in `MasroufApp.Repair`,
  each stamped with the version that introduced it, taken as a union and run once
  in declaration order. `CURRENT_MAINTENANCE_VERSION` is **30**.
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

## Open items

0. **3,723 records are PENDING.** They are the recovered history and the owner
   has not seen them; the app has a confirm-all action for exactly this.
0. **Every confirmed sender now has a profile** (urpay, meem, Vision Bank added
   2026-09-02; AlJazira and SAIB 2026-09-01). Still unread: STC's `900` landline
   bills (217 - probably already captured from the paying bank's side, so adding
   them would double-count). Ask before parsing.
0. **No tiles for urpay 4322, Vision Bank 2455 or meem 5654/0891/0883 - the
   owner's decision, 2026-09-02** ("these cards no longer matter to me; what
   matters is that their spending is recorded"). Do not re-ask. Their rows carry
   `bank_id` and file like any other.
0. **30 barq Western Union transfers (2025-2026, ~45,000 riyals of wages) are
   stored as OWN_TRANSFER.** barq writes the owner under `من:` as the SENDER and
   the worker under `الى:`; the self-transfer demotion strips only lines that say
   مرسل/From, so it sees his name and calls the wage his own money. Fix belongs in
   `IntentClassifier.withoutSenderLines` (drop a `من:` line when an `الى:` line
   names someone else) plus a RETYPE pass. Not done: it changes a rule shared by
   every bank and needs its own corpus diff.
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

- `MonthNavigationTest` fails rarely with "uncaught exceptions before the test
  started" - some earlier test leaks a late-throwing coroutine. Twice seen,
  green on every rerun and in isolation; not chased yet.
- 66 cashback refunds still carry the word "بطاقه" as their party, and ~40 ENBD
  card payments carry "XX8101": reparse fills missing parties but never
  rewrites a wrong one. Cosmetic - all are non-spending types.

- `WEST` is three unrelated merchants and has no rule on purpose.
- Statement import is not wired into the app; see the note in `CLAUDE.md` about
  reconciling a whole file inside one lock before it is.
- Instrumented tests run on a device only, and there is no CI.
