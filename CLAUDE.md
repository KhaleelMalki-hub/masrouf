# masrouf — Claude context

Personal Arabic-first Android expense manager for Saudi bank messages and
statements. Single user, on-device, offline. Not a product, not published.

## Commands

```bash
./gradlew :core:test              # 229 tests, runs anywhere with a JDK
./gradlew :app:testDebugUnitTest  # 114 tests, needs the Android SDK
./gradlew :app:assembleDebug      # needs local.properties with sdk.dir
./gradlew :app:connectedDebugAndroidTest   # 2 migration tests, needs a running device
```

The SDK lives at `/opt/homebrew/share/android-commandlinetools` and the JDK is
keg-only, so builds need `JAVA_HOME=/opt/homebrew/opt/openjdk@21`. There is an
arm64 AVD named `masrouf35`:

```bash
$ANDROID_HOME/emulator/emulator -avd masrouf35 -no-window -gpu swiftshader_indirect &
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell cmd locale set-app-locales sa.masrouf.app --locales ar-SA   # check RTL
adb shell cmd notification allow_listener \
  sa.masrouf.app/sa.masrouf.app.capture.MasroufNotificationListener
adb logcat -s MasroufCapture     # one line per message the listener refused
```

Reading the database off a device needs the write-ahead log, not just the file.
Room runs in WAL mode, so a write made seconds ago is still in `masrouf.db-wal`
and `cat databases/masrouf.db` alone reports the state *before* it - which reads
as "the button did nothing" when the button worked:

```bash
for f in masrouf.db masrouf.db-wal masrouf.db-shm; do
  adb exec-out run-as sa.masrouf.app cat "databases/$f" > "local.${f#masrouf.}"
done
sqlite3 local.db 'SELECT id, status, source, amount_halalas FROM transactions;'
```

`:app` is included in the build **only** when an Android SDK is present (see
`settings.gradle.kts`). That is deliberate: `:core` must stay buildable and
testable on a machine with no SDK, because that is where its correctness is
proven.

## Layout

```
core/   pure Kotlin/JVM — no Android dependency, ever
  text/       ArabicText (normalise SMS text) · VisualOrder (undo PDF layout)
  money/      Money — integer halalas
  model/      Transaction, TransactionDraft, enums · SaudiCategories
              CategoryGuess - merchant to category, refuses to guess amounts
  time/       RiyadhTime · ArabicDates
  capture/    MessageGate → ParserRegistry → CapturePipeline · SaudiBanks
  dedup/      Fingerprint · DuplicateDetector · EventSignature
  statement/  RowAssembler · StatementImporter · SaudiStatements
app/    Android - Compose, Room, Arabic default with English in values-en
  capture/    MasroufNotificationListener · SmsCaptureReceiver (both thin)
              → SmsAssembly · CaptureRecorder (both tested, no Android)
              SmsInbox (reads the existing inbox) → HistoryImport (backfill,
              same pipeline, same gate, everything lands PENDING)
  data/       TransactionEntity + mappers · TransactionDao · MasroufDatabase
              TransactionRepository - the only route to storage, and where
              cross-source reconciliation runs under a lock
  ui/         AmountInput · DayLabel · MoneyFormat (all tested)
              AddExpenseViewModel · AddExpenseScreen
              Theme (Sadu palette, bundled Plex Arabic) · MonthStrip ·
              ReceiptSlip · CategoryChips · CategoryPalette
```

## Rules that must not be broken

1. **Money is never a `Double`.** Integer halalas via `Money`. Excess precision
   is refused, not rounded: three decimals in a bank message means the format was
   misread, and rounding replaces a detectable bug with a plausible wrong number.

