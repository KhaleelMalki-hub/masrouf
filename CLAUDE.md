# masrouf — Claude context

Personal Arabic-first Android expense manager for Saudi bank messages and
statements. Single user, on-device, offline. Not a product, not published.

## Commands

```bash
./gradlew :core:test        # 140 tests, runs anywhere with a JDK
./gradlew :app:assembleDebug   # needs local.properties with sdk.dir
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
app/    Android — not written yet
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

## Privacy

- No server, no account, no external API. Data never leaves the device.
- Fixtures under `core/src/test/.../fixtures` are **redacted**: names replaced,
  OTP codes replaced with `000000`, balances invented. Message structure, amounts
  and card last-four are kept, because those are what is tested.
- Never store a full account number, IBAN, or card number. Last four only.
- `/samples/` is gitignored. Real statements and exports stay out of the repo.

## Known gaps

- `:app` does not exist.
- `ColumnRuler` boundaries in `SaudiStatements` were measured with **pdfplumber's**
  coordinate system. A different PDF extractor on Android may report different x
  values — verify against a real file before trusting barq / Emirates NBD imports.
- barq and Emirates NBD row layouts are validated against a handful of rows, not
  a whole file.
- No salary, bill-payment or ATM-withdrawal message samples yet; those parsers
  will report `NotUnderstood` until samples exist.
