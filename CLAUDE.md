# masrouf — Claude context

Personal Arabic-first Android expense manager for Saudi bank messages and
statements. Single user, on-device, offline. Not a product, not published.

## Commands

```bash
./gradlew :core:test              # 140 tests, runs anywhere with a JDK
./gradlew :app:testDebugUnitTest  # 46 tests, needs the Android SDK
./gradlew :app:assembleDebug      # needs local.properties with sdk.dir
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
  model/      Transaction, TransactionDraft, enums
  time/       RiyadhTime · ArabicDates
  capture/    MessageGate → ParserRegistry → CapturePipeline · SaudiBanks
  dedup/      Fingerprint · DuplicateDetector · EventSignature
  statement/  RowAssembler · StatementImporter · SaudiStatements
app/    Android - Compose, Room, Arabic default with English in values-en
  capture/    MasroufNotificationListener · SmsCaptureReceiver (both thin)
              → SmsAssembly · CaptureRecorder (both tested, no Android)
  data/       TransactionEntity + mappers · TransactionDao · MasroufDatabase
  ui/         AmountInput (tested) · AddExpenseViewModel · AddExpenseScreen
```

## Rules that must not be broken

1. **Money is never a `Double`.** Integer halalas via `Money`. Excess precision
   is refused, not rounded: three decimals in a bank message means the format was
   misread, and rounding replaces a detectable bug with a plausible wrong number.

2. **`MessageGate` runs before any parser, always.** Use `CapturePipeline`; never
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

5. **`TransactionType.countsAsSpending` is the only place** that decides what
   enters a monthly total. Do not re-derive it per screen; two surfaces
   disagreeing about the same month is the failure this prevents.

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

9. **A screen that compiles is not a screen that fits.** Layout is checked by
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
- A pending record can be confirmed or dismissed, but not **edited**. A misread
  amount is dismissed and typed in by hand, which is deliberate - it keeps every
  number the user vouched for a number they actually entered - but it makes
  correcting a near-miss more work than it should be.
- Dismissing deletes the row, which frees its fingerprint, so an identical message
  redelivered later reappears. It needs the same second on the device clock, so it
  is rare; closing it properly means a `REJECTED` value on the core `Status` enum.
- Statement import is not wired into the app, so `DuplicateDetector`'s one-day
  window is exercised only by the message paths. Cross-source reconciliation
  between SMS and notifications is live and tested; a statement arriving later has
  never been reconciled against anything.
- No categories, no accounts, no date picker (a manual record is timestamped when
  it is saved).
- `ColumnRuler` boundaries in `SaudiStatements` were measured with **pdfplumber's**
  coordinate system. A different PDF extractor on Android may report different x
  values — verify against a real file before trusting barq / Emirates NBD imports.
- barq and Emirates NBD row layouts are validated against a handful of rows, not
  a whole file.
- No salary, bill-payment or ATM-withdrawal message samples yet; those parsers
  will report `NotUnderstood` until samples exist.