2. **`MessageGate` runs before any parser, always.** And its marker list is written
   against bodies that actually arrived: 58 English "Your secure code is NNNN"
   messages reached storage as confirmed purchases, each doubling the purchase it
   authorised and each keeping a credential on disk, because the markers were
   Arabic-only. `purgeCredentialBodies()` asks the gate on every launch, so a
   marker added later also removes what an earlier gate let through. Use `CapturePipeline`; never
   call a `MessageParser` directly. A one-time-password message carries the same
   amount and merchant as the purchase it authorises and arrives seconds earlier,
   so an ungated pipeline silently doubles every online purchase. OTP bodies are
   also never persisted — they contain a credential.

3. **Parsers refuse to guess.** No recognised intent or no amount → `Failed` /
   `NotUnderstood`, never a draft with a plausible value in it. That signal is how
   a changed bank template becomes visible instead of becoming missing money.

4. **Nothing auto-confirms.** Captured transactions land as `PENDING`.
   `ParserRegistry.CONFIRMATION_THRESHOLD` is 1.0 and is lowered per parser only
   after that parser has been measured against real messages.

5. **`Transaction.countsAsSpending` is the only place** that decides what enters a
   monthly total. Do not re-derive it per screen; two surfaces disagreeing about
   the same month is the failure this prevents. It reads the category as well as
   the type, for one case: a deposit at an investment house reaches the bank as an
   ordinary card purchase at a terminal, and a month that counts it tells the user
   they spent money they still have. One function, two inputs - the rule is about
   there being one decision, not one input.

6. **Dates come from the device, not the message body.** Statement and screenshot
   text is visually ordered, so a date's character order there is not its logical
   order. All day arithmetic goes through `RiyadhTime` — never UTC.

7. **No invisible characters in source.** Bidi marks, zero-width characters and
   control characters are declared as code points (`Char(0x200F)`), never as
   literals. They are invisible in an editor and in a diff, so a corrupted paste
   would be unreviewable. This has already caused one real defect: a NUL byte
   silently became a fingerprint field separator.

8. **Every parser is written against a captured sample, never against a guess.**
   An invented regex compiles, matches something eventually, and is wrong in a way
   no test catches — because the test was invented from the same imagination. This
   has already produced one wrong test in this repo.

9. **Provenance is passed in, never inferred.** Two of these now. `CaptureRecorder`
   takes `source`, and every category write records a `category_source` of `MANUAL`
   or `AUTOMATIC` so that re-filing can replace the app's decisions and keep the
   user's. Neither can be recovered afterwards: asking whether the current rules
   would produce a stored category cannot tell a correct guess from a person
   agreeing with it, and would discard the agreement. Rows written before migration
   3→4 read as `AUTOMATIC`; see `CategorySource.LEGACY` for what that costs.

   `CaptureRecorder` is shared by the
   notification listener and the SMS receiver, so it takes `source` as a parameter.
   It used to hardcode `NOTIFICATION`, which made every barq and D360 record - banks
   with no app on the phone, so SMS only - claim it came from a notification. No test
   caught it: the dedup tests hand-built `Source.SMS` values the producer could never
   emit, so the suite proved the consumer while the producer was wrong.

10. **The check-then-insert in `recordCaptured` runs under a lock.** Two capture
   paths write from different coroutines, and the arrival pattern the feature exists
   for - a bank's SMS and that bank's own push, seconds apart - is exactly the one
   that interleaves them. Unlocked, both read the neighbour window before either
   inserts and the month doubles, with no error anywhere.

11. **A screen that compiles is not a screen that fits.** Layout is checked by
   running it, because the compiler has no opinion about width. Five type chips in
   a plain `Row` compiled, passed every unit test, and on a real screen wrapped the
   fourth chip to one letter per line and pushed the fifth off the edge entirely.
   Arabic labels are longer than the English ones, so the locale that matters most
   is the one that breaks first.

## Privacy

- No server, no account, no external API. Data never leaves the device.
- Fixtures under `core/src/test/.../fixtures` are **redacted**: names replaced,
  OTP codes replaced with `000000`, balances invented. Message structure, amounts
  and card last-four are kept, because those are what is tested.
- Never store a full account number, IBAN, or card number. Last four only.
- `/samples/` is gitignored. Real statements and exports stay out of the repo.

