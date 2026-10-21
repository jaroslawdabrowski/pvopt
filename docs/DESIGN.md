# Design system and UI rules

How PV Opt looks and why. The portable rules come from the sibling projects Finansik
(`../finansik/docs/DESIGN.md`, Part 1) and TurboOrders (`../turboorders/docs/DESIGN.md`) - read
those for the full reasoning; this file records what PV Opt adopted and what is its own. Exact
values live in `frontend/src/styles.scss` (tokens + shared classes).

History: the first look was Angular Material defaults with a cream page and a pastel "Bauhaus"
blue/yellow/terracotta palette - cream + terracotta is one of the stock "AI-generated" looks and
the Material toolbar/tables read as a component demo. Redesigned 2026-10-13.

## Adopted from the siblings (portable rules)

- **No Angular Material components are rendered.** Plain elements styled in `styles.scss`:
  `.card`, `.field` > `label` + `.input` (`.input-group .suffix` for units), `.field-grid`,
  `.segmented` (pill radio group - the language switch), `.list` > `.list-row` (`.list-icon`,
  `.list-main` > `.list-title` + `.list-sub`, `.list-end`), `.chip`, `.btn-primary` /
  `.btn-stroked` / `.icon-btn`, `.spinner` (`role="progressbar"`), `.error-box` / `.warning-box` /
  `.success-box` / `.empty-box`. The `@angular/material` package is still a dependency but unused.
- **Every value from a scale**: spacing `--space-1..9` (4-96), type `--text-xs..5xl` (12-48 px),
  radii (12 fields, 20 cards, pill buttons), shadows `--shadow-1/2/4` tinted with the ink.
  Components use semantic roles (`--surface`, `--text-primary/secondary/tertiary`,
  `--border-strong`, `--night`, ...), never ramp values.
- **Contrast**: text ≥ 4.5:1 (tertiary grey `#5c6678` is the palest grey allowed for text, 5.8:1
  on white); `--border-strong` (`#8892a3`, 3.1:1) for an input's only edge.
- **Colour is never the only signal**: every window colour comes with the window's name and
  icon (moon / sun); grid charging is a chip with an icon *and* a label.
- **Layout, mobile first**: desktop (> 900 px) has a fixed 248 px sidebar (brand, nav, language
  switch, logout); phones get a slim sticky top bar (brand, language switch, logout icon) and a
  floating bottom tab bar with all four destinations. `.page` pads its bottom on phones.
- **Forms**: labels above fields, units inside the field's right edge, 16 px inputs (iOS zoom),
  one filled primary action ("Zapisz ustawienia") in a sticky save bar.
- **Motion with a purpose only**: `:active` scale press feedback, nav icons fill when active,
  View Transitions cross-fade between routes (sidebar/top bar/tab bar stay still), boxes that
  appear because of an action drop in 4 px. Reduced motion respected.
- **mobile-native baseline**: `viewport-fit=cover, interactive-widget=resizes-content`,
  safe-area insets, no tap highlight, `overscroll-behavior: none`, hover only behind
  `@media (hover: hover) and (pointer: fine)`, `dvh` for the shell.

## PV Opt specifics

- **One colour per charge window, everywhere** (dial arc, legend, history icon, TOU slot):
  - **indigo = overnight** window (`--night`, moon icon);
  - **amber = afternoon** window and PV (`--day`, sun icon);
  - **green = cheap tariff now / slot charges from the grid** (`--cheap`).
  Each tone has `-strong`, `-tint` and `-on-tint`; `.tone-night/-day/-cheap/-neutral` set
  `--tone*` for a subtree. Ink `#151b28` is chrome, the SOC ring and primary buttons.
- **Page**: cool slate `#f3f5f8`, white cards, ink text. Font: **Hanken Grotesk**, one family -
  personality from weight 800 and tight tracking on titles and big numbers.
  Icons: **Material Symbols Rounded** (plain `<span class="material-symbols-rounded">`).
- **The one memorable element: the day dial on the dashboard** (`dashboard/day-dial.ts`). The
  day as a 24 h ring, midnight at the top: the cheap G12 windows as arcs in their window's
  colour (parsed from the settings' `cheapTariffWindows`, `dashboard/tariff-windows.ts`), round
  icon chips on the day ring - white with an ink `bolt` at each decision time (`update` for the
  03:30 recheck), an ink one with a sun by day / moon at night for "now" - explained by a key under
  the legend, with hover titles. The chips are HTML laid over the SVG (positions in % of the
  viewBox), because icon-font ligatures inside SVG `<text>` aren't reliable everywhere, and the battery SOC as the big number in the
  middle with the inner ring filled to it. Each window that holds a reserve (its latest decision
  charges from the grid) gets a badge with its target SOC at the middle of its arc - a white pill
  outlined in the window's colour. Reserves sit on the arcs, not on the battery ring: the outer ring
  is time and the inner one charge level, and a notch at "36% of the way round" on the inner ring
  read as a time of day. The legend repeats it per window ("zapas 36%" /
  "bez ładowania") - the battery now and the plan for it in one picture. The SOC ring filling up once on load is the app's
  only orchestrated motion.
- **Costs page**: a month switcher, three figures (bought minus sold, the same consumption without
  panels and battery, the saving in green text), then the day-by-day chart, where the cost came from
  (cheap zone, expensive zone, export credit at its average price) and a table of all days as the
  chart's table view. The chart is **one series**, so one colour: `--chart-cost` `#2a78d6`, the
  dataviz reference palette's slot 1, validated (lightness band, chroma floor, 3:1 on the surface),
  deliberately not a window colour. Columns grow from a zero line (below it when exports paid for
  more than was bought), ≤ 16 px, 4 px rounded at the data end only; the no-PV baseline is a short
  ink marker per day, a reference rather than a second series, so the saving reads as the gap.
  Whole-złoty axis, hairline grid, a tooltip beside the hovered day (never over it), partial days
  faded and named in the tooltip and the table, today marked "dziś".
- **TOU page**: a thin 00-24 bar of the six slots (governed slots tinted in their window's
  colour, solid when the slot charges from the grid) above the slot list.
- **History**: grouped by day ("Dziś", "Wczoraj", "niedziela, 11 października"); dates are
  formatted with `Intl` in the UI language (`core/format.ts`), not Angular's `DatePipe`, so no
  locale data has to be registered.
- **Copy**: sentence case, no ALL CAPS states ("Ładuje z sieci do 90%", not "WŁĄCZONE").

## Checking a change

There's no e2e suite yet. Build (`npm run build`), serve `dist/frontend/browser` behind a small
mock of `/api/*` and shoot every screen at 1280 px and as an iPhone 13 with Playwright - look at
every screen before shipping, and check contrast numerically for new text/background pairs.
