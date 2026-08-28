# Product

## Register

product

## Users

One person: the developer, in Saudi Arabia, on an Android phone, reading Arabic
first and English second. They check the app in two very different places, and
both matter:

- Standing outside in Riyadh daylight, glancing at what a purchase just cost.
- In bed at night, working through the transactions the day's bank messages
  produced.

That split is why the app cannot pick a single theme on aesthetic grounds. It was
dark-only for one release on the reasoning that the palette looked better dark,
which is the wrong basis for the decision and unreadable in the sun.

The job to be done: know what this month has gone on, without doing data entry.
The app reads the bank's own messages; the person's work is confirming what it
read and saying what it was for.

## Product Purpose

An offline, on-device expense record built from Saudi bank SMS and notifications.
No server, no account, no network permission at all. It captures transactions the
banks announce, deduplicates the same purchase arriving by two routes, and asks
the user to vouch for each one before it counts.

Success is a monthly total the user trusts, broken down by category, that they did
not have to type.

## Brand Personality

Precise, quiet, unembarrassed. Three words: **exact, calm, Arabic-first.**

The product's whole claim is that its numbers are right. The interface should feel
like something that would rather show nothing than show a number it is unsure of,
because that is literally how the parsers behave.

Not playful. No mascots, no emoji as UI, no celebration animations. A person
looking at money they have already spent does not want to be congratulated.

## Anti-references

- **Drahim (دراهم).** Explicitly rejected by the user. Teal on near-black,
  floating rounded cards, stacked bar charts by weekday, emoji budget avatars,
  five-tab bottom nav with a plus FAB. The whole reason this app was designed
  rather than cloned.
- **Bank apps.** Dense product cross-sell, rewards banners, marketing tiles
  competing with the balance.
- **Gamified finance apps.** Streaks, confetti, encouraging copy about saving.
- **Anything that looks AI-generated:** identical card grids, gradient text,
  hero-metric templates, side-stripe accent borders.

## Strategic Design Principles

1. **The captured record is the hero, not a form.** The app's value is that entry
   already happened. A screen of empty fields above the answers gets the priority
   backwards.
2. **Show the evidence.** This is the only app of its kind that keeps the bank's
   original message. When asking "is this right?", show what the bank actually
   wrote. It turns an unanswerable question into a two-second one.
3. **Uncertainty is visible, never smoothed over.** Pending is not confirmed.
   Unfiled is not "other". A parser that could not read a message says so.
4. **Both languages are first-class.** Arabic is the default locale, not a
   translation. Layout direction comes from the locale and is never hard-coded.
   Numerals stay Western in both, because that is what Saudi banks print.
5. **Both themes are first-class.** Light for daylight, dark for night, the
   system's choice by default and the user's choice when they want it.

## Accessibility

- Touch targets at least 48dp. This has been violated by chips and text buttons
  before and is checked on each pass.
- Every row a screen reader hits should be one node describing one transaction,
  not eleven fragments to reassemble.
- Colour is never the only carrier of meaning: the category strip has a labelled
  legend, and direction is shown with a sign as well as a colour.
- Contrast at WCAG AA in both themes, verified rather than assumed.
- Reduced motion respected. The one animation in the app is decorative.
