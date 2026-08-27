# masrouf — Claude context

Personal Arabic-first Android expense manager for Saudi bank messages and
statements. Single user, on-device, offline. Not a product, not published.

## Commands

```bash
./gradlew :core:test              # 140 tests, runs anywhere with a JDK
./gradlew :app:testDebugUnitTest  # 26 tests, needs the Android SDK
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
  capture/    MasroufNotificationListener (thin) → CaptureRecorder (tested)
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
- **The listener has never seen a real bank notification.** On the emulator it was
  proven to bind, receive and refuse a shell-posted notification as
  `UNKNOWN_SENDER`; that the bank profiles match a real posting package is still
  only asserted by unit tests against captured message *bodies*. The package names
  in those tests were inferred, not observed. Check them against a real phone
  before trusting capture.
- **Nothing confirms a `PENDING` record yet.** Captured transactions are stored and
  counted on screen but are excluded from the monthly total, and there is no screen
  to confirm, edit or delete one - so today capture fills a list the user cannot
  act on.
- No SMS capture, so `DuplicateDetector` is not wired up. The unique fingerprint
  index collapses a notification Android reposts, which is the only duplicate that
  can currently occur; the moment SMS or statement import lands, cross-source
  reconciliation has to be added or every purchase seen twice becomes two records.
- No categories, no accounts, no date picker (a manual record is timestamped when
  it is saved).
- `ColumnRuler` boundaries in `SaudiStatements` were measured with **pdfplumber's**
  coordinate system. A different PDF extractor on Android may report different x
  values — verify against a real file before trusting barq / Emirates NBD imports.
- barq and Emirates NBD row layouts are validated against a handful of rows, not
  a whole file.
- No salary, bill-payment or ATM-withdrawal message samples yet; those parsers
  will report `NotUnderstood` until samples exist.
