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

Eight, one per spending category, and they are data rather than decoration: the
strip is unreadable if two categories are hard to tell apart. Specified per theme,
because a colour legible on `#121318` is often invisible on `#FBF8FF`. Adjacent
categories in display order are kept far apart in hue, since they sit next to each
other in the strip.

Uncategorised is deliberately the dimmest, closest to the surface: it should read
as absence, not as a ninth category.

## Typography

M3 type scale, with IBM Plex Sans Arabic bundled for every role. The system Arabic
face is a Naskh and makes the app look like a default; Plex Arabic shares a
skeleton with its Latin, so Arabic labels and the Western numerals this app insists
on sit on one line without looking like two typefaces.

- Display for the month total only, tight tracking so a five-figure number reads
  as one object.
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

Two destinations, and a navigation bar only because there are two. **Spending** is
a single scrolling screen in reading order: cards, month, what needs you, history.
**Income** is salary and bonuses over the years — a different question over a
different span, which is the whole reason it is not a panel on the other.

The bar hides on the way down and returns on the way up, mirroring the top bar. It
is not floating: that is Google's own pattern rather than anything in M3, this
screen already has a FAB in the same corner, and two floating things over a column
of figures is how a number gets covered — which has happened here once.

Recording an expense is a button, not a form on the page.

Spacing on a 4dp grid, varied for grouping. No nested cards. No side-stripe accent
borders.