## Known gaps

- `:app` has been run only on the `masrouf35` emulator, never on a physical
  phone. Manual entry, the Room write, the Arabic RTL layout and the monthly
  total recomputing were all confirmed there; nothing else has been.
- **Neither capture path has yet seen a real bank message.** What is proven on
  hardware: the notification listener binds and refuses non-bank notifications; the
  SMS receiver fires on `SMS_RECEIVED`, extracts the sender, and has messages
  claimed by the right profile (`alrajhi` and `barq` both, by sender alone).
  What is not: a complete bank message becoming a stored row on a device. The
  emulator console cannot inject a newline, and every real bank SMS is multiline,
  so that last step is covered only by unit tests against the captured fixtures.
- barq and D360 have no app installed on the phone; their transactions arrive as
  SMS only. Package names for the apps that *are* installed were read off the
  device and are pinned in `ObservedBankPackagesTest` - that file should fail when
  a bank app is renamed.
- Nothing can be **edited**. A wrong record is deleted and typed again, which is
  deliberate for a captured one - it keeps every number the user vouched for a
  number they actually entered - but deleting a captured record also destroys its
  `rawText`, the one thing that cannot be typed back.
- Dismissing deletes the row, which frees its fingerprint, so an identical message
  redelivered later reappears. It needs the same second on the device clock, so it
  is rare; closing it properly means a `REJECTED` value on the core `Status` enum.
- Statement import is not wired into the app. Two consequences: `DuplicateDetector`'s
  one-day window is exercised only by the message paths, and `recordCaptured` still
  reconciles one record at a time. `DuplicateDetector.reconcile` takes a *list* on
  purpose - it pairs candidates as a multiset so two identical top-ups in one import
  produce two merges rather than one. Importing with `forEach { recordCaptured(it) }`
  would defeat that and silently merge real money. Whoever wires statement import
  must add a batch call that reconciles the whole file at once, inside the same lock.
- Nothing has been edited, only deleted and re-entered. Deleting a captured record
  destroys its `rawText`, the one field that cannot be typed back.
- No accounts and no date picker (a manual record is timestamped when it is saved).
- Categories are a fixed list in `SaudiCategories`, not a table. Nothing lets a
  person add or rename one, so a table would be storage for a value that never
  changes plus two seeding paths that can disagree. Give them editing and that
  reasoning expires. What *is* a table is `merchant_rules`: the user's own filing
  decisions, keyed on the folded merchant. Measured against a real 22,084-record
  history, the shipped merchant list plus `CategoryGuess.forType` file 83.7%; the
  remaining 3,595 are spread over 1,289 local merchants at about two each, so no
  list that ships in an APK will ever reach them. Filing one merchant files every
  transaction from it and every future one, which is the only mechanism that scales
  to that tail.
- A category is optional everywhere. Filing can wait; recording cannot, and a
  required category turns a five-second entry into one that gets skipped. Both
  paths keep the last choice, since several of the same kind in a row is the
  common case.
- `ColumnRuler` boundaries in `SaudiStatements` were measured with **pdfplumber's**
  coordinate system. A different PDF extractor on Android may report different x
  values — verify against a real file before trusting barq / Emirates NBD imports.
- barq and Emirates NBD row layouts are validated against a handful of rows, not
  a whole file.
- Any amount in a currency other than SAR is refused, not parsed. The amount is in a
  foreign currency and `Money` is integer halalas of SAR; reading "USD 1" as one
  riyal would fabricate a number and picking up the SAR balance line would be
  worse. 29 in a 5,074-message corpus. Closing it means letting the model hold a
  currency, not loosening the parser.
- The AlRajhi parser was measured against a real 5,074-message corpus: 4,033
  captured, 608 refused as one-time passwords, 369 with no recognised intent
  (almost all genuinely not transactions - login notices, marketing, OneCard
  vouchers), 52 with no amount. The remaining gaps are listed above.
