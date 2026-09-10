# Design

## Visual Theme

Material 3, as Google specifies it: tonal colour roles, surface containers for
elevation instead of shadows, the M3 type scale, and M3's components rather than
hand-rolled equivalents.

Light and dark are both fully specified. The default follows the system; the user
can override it in the app. Neither is the "real" theme with the other as an
afterthought, because the two situations this app is used in (Riyadh daylight,
a dark bedroom) are equally common.

The previous release used a Sadu weaving palette on a dark wool ground. That is
retired. What survives from it is not stylistic: the month strip, because a
segmented bar answers "what did the month go on" better than a bar chart per
weekday, and the receipt slip's torn edge, because it is the product's own
metaphor and matches the launcher icon.

## Color

Dynamic on Android 12 and later: the scheme is derived from the device wallpaper
(Material You), which is what following Google's Material 3 means on a phone.
The seeded schemes below are the fallback for older devices and the reference
for what the app looks like with no wallpaper to read. Category colours are not
part of the theme and never change with it.

Seeded, not hand-picked per role. One source colour generates both schemes so the
tonal relationships are M3's rather than invented.

**Seed:** `#2E5AAC` — a considered blue. Deliberately not M3's baseline purple,
which reads as an untouched template, and deliberately not the teal that the
rejected reference uses.

### Light
- surface `#FBF8FF`, onSurface `#1A1B21`
- surfaceContainerLow `#F5F2FA`, surfaceContainer `#EFECF4`, surfaceContainerHigh `#E9E7EF`
- primary `#3A5FA8`, onPrimary `#FFFFFF`, primaryContainer `#D9E2FF`, onPrimaryContainer `#001945`
- secondary `#575E71`, secondaryContainer `#DBE2F9`
- error `#BA1A1A`, errorContainer `#FFDAD6`
- outline `#757780`, outlineVariant `#C5C6D0`

### Dark
- surface `#121318`, onSurface `#E3E1E9`
- surfaceContainerLow `#1A1B21`, surfaceContainer `#1E1F25`, surfaceContainerHigh `#292A30`
- primary `#AEC6FF`, onPrimary `#05306B`, primaryContainer `#22468E`, onPrimaryContainer `#D9E2FF`
- secondary `#BFC6DC`, secondaryContainer `#3F4759`
- error `#FFB4AB`, errorContainer `#93000A`
- outline `#8F909A`, outlineVariant `#45464F`

### Category colours

Nineteen, one per category, and they are data rather than decoration: the strip is
unreadable if two categories are hard to tell apart. Specified per theme, because a
colour legible on `#121318` is often invisible on `#FBF8FF`.

The rule is MUTUAL distance, not adjacency. It was written as "adjacent categories
are kept far apart in hue" when there were eight; with nineteen, and with the strip
sorted largest-share-first, any two categories can end up side by side in some
month, so adjacency is not a property the palette can be designed against. Every
pair clears a CIE76 floor of 13 instead, asserted by `CategoryCoverageTest` - which
measures it rather than checking the colours are merely different, as it did while
two pairs shipped 5.8 and 4.2 apart.

Lightness is what carries legibility against the surface, so a colour is retuned by
rotating its hue and leaving its lightness alone.

Uncategorised is deliberately the dimmest, closest to the surface: it should read
as absence, not as a ninth category.

## Typography

M3 type scale, with IBM Plex Sans Arabic bundled for every role. The system Arabic
face is a Naskh and makes the app look like a default; Plex Arabic shares a
skeleton with its Latin, so Arabic labels and the Western numerals this app insists
on sit on one line without looking like two typefaces.

- Display for the figures a screen is built around - the month total, the amount
  being typed, an answer's result - with tight tracking so a five-figure number
  reads as one object.
- Headline where a figure's currency mark has to be sized against it, and for the
  slip's own amount: a mark whose height IS the digit height of its style comes out
  a speck at title size beside a display-size total.
- Title for section headings.
- Body for transaction rows.
- Label, widely tracked, for captions and metadata.

Numerals are always Western, in both languages, matching what Saudi banks print.

## Components

M3 components, not lookalikes: `Card`, `FilledTonalButton`, `FilterChip`,
`ListItem`, `TopAppBar`, `ModalBottomSheet`, `SegmentedButton`, `AlertDialog`.

Elevation via `surfaceContainer` tones, not shadows.

Two custom pieces earn their place:
- **Month strip** — a segmented proportional bar, mirrored by layout direction.
- **Receipt slip** — a torn lower edge, matching the launcher icon, on the one
  surface that shows the bank's own words.

## Layout

Three destinations, and a navigation bar because there are three. **Spending** is
a single scrolling screen in reading order: cards, month, what needs you, history.
**Income** is salary and bonuses over the years — a different question over a
different span, which is the whole reason it is not a panel on the other. **Ask**
is a typed question and the figure it comes to, with the records underneath.

Ask is a destination rather than a search field on the spending screen, and the
argument is that it does not narrow that screen — it answers a different question
over a span the spending screen does not have. "How much on petrol in 2024" is not
the current month filtered; it is its own view of the history, and giving it the
top of the spending screen would make the month total the answer to a question
nobody asked. The history's own search box stays where it is and does what it has
always done: narrow the list in front of you.

Three is also the point at which the bar is unarguable. Two destinations can be a
toggle; three want a bar, which is what M3 says a bar is for.

The bar persists. It does not hide on scroll and it does not float, and both were
tried on a real screen before being refused.

Hiding it was the worse of the two: M3 hides app bars on scroll, never the
navigation bar, and one small downward drag took away the only route to the other
destination. Navigation you have to hunt for costs more than the height it saves.
Floating is Google Photos' own pattern rather than anything in the specification,
and this screen already has a floating button in the same corner — two floating
things over a column of figures is how a number gets covered.

Recording an expense is a button, not a form on the page.

Spacing on a 4dp grid, varied for grouping. No nested cards. No side-stripe accent
borders.
