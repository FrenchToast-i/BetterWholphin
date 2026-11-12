# Agent notes

## Build environment

- A full JDK is required (21 works). With a JRE-only install Gradle fails with
  `No Java compiler found` while compiling the version catalog, which does not mention Java.
- `compileSdk`/`targetSdk` are 37: install `platforms;android-37.0` (the 37 platform is published
  with a minor version, so plain `android-37` does not exist) plus `build-tools;37.0.0`.
  Building against 36 fails in `DebugPage.kt` (`HDR_TYPE_HLG_PLUS`) and `SwitchServerViewModel.kt`
  (`CINNAMON_BUN`, `ACCESS_LOCAL_NETWORK`).
- `local.properties` needs `sdk.dir=...`. Version name/code come from `git describe`/tags, so a
  shallow clone reports a suffix like `1.0.9-31-g38bd690`.
- Useful commands: `./gradlew :app:assembleDefaultDebug`,
  `:app:testDefaultDebugUnitTest` (Robolectric/MockK suite), and
  `:app:dependencies --configuration defaultDebugRuntimeClasspath`.
  There is **no** spotless/ktlint Gradle task even though DEVELOPMENT.md mentions it.
- TV emulator: `system-images;android-34;android-tv;x86` with device `tv_1080p`. That image is
  32-bit x86 + armeabi-v7a, so install the *universal* debug APK; the ABI splits only cover
  arm64-v8a, armeabi-v7a and x86_64.

## Liquid Glass mode

- Preference is `InterfacePreferences.liquid_glass` (proto field 16), surfaced as
  `AppPreference.LiquidGlass` in the Interface group and read anywhere via `LocalLiquidGlass`.
- The material library is KMPLiquidGlass, published as `io.github.kashif-mehmood-km:backdrop`
  (`0.0.1-alpha02`); there is no `kmpliquidglass` artifact. It ships Modifier extensions
  (`drawBackdrop`, `layerBackdrop`, `rememberLayerBackdrop`), not a composable wrapper.
- `ApplicationContent` wraps the whole background stack (artwork + palette wash) in
  `Modifier.layerBackdrop` and provides `LocalGlassBackdrop`; `Modifier.glassSurface` samples it.
  Content rendered outside that provider (dialogs, screensaver) gets the tint fallback.
- The material is the library, end to end: `vibrancy()` → `colorControls` → `blur` → `lens`
  (`refractionAmount` is **negative**, so the pane samples inward and the art reads magnified)
  → `opacity`, decorated by the library's `Highlight` (rim light), `InnerShadow` (the dark band
  along the far edge) and `Shadow` (lift). Do **not** hand-paint a bevel, specular line, gradient
  outline or custom shader into `glassSurface`: the whole point of glass mode is that every pane
  is the same material under the same lighting model, and stacked painted effects are what made
  earlier versions look like a stroked rectangle.
- The material is frosted glass, not a clear window: `panelBlur` 10dp, `barBlur` 8dp. The blur
  separates the pane from the artwork; the `lens` band makes it glass. `refractionHeight` 13dp /
  amount -24dp on panels and 10dp / -16dp on the 50–66dp capsules keeps the bend in a narrow
  wall at the rim instead of zooming the whole face (do not enable `depthEffect`). Frost is 4.5%
  white in the content layer and 13% on chrome (`BAR_FROST`) so capsules hold white labels.
- The rim is `HighlightStyle.Default(angle = 45f, falloff = 1.5f)`: the shader lights
  `abs(dot(normal, light))`, so a low falloff lights most of the outline and reads as a sheen
  while a high one concentrates the light into the corners. Panels: width 2.4dp, blur 4dp,
  alpha 0.72; capsules 2dp / 3.2dp / 0.78; a focused control 2.8dp / 5dp / 1. Not a 1px stroke.
- `Modifier.glassCapsule()` is the one definition of chrome material (tint, blur, tight lens,
  frost, rim, elevation). The top bar, the sub-navigation strip, the search field, the icon
  capsules and the alphabet rail all call it; a new capsule must not repeat the token block, or
  the bar and the strip drift into two subtly different materials.
- The alphabet rail is **two columns**, split A–M / N–Z. One column of 27 cells at a legible
  12sp is 444dp, taller than a television's grid area, so it used to end at T and scroll — which
  reads as a clipping fault rather than as a control. Two columns of 14 × 18dp fit with air to
  spare, every letter is always on screen, and the rail needs no scroll state at all. Each
  column is measured explicitly (`(railWidth - 8dp) / 2`): `fillMaxWidth` inside a Column takes
  the *row's* whole constraint, so two such columns each claimed the full rail and the second
  hung off the right edge of the television. The rail still measures its height
  (`BoxWithConstraints`) and shrinks the cells on a short window instead of running off the
  bottom.
- `InnerShadow`'s offset is **negative**: the library clears the shape shifted by the offset and
  keeps the remainder, so `DpOffset(0, -2dp)` leaves the dark band at the bottom inside edge
  (the thickness of the slab where the light does not reach).
- A text shadow must not be added to glass labels; the material supplies the contrast. Text
  shadows were the "Windows Vista" look the user rejected.
- `androidx.tv.material3.Surface` does **not** centre its slot: content is measured at natural
  size and pinned top-left. Every fixed-size pill/icon/button must put its content in a
  `Box(Modifier.fillMaxSize()/fillMaxHeight(), contentAlignment = Center)` — this was the
  long-standing "labels and icons look offset" bug in the bar and hero buttons.
- Do not append a custom `effect(RenderEffect.createRuntimeShaderEffect(...))` with its own
  `padding` to the backdrop chain: it crashes with `IllegalStateException: LayoutNode should be
  attached to an owner` during measure. Use the library's `lens` for magnification.
- Real blur needs API 31+, AGSL refraction/highlight API 33+ (`GlassTokens.canBlur/canRefract`).
  Below that the same modifier degrades to a translucent, shape-clipped pane instead of a clear
  window; on API 31–32 `vibrancy`/`lens` are silent no-ops, so panes still draw, just plainer.
- `GlassTokens.heroTint` (30% black) is the one content pane that is not clear, because the hero
  carries the title *logo* as well as copy: a logo is drawn in its own colours, so over artwork
  of the same hue (a Halloween special is orange on orange) clear glass makes it vanish. It stays
  below `copyTintOnLight`, so pale art still smokes harder through the copy rule. The hero's
  metadata line is `QuickDetails(oneLine = true)`: as a Row of separate texts each measured to
  its own width, "… · Ends at 11:20" ran under the pane's rounded corner and lost its minutes.
  Logo boxes are larger than they look like they need to be (44dp here) because a logo image
  carries its own transparent margin, and a three-line logo's first line thins out below that.
- `MaterialTheme.colorScheme.onSurface`/`onBackground` are dark inks in the glass scheme. Any
  page-level copy drawn straight onto the artwork (empty grids, placeholders) has to switch to
  white in glass mode, or it reads as a rendering fault — see the empty state in `CardGrid`.
- Type lives in `GlassType` (`ui/theme/Type.kt`) and spacing in `GlassTokens`. Material's default
  scale is sized for a phone; glass mode steps roles up and tightens tracking, and its numbers are
  all relative to a 960×540dp television, so a 4dp change is visible from the sofa.
- `OverviewText`'s default `textBoxHeight` is `maxLines * 20dp`, the classic line height. Glass
  type is taller, so a glass header must pass `Dp.Unspecified` (as the detail and episode headers
  do) or the last line is clipped. It also collapses paragraph breaks before drawing the preview:
  Jellyfin descriptions often contain blank lines, those count against `maxLines`, and Compose
  then paints the ellipsis alone on a line of its own.
- `WatchedIcon` fills its disc from the glass theme's white `border` and ticks it white in every
  classic theme; on glass that drew a white tick on a white dot, so a watched poster grew a blank
  badge. The glass branch is a white disc with a **black** tick and the existing hairline ring —
  the same white-fill/dark-glyph signal as a selected bar pill.
- Media cards use `glassCardShape/Border/Glow/Scale` rather than sampling the backdrop: one blur
  pass per card is too expensive on TV hardware.
- Glass mode keeps the top bar on `Destination.Settings`, which is `fullScreen` in the classic UI.
- `onDrawSurface` **does not composite above a layer backdrop** in `0.0.1-alpha02`: panes kept
  their rim, lens and blur while every tint, frost and scrim given to that callback silently went
  missing. `glassSurface` therefore paints its fill in `drawWithContent` (fill, then frost, then
  the copy scrim, then `drawContent()`), which is why pane tints actually work. That block paints
  *under* the children, so it must never move after `drawContent()` — anything drawn after it
  dims every label in the pane.
- A pane is only ever a tint, a frost and a copy scrim. Edges, sheens and glows are decoration
  and belong to the library's `Highlight`/`InnerShadow`/`Shadow`, not to hand-painted draw calls.
  All three are painted by one `DrawScope.drawGlassFill`, shared by the sampled path and the
  no-backdrop fallback, so the fallback is the same substance without a blur — clipped to the
  pane's shape, because a rectangular fill behind a 30dp corner gives it away.
- Copy panes adapt to the artwork through `LocalBackdropLuminance` (a 0–1 mean published by
  `BackdropService` via `LocalBackdropLight`'s provider). A clear pane over a white frame is a
  white pane, and white labels on it cannot be read from a sofa. The smoke is a **ramp**, not a
  threshold: `GlassTokens.copySmoke(luminance)` goes from nothing at 0.30 to
  `copyTintOnLight` (52% black) at 0.72. The old boolean jumped from clear to 52% at 0.55, which
  left every busy mid-tone television still in the legibility gap the material exists to close.
  Panes opt in by passing `scrim > 0`, and a pane already tinted harder than the ramp (a dialog)
  keeps its own tint rather than being thinned. The luminance is measured whenever glass mode is
  on, even if the dynamic colour wash is disabled, because the smoke depends on the artwork
  rather than on the palette.
- Copy that is not inside a pane still has to be legible. A grid's "nothing here" line used to be
  white type straight on the artwork; over a pale cartoon frame it vanished, which reads as a page
  that failed to draw rather than one that is deliberately empty. `EmptyGridMessage` in `CardGrid`
  is a `glassSurface(scrim = 1f)` card, so the same copy-smoke ramp that protects detail sheets
  protects it: clear over dark art, smoked over light, always with an edge. `LoadingRowPlaceholder`
  takes the equivalent branch in ink (white title, 66% white message), because a row that is still
  loading is exactly the moment a dark-on-dark placeholder is unreadable.
- The library header's controls are all one material: `SortByButton`, the filter disc and the
  view-options disc render through `Button(glassFilled = true)` (`TextButton` forwards the flag),
  the smoked capsule that floats on artwork. Two of the three were left on the classic opaque
  `surfaceVariant` chip and the row read as two greys and a glass disc three across.
- Every control whose focus fill is `colorScheme.border` becomes a **white** disc on glass, so
  its glyph must flip to black on focus. The playback transport, the music-player transport and
  the watched badge all drew white-on-white and read as empty circles; they now share the bar's
  white-fill/dark-glyph signal. `PlaybackButton`/`PlaybackFaButton` track focus locally for this
  (they take an optional `interactionSource`, but the focused color is what has to change).
- Detail headers pass `oneLine = true` to `QuickDetails` in glass mode: the pane is 60% of the
  page, so the Row variant measures its last piece past the rounded corner and clips it —
  an unplayed movie's "Ends at 3:44 PM" was rendering as a lone `•` at the end of the line. The
  one-line form ellipsizes the sentence instead.
- Chrome adapts on its own ramp, `GlassTokens.chromeSmoke`, which starts earlier (0.22) because
  a capsule's frost adds white: `glassCapsule` interpolates `barTint` → `barTintOnLight`
  (55% black) and thins its frost to 40% with the same fraction. Frost is white light, so over a
  white frame it is the thing that makes the bar a pale slab and washes the labels out; the
  smoke has to out-run it. Numbers here are measured against the frost on top, not against a
  bare tint — 52/55% is what lands near 4:1 once the frost is included, not the 34–38% a
  tint-only calculation suggests.
- Dialogs must pass `backdrop = null` (`glassDialogSurface` does). A `Dialog` is its own window,
  so it inherits `LocalGlassBackdrop` but cannot sample the shell's layer: the library draws
  nothing and the pane comes out as a pale milky wash over the whole screen. The fallback tint is
  the correct material there, and the OS blurs the app behind the window anyway. The player's
  bottom menus go through the same modifier.
- Playback is the other place with nothing to sample: the video is a platform surface, not part of
  the Compose layer, so a pane over it would refract the page artwork hidden behind the player.
  `Modifier.glassPlayerDeck()` is therefore the tint fallback — a floating slab holding the seek
  bar and transport, inset 24dp so the picture keeps its frame. It is in `PlaybackControls` only;
  chapter and queue rows stay bare over the scrim.
- Emulator screenshots opened downscaled in the preview acquire a hard white 1px rim by
  resampling. Judge the edge on a 2–3× crop of the PNG, not on the preview image: the rim should
  ramp over a few pixels instead of spiking to 255 at the boundary.
- The emulator is 1920x1080 at density 2.0, so 1dp = 2px.
- Glass mode calls `Backdrop(fullBleed = true)` and then `GlassBackdropWash`. The stock backdrop
  is a 70% corner image, which leaves most panes refracting nothing; full-bleed art gives the
  material texture, and the wash keeps the stock behaviour of the frame taking its palette from
  the focused item. Both still follow focus — do not replace the artwork with a static gradient.
- The full-bleed scrims are deliberately light (30% left, 55% bottom). They exist for text and
  shelf contrast; darkening them further removes the very thing the glass is refracting.
- Navigation is a tvOS-style top bar (`GlassTopBar`): separate capsules for identity, all
  destinations, the clock, and settings. `GlassTokens.contentTop` is the padding pages leave for
  it; the classic drawer path is untouched.
- Every control on the bar can be switched off under Interface preferences (glass only): Home,
  Now Playing, Search, Settings, and the profile capsule; the clock uses the
  existing `Show Clock` setting. They are stored as `hide_*` flags in `TopBarPreferences` so that
  an absent message means "visible" rather than an empty bar.
- The home page draws **as each row arrives**, not when the last one does. A cold start that
  included a slow live-TV query used to hold the whole screen behind a spinner for the better
  part of a minute; the rows are independent requests, so each shelf now draws its real heading
  and fills in, and `loadingState` is `Success` from the first frame. The initial focus waits for
  a row that actually has cards (a placeholder leaves the composition when the real row lands,
  and a request into one is a request into nothing), anchors the list at item 0 first, and is
  given the *default* bring-into-view spec: the custom one exists to keep a heading visible while
  the viewer moves between shelves, but on first focus it slid the first row's heading off the
  top of the page.
- Preferences reach pages through `LocalUserPreferences`, provided above the navigation host. A
  page is composed inside a `NavEntry` that is built once per destination, so a `preferences`
  value captured in the entry lambda goes stale: reading it inside the entry (or taking it as a
  composable parameter from `ApplicationContent`) is what makes a setting apply without
  navigating away and back.
- Bar pills are text-only on purpose: glyphs add ~25dp each, which pushes the last destination
  off the screen and clips it at the capsule edge.
- **Play something** is a disc in the bar (Font Awesome's `fa_shuffle`, since the material icon
  set has no shuffle mark), one press from any page. It is the app's answer to "what should I
  watch?" and it starts playback immediately: browsing is the expensive part of a media client.
  It sits beside Search because both are global actions rather than places, and unlike Search it
  is **not** hidden on Settings or Search — starting something from there is exactly the point.
- `SurpriseService` owns the pick and the taste model lives in `SurprisePicker` (pure, unit
  tested). Genres of the last 120 played movies/episodes are weighted by recency, so what the
  viewer just finished counts most; a candidate with no matching genre keeps a 0.25 base weight
  so an unfamiliar library is not a dead end. It picks a **movie or a series, never an episode**
  — `Destination.Playback(seriesId)` already answers a series with its next unwatched episode and
  the rest of the queue, which is why "play something" on a show continues the story. Finished
  titles are excluded when anything is unwatched, and the last five picks are never repeated (a
  weighted draw over a small library happily returns the same title twice, which reads as a
  broken button).
- **Tonight** is the moon disc beside Play something: the page that answers "what can I finish
  before bed?" (see the Tonight section below). It is a disc rather than a destination pill
  because six libraries plus a pill overflowed the capsule, and a moon is recognisable at 50dp.
  The clock never yields to the libraries: on a full bar it shrinks to a **watch face** disc —
  the time over its meridiem — instead of disappearing. Hiding the readout whenever the viewer
  owned six libraries traded a feature for a layout, and a television without a clock is worse
  than a smaller one. The split is found by shape (`splitClockFace`, unit tested), not by the
  last space: a locale can put the day period first ("PM 4:16") or run it straight onto the
  digits ("下午4:16"), and the space-based split left those strings as one long line in a 50dp
  disc. 24-hour locales carry no day period and draw the single line.
- **The bar is one height.** Every capsule — the destinations capsule, the profile disc, the
  moon, the shuffle, search, the clock and settings — measures `GlassTokens.pillHeight` (50dp),
  and `GlassTokens.barHeight` is that same number. It used to be 66dp with the destinations
  capsule stretched to the full height, which read as two ranks of chrome that meant nothing: a
  library is not more important than search. The selected pill fills the capsule rather than
  sitting at a height of its own, so it reads as the current page and not as a lit label. The
  bar's gap is 8dp so that profile + every destination + both global actions + clock + settings
  still fit a 960dp television without clipping the last destination's label.
- Search is a bar capsule, not a floating button: a hovered-over-grid control would cover
  posters and sit outside the D-pad path, so it lives in the bar one press away and stays
  visually separate from the library destinations. It is hidden on the Search page itself
  and on Settings, where it would land on the switches.
- A selected bar pill is pure white, so focus cannot be shown with light alone — it uses a dark
  rim plus a bigger scale, otherwise a focused tab looks identical to an unfocused one. Focus
  outlines are dark, never white; `BarPill`/`TabRow` rely on a dark rim plus the focus fill.
- `ui/components/TabRow.kt` has a glass branch that renders sub-navigation as capsules; every
  library/search/series page inherits it.
- `PreferencesContent` paints `surfaceColorAtElevation(1.dp)` behind the list; that has to be
  skipped in glass mode or it covers the glass sheet.
- Dialog ink: `BasicDialog` and `ScrollableDialog` provide `LocalContentColor` as white in glass
  mode. A dialog is its own window with no page surface to inherit an ink from, and the glass
  theme's `onSurface` is a dark ink, so unstyled labels were dark-on-dark over the smoked pane.
  New dialog content should either leave colour unspecified (the provider handles it) or branch
  on `LocalLiquidGlass`; hand-rolled `Dialog`s (the view-options popups, the programme card, the
  playlist picker) call `Modifier.glassDialogSurface(...)` and wrap their content in the same
  provider rather than painting their own elevation background.
- The Live TV guide is one pane of glass behind the whole grid (`TvGuideGridContent`), not a pane
  per cell: a schedule is dense, and 200 glass tiles would both cost a blur pass each and look
  like confetti. Cells thin to white-alpha fills, the channel column loses its opaque backdrop,
  and the timeline keeps its gaps as part of the sheet.
- Per-account discovery: `AccountPreferences` carries `tmdbApiKey` and `seerrEnabled` as well as
  `liquidGlass`. Seerr's *credentials* were already per Jellyfin user (`SeerrUser.jellyfinUserRowId`,
  restored by `UserSwitchListener`); `seerrEnabled` is the separate question of whether that
  profile sees the Discover door, the requests rail and the request actions. It is combined into
  `NavDrawerService`'s `discoverActive`, so switching profile adds or removes the destination
  without a restart, and Home skips refreshing the requests rail when it is off. Both account
  switches live in the Discovery group on the settings page and write through
  `AccountPreferencesService`, never the device preference file.
- The glass settings sheet is a **fixed pane with air at both ends** (10dp top, 22dp bottom), and
  its list is `weight(1f)` under the title — not `fillMaxSize`, which measured the list against
  the whole column and painted the last rows past the pane's rounded bottom. A sheet flush with
  the screen edge loses its corners and reads as a page that scrolled.
- Settings group labels scroll with their rows in glass; they are not `stickyHeader`s. A pinned
  label is pushed up across the last row of its own group as the next group arrives, and the
  translucent header wash left both labels readable on top of each other at every boundary. The
  sheet's own title stays at the top, which is the orientation the list actually needs.
- On the TV emulator the home backdrop artwork can take ~20s to appear after a cold start; glass
  looks art-less and washed out until Coil/Jellyfin deliver it. Not a layout bug.

## Session continuity

- A cold start re-opens the page the viewer was on (`ContinuityService`, written from
  `NavigationManager.onBackStackChanged`). Android TV reclaims a backgrounded app without any
  saved instance state, so without this a viewer browsing a series came back to Home every time.
  The activity's own `savedInstanceState` path is unchanged and wins when it exists: continuity
  only fills the gap the system did not restore.
- The record is keyed by user id and carries a timestamp, and both are checks, not decoration: a
  profile switch must not reopen somebody else's page, and a screen from a month ago is a
  surprise rather than “where you left off”. `ContinuityPolicy` holds the rules and is unit
  tested, because the interesting question is what it is safe to bring back, not where it sits.
- **A player is not a place.** Playback, playback lists and slideshows are
  stripped when the stack is written, so a crash in the middle of an episode reopens the detail
  page with its Resume button rather than restarting the media. Restoring into a stale session
  is precisely the behaviour a resume prompt exists to prevent.
- Restore only happens while the back stack is a single `Home` entry. A configuration change, a
  restored instance state and a hot reload already carry a deep stack, and re-applying a
  remembered one would push the viewer backwards out of wherever they actually are.
- `DestinationCodec` is the one JSON configuration for the stack; the activity's save path and
  the continuity store must agree, or one writes a format the other cannot read.

## Detail headers

- A detail header is a pane, not type floating on artwork: `MovieDetails`, `SeriesDetails` and
  now the collection page wrap title, metadata, synopsis **and the action row** in one
  `GlassPanel(scrim = 1f)` at 66% width. The collection page was the last one still drawing its
  copy straight onto the backdrop, and a collection is the worst case for it — the header follows
  the focused member, so the text lands over whichever poster was last highlighted, and white
  copy over a white star on a red shield is genuinely unreadable.
- The actions belong inside the pane with the copy. Outside it they start at
  `HeaderUtils.startPadding` while the pane starts at `GlassTokens.screenInset`, which leaves two
  left edges a few dp apart — visible at ten feet as a page that cannot line up its own blocks.
  The movie page has always travelled the play buttons with the header for the same reason.
- Glass headers drop the fixed 180dp `HeaderUtils.height` and measure to their content; the
  classic slot stays fixed.

## Android TV home screen

- The recommendations channel (`TvProviderWorker`) caches its channel id in the `channels`
  preferences. That id is not permanent: the viewer can remove the row, and the launcher can drop
  the app's channels. Publishing against a dead id fails every program insert with a foreign key
  error, and because the id was never re-checked the row stayed gone for good. The worker now
  verifies the channel exists (recreating it when it does not) and rebuilds once if an insert is
  still rejected, so a tidied home screen costs one refresh rather than the feature.

## Search

- The empty page carries **recent searches** above the trending shelf (`RecentSearchService`,
  `RecentSearches.kt`). They are stored per user through `KeyValueService`, like the excluded
  search types, so two profiles on one television do not read each other's history. The writes
  run on the application's `@IoCoroutineScope`, not a ViewModel scope: leaving Search is exactly
  when a query is recorded, and a ViewModel job would be cancelled by the same navigation.
- A query is recorded on an explicit search and when the page is disposed, because the page
  searches as you type — most viewers never press the search key, so a history recorded only on
  that key would stay empty forever. `mergeRecentSearches` trims, drops repeats regardless of
  case (keeping the spelling it was first stored with, so a chip does not change case under the
  viewer), and keeps six — two chip rows, which is what fits above the shelf.
- Down from the field goes to the *newest* chip via `focusProperties { down = ... }`. Left to
  geometry, the nearest chip below the wide field is "Clear" at the end of the row, so one press
  of down and one of select would wipe the history the viewer was reaching for.
- The field owns `textFieldFocusRequester` alone. Attaching it to the wrapping Row as well made
  the request land on the group, whose first focusable child is the microphone, so Search opened
  with the mic armed instead of a caret.
- On a read-only field, direction keys are navigation: `onPreviewKeyEvent` moves focus itself
  while `!isSearchActive`. Left alone, the `BasicTextField` consumes the arrows to move a caret
  nobody can see, and D-pad down from the field never reaches the results or the shelf.
- The field row is **one composable**, `SearchFieldRow` in `ui/search/SearchFieldRow.kt`: the mic,
  the capsule, the filter disc, the read-only direction keys and the focus request all live
  there. The Seerr Discover search had its own stale copy of that row and had already drifted:
  it opened with the microphone armed (the request was attached to the focus group, whose first
  child is the mic) and its Back handler moved focus instead of leaving the page. A change to
  search input belongs in the shared row, not in either page.
- The row never intercepts Back at all. The on-screen keyboard is its own window and takes the
  first Back to close itself, so an app-level handler only ever sees the presses *after* the
  keyboard is gone — it cannot close what is already closed, and it swallows them: typing, then
  Back, then Back left Search needing a **third** press. `WindowInsets.isImeVisible` is not a way
  out either: the TV keyboard floats and does not resize the app, so its insets stay zero. The
  residual cost is that after the keyboard is dismissed with Back the field still thinks it is
  editing, so a direction press moves an invisible caret until Enter re-opens the keyboard or
  Back leaves the page; that is cheaper than a page that ignores two Backs.
- The trending shelf scrolls the row's heading and the required TMDB credit out of view when
  focus lands on a poster, because the scroll viewport under the field (about 282dp) is shorter
  than heading + credit + poster + caption. The credit is on the page before browsing and in the
  poster dialog; do not fix this by shrinking the posters into stamps.

## Discovery (TMDB)

- The TMDB key is a preference (`InterfacePreferences.tmdb_api_key`, proto field 18) and never a
  build constant or a resource: a build must not carry anyone's credentials. `TmdbService` reads
  it per call, accepts either a v3 key or a v4 bearer token, and treats a blank key as "no
  discovery" rather than an error.
- Never log a TMDB request, URL or exception message. A v3 key travels in the query string, so a
  logged URL or `Exception.message` is the key itself; only the failure class is logged.
- An empty Search page is the discovery surface (`TmdbTrendingSection`): with nothing typed there
  is nothing to search, and a shelf of what is trending beats a blank canvas. It is gated on
  `query.isBlank()` at the page level, not on `SearchResult.NoQuery`, because that branch is only
  reached in combined-results mode.
- Editing the key takes effect without leaving Search: `TmdbTrendingViewModel` follows
  `TmdbService.keyFlow` instead of reading the key once in `init`. Search stays on the back stack
  while Settings is open, so an init-time read would keep reporting "no key" after the viewer
  just added one.
- Without a key the section draws nothing at all, so the page stays exactly as it was. A failed
  request says so in one muted line instead, because a shelf that silently disappears reads as
  "the setting did nothing". `TmdbResult` keeps a rejected key (401/403) separate from an
  unreachable service: "TMDB rejected this key" and "Could not reach TMDB" send the viewer to
  different places, and a malformed body counts as unreachable rather than as a bad key.
- While the home rail has focus the hero follows it: `HomePageContent` swaps in the seed
  `BaseItem`, so the heading, the backdrop and a working Resume action all describe the show the
  rail is recommending from. Without that the hero keeps the last library row's item and the top
  of the screen contradicts the shelf under it. The seed must stay a real resume `BaseItem` for
  this reason, not just a title string.
- TMDB titles are not in the library, so there is no detail screen to open: tapping a poster opens
  a dialog with the overview. That dialog needs `DialogProperties(usePlatformDefaultWidth = false)`
  (the platform default squeezes the text column to half the card) and must not contain a
  `weight` child, which would make the column eat the window and the pane cover the screen.
- The attribution line is required by TMDB's terms and is shown on every shelf and in the dialog.
  On a shelf it sits **under the heading**, not after the posters: as a footer it is the last
  14dp of the row, so a home page that scrolls the next row into view leaves that fragment
  clipped and floating under the hero with nothing to attach to. `ItemRow` takes a `subtitle`
  slot for exactly this.
- Home has a second discovery surface: `TmdbHomeShelf`, headed "Because you're watching …", seeded
  from the most recently played unfinished title and filled from TMDB `/recommendations`. When
  the resume row is empty (the viewer finished everything), the top of the Next Up row seeds it
  instead — the order is pinned by `TmdbHomeSeedTest`. It
  follows the continue-watching row, so it reorders itself as the viewer moves between shows; with
  no watch row it goes after the last row rather than jumping to the top of a home page the
  viewer arranged deliberately. `HomePage` re-seeds it on every `LifecycleStartEffect`, which is
  what makes it follow the show that was just watched.
- Do not trust the resume endpoint's order: `/UserItems/Resume` is not sorted by last played (the
  Continue Watching row sorts its own combined list). The rail asks for a page and takes the item
  with the newest `userData.lastPlayedDate`, otherwise the heading can name a show that is merely
  alphabetically early.
- The seed is the *series*, not the episode: resume rows return episodes, and an episode's TMDB
  id would seed a list of other episodes. The service re-fetches the series/movie with
  `ItemFields.PROVIDER_IDS`, and falls back to a title/year search when the library was not
  scraped by Jellyfin. On a cold start the signed-in user can arrive after the home page composes,
  so the rail waits briefly for `currentUserFlow` instead of bailing out.
- The home rail is silent on every failure (no key, nothing in progress, no id, TMDB down). Search
  is the surface that reports TMDB health; an error line between two library rows just looks like
  a broken row.
- Discovery must never dead-end: the poster dialog carries a "Search your library" action that
  navigates to `Destination.Search(title)`, and a non-combined search where every type came back
  empty now says "Nothing in your library matches …" instead of rendering a blank page. TMDB
  recommendations are mostly *not* owned, so that empty state is the common result of the bridge,
  not an edge case.
- `ui/components/TmdbCard.kt` owns the poster, dialog, attribution and `TmdbPosterShelf` shared
  by both surfaces. Do not fork a second TMDB card — or a second titled shelf — for a new
  surface; the shelves have to be the same object.
- Discovery participates in the focus model. A TMDB title is not in the library, so it has no
  item id and cannot go through `BackdropService.submit(BaseItem)`; the shelf reports the focused
  poster through `onFocusItem` and both TMDB ViewModels submit `TmdbService.imageUrl(backdropPath,
  "w1280")` under a `tmdb_<id>` key. Without that, the whole Search page kept refracting the
  previous screen's artwork while focus moved across posters. The empty page also seeds the first
  trending backdrop as soon as the shelf composes — that is the only artwork the surface has
  before anything is focused. Library results follow the same rule through
  `SearchViewModel.updateBackdrop`, which is why `SearchCombinedResults` takes both focus
  callbacks.
- The library is asked first, and `TmdbSearchSection` ("Also on TMDB") answers a query the
  library owns nothing for, so a search that came back empty still ends somewhere the viewer can
  act. It is drawn only when every searchable type finished empty, it is debounced by hand
  (`collectLatest` + `delay`) because a TV keyboard types one character at a time, and it stays
  silent without a key so the page keeps exactly the empty state it had before.
- That shelf is the only focusable thing on an otherwise empty results page, so the search
  action (`searchClicked` in `SearchPage`) hands it focus through `firstFocusRequester`.
  Without the handoff the request targets result row 0, which drew nothing, and focus ends up
  nowhere while the posters sit on screen unreachable.
- The empty results page is **one list item**, message and shelf together, and its shelf is one
  poster row (`singleRow`). Compose scrolls a focused card into view by aligning the card, not
  its heading, so when the sentence and the shelf were separate items the handoff scrolled the
  heading and the TMDB credit off the top of the page — a clipped attribution, and the page's
  only copy — and two poster rows never fit under the search field in the first place. One
  item, one row, and the whole section is visible without scrolling.
- The same alignment rule sets the home page's `spaceAbovePx` (70dp, was 50dp): the discovery
  rail's heading block is taller than a normal row title because the credit sits under it, so a
  card-aligned scroll sliced the heading in half every time focus reached the posters. That
  number is what keeps a row's heading — and the credit — inside the viewport when the row is
  scrolled to the top.
- The Seerr Discover search is the same results composable with its own ViewModel, so it needed
  the same backdrop rule: `DiscoverSearchViewModel.updateBackdrop` submits the focused
  `DiscoverItem` through `BackdropService.submit(DiscoverItem)`. The page shares the combined
  results component, which is why it only had to pass `onFocusDiscoverItem`. A discovery surface
  without that call leaves the shell on the previous screen's artwork.
- `TmdbService` must be injected with `StandardOkHttpClient`, never `AuthOkHttpClient`: the
  auth client adds the Jellyfin `Authorization` header to every request, which would leak the
  user's Jellyfin token to `api.themoviedb.org` and can break TMDB calls.
- Discovery titles can be saved to **My List** (`TmdbWatchlistService`, per user through
  `KeyValueService` key `tmdbWatchlist`). It is deliberately TMDB-only: anything the library
  already owns belongs in Continue Watching or Next Up, where it is playable. The stored
  `TmdbItem` keeps its own poster and captions, so the row draws with no network and a title that
  leaves the trending page still reads correctly. Identity is the TMDB id **and** media type — a
  film and a show can share an id — and the list is capped at 24, dropping the oldest save.
  Writes go through the application's `IoCoroutineScope` under a mutex, and the toggle reads the
  stored value inside the lock, so closing the dialog cannot cancel a save and two fast presses
  cannot overwrite each other with a stale snapshot.
- Home draws My List as a shelf **above** "Because you're watching": saved titles are the
  viewer's own choice and outrank a suggestion. Each shelf is its own `LazyColumn` item
  (`HomePageContent(shelves = ...)`), not two rows stacked in one item; a block taller than the
  viewport cannot be scrolled fully into view, which sliced the lower row at the screen edge.
- Home shelves draw posters with **no captions** (`TmdbPosterCard(showCaption = false)`): the
  fixed glass hero leaves about 235dp of list viewport on a 540dp television, and a poster plus
  two caption lines is taller than that, so the focused card ran off the bottom edge. Home rows
  are posters everywhere else too; captions belong to the grid and the Search shelf, where there
  is vertical room.
- Removing the last saved title must not dismiss its dialog or drop its focus. `WatchlistShelf`
  keeps the row inside an always-composed `Column`, so the dialog's position in the composition
  does not change when the list empties; branching the whole shelf out recreated the dialog and
  left the remote with nothing focused.

## Tonight

- The page is "what can I actually finish tonight?" — the second half of the question every
  other surface answers from taste alone. A great 2h40m film is a bad answer at 10:40 PM, and
  the app already knows the runtimes, resume positions and the viewer's bedtime. Everything on
  the page is library material: no key, no external service, best-effort per source.
- `TonightPlanner` is the arithmetic and is pure (unit tested in `TonightPlannerTest`): the
  window (clamped to 20–360 minutes so a television left on at noon does not plan a twelve-hour
  evening), a 90-minute late-night window past bedtime, a 60-minute small-hours window after
  5 AM's cut-off, and +45 minutes on Friday/Saturday. The small-hours window only applies when
  the bedtime is **not** just ahead: a viewer at 00:15 with a 00:30 bedtime has fifteen minutes,
  and the old rule answered "Late night · 1h 30m" then recommended a film that ran past the bed
  they had just set. Ranking is fit first, then taste, quality
  and a gentle preference for continuing something already started; the only randomness is a
  tie-breaker seeded by the day of the year, so a plan is stable through an evening without
  being identical every night.
- `TonightService` fetches (resume, next up, unwatched films, rewatchable films, taste profile,
  plus one `getSimilarItems` lookup for the seed) and owns the dismissal store. Every
  enrichment call is `quiet`: one failed similar lookup costs one shelf, not the evening. Two
  failed film queries are the only load failure, because an empty library is a valid answer.
- **Not tonight** is stored per user and per date (`tonightDismissed` through `KeyValueService`)
  and the write runs on the IoCoroutineScope: refusing a title then leaving the page must not
  cancel the write. `buildPlan` is pure, so a refusal rerolls instantly from the held
  `TonightData` without another round trip; a refused title is removed from the shelves too,
  since reappearing one row lower reads as a button that did nothing. The pick is excluded the
  same way — the card is the top of the page, and a "Done before bed" row opening with the very
  poster above it reads as a row that forgot what the page recommends. `buildPlan` also re-applies
  the four-item shelf minimum after those removals, so a row cannot be whittled down into a
  sparse three.
- The pick card is a `GlassPanel(scrim = 1f)` at 76% width with title logo, reason, one-line
  details, the deadline rule (a bar from now to bedtime, "Ends at 4:04 PM · Plenty of time" or
  the amber overrun) and Resume/Details/Not tonight. All three actions are `Button` pills with
  `glassProminent` on Resume and `glassFilled` on the other two; Not tonight used to pass only
  `glass = true` (the clear variant), which has no tint to read against artwork and left it looking
  like stranded text beside two capsules — a disabled control rather than the second way out of
  the card. It carries `heroTint` because the logo is
  drawn in its own colours. The finish time comes from the rule, not `QuickDetails` — passing
  `endsAt = null` was not enough, because the one-line form derives an end time from
  `timeRemaining` as well. The card passes `showEndsAt = false`, otherwise two "Ends at" lines
  a minute apart appear. The details line keeps the "7m left" chip, which is new information.
- "One episode left" is the finish-line shelf: series whose `unplayedItemCount` is exactly 1,
  drawn above hidden gems. A viewer with twenty minutes wants the shortest path to done, and
  that is almost always the last episode of something already in progress. One `getItems` call
  for the next-up series' watch counts, filtered in `TonightPlanner.finishLine` (pure, tested),
  rather than a lookup per episode; a failure costs one shelf.
- Shelves: fits tonight, hidden gems (rating ≥ 7.5), because-you-watched (the seed's similar
  titles), a different lane (rating ≥ 7.0 outside every top genre — a real departure, not a
  different mix) and comfort rewatch (played films ≥ 6.5, least recently played first). A shelf
  needs 4 items to be drawn; fewer than that is a row that looks broken.
- `TonightPage`'s heading lives outside the `LazyColumn`: Compose scrolls a focused control into
  view rather than scrolling to its item, so a heading inside the list was left above the
  viewport the first time focus landed on the Resume pill. Focus lands on Resume after two
  frames and the list is reset to item 0.
- Bedtime is `InterfacePreferences.bedtime_minutes` (proto 21) with `AppPreference.Bedtime`. A
  stored zero means "never set", not midnight: the preference was added after the first stores
  existed, and 00:00 would be the most extreme option by accident.

### Sleep timer

- The player's clock key opens the sleep timer; `SleepTimerPlan` holds the pure parts (the
  offered lengths, the next-bedtime arithmetic including the midnight wrap, the fade ramp and
  the rounded-up countdown) and is unit tested. A timer counts **wall-clock** time: pausing to
  answer the door must not hand the evening back, because the feature exists for the viewer who
  fell asleep.
- The countdown lives in `PlaybackViewModel`, not the overlay: the controls hide themselves and
  the overlay leaves the composition for the chapter and queue decks, so a timer owned by the
  UI would stop counting exactly when it is needed. The last 20 seconds ramp `player.volume` to
  silence, then the player stops and the page pops back to where the viewer came from — not a
  paused frame: a still image keeps the panel lit at bedtime, and it would sit under the next-up
  card waiting to auto-play at 3 AM. The volume is restored first and one toast says so. Volume
  writes are `runCatching`-guarded so a backend that cannot fade cannot take the session down.
- A running timer suppresses next-up auto-play: the card waits for the viewer instead of
  starting an episode the timer would cut in half. Doing the fade and the stop from the ViewModel
  is also why a timer outlives the controls hiding, the chapter and queue decks, and a stream
  switch.
- The picker is a `BottomDialog` (the same smoked sheet as captions and audio) with a header and
  an "At bedtime · Nm" row when the configured bedtime is 2–360 minutes away — the bridge from
  Tonight: the page plans an evening that finishes before bed, and the timer makes the
  television keep that promise when the viewer starts something with eleven minutes to spare.
  The row shows the minutes, because the countdown is the half of a bedtime a viewer can act on.
- `SeekTimecodes` draws "Sleep in Nm" between the two timecodes rather than beside the
  transport: the row has the width, and a fourth right-hand button would have pushed the seek
  bar's own labels out of the deck. The clock glyph is lit (white) while a timer runs.

## Live TV

- The landing page is four tabs in this order: **Guide, Channels, Sports**, then DVR Schedule and
  the recording folders. Channels and Sports are not decoration: a tuner can be added to Jellyfin
  hours before its guide is fetched, so the page's default view used to be 48 hours of “No data”
  per channel with nothing pressable in it.
- The **guide hides channels with no listings of their own** (`guideChannels` in `TvGuideGrid`),
  falling back to the full list when nothing has listings at all. A sports tuner has programmes
  for the games and none for anything else: drawing all 34 channels turned twelve games into thirty
  rows and buried the schedule. The full list is one tab away in Channels.
- The programme header collapses when the focused cell is filler or empty. It used to reserve 30%
  of the page to headline the words “No data”, which on a sparse guide was most of the screen.
  `TvGuideHeader` also renders an **empty** title for a null programme: `program?.id.toString()`
  yields the literal string `"null"`, and the header is drawn while focus moves across gaps.
- The tab strip does **not** hide when the guide scrolls. It did, which left a viewer deep in a
  schedule with no way to reach Channels or Sports except Back — the two views a sports tuner
  exists for.
- Channel ordering and paging still run over the tuner's whole list. The grid draws the filtered
  subset, so its row index is translated back to the original channel before `onFocusChannel`, or
  deep scrolling stops fetching listings.

## Sports

- **There is one sports surface, and it is inside Live TV.** The standalone NFL page is gone:
  its schedule, its week stepper, its scoreboard and its per-game link editor were a second
  answer to a question the tuner already answers, and a bar slot spent on a page that duplicated
  Live TV. Sports is the fourth Live TV tab, reading the programmes the page has already loaded
  (`ProgramCategory.SPORTS`, i.e. Jellyfin's `IsSports` or an XMLTV `<category>sports</category>`).
  `InterfacePreferences.enable_sports` (proto 19) is **reserved**; nothing reads it.
- The tab opens with a heading and a count ("1 live · 20 coming up"), then a **feature card** — the
  live game, or the next kickoff when nothing is on — then one shelf per day (*Today*,
  *Tomorrow*, then the date). A week of fixtures is a calendar, and the day is what a viewer
  scans for; a single 24-item "Next up" shelf made them read every title to find Sunday.
- The feature card is also the live game's only appearance: the *Also live* shelf underneath
  lists the other games, so the page does not show the same fixture twice. Each card carries the
  live badge, `quickDetails` (start–end, duration, time left) and the channel name — but only
  when it differs from the title, because a sports tuner names the channel after the fixture.
- Every card opens `Destination.Playback(channelId)`, the same path a channel takes from the
  guide: one player, one resume story, one transcoding path. A pre-placed fixture whose stream
  does not exist yet is caught in the player (404 in `PlaybackViewModel.init`, HTTP 500 from the
  proxy's HLS segment in `onPlayerError`), which toasts "This channel is not streaming yet" and
  returns to the page instead of stranding the viewer on the error screen.
- A tuner can exist for hours before its guide does. Then the tab falls back to the channel grid
  and names the missing piece; its copy distinguishes "no guide data yet" (refresh the guide) from
  "no games in the guide" (this tuner does not carry sport). Sending the second viewer to refresh
  is advice that cannot work.
- `SportsColors.kt` holds the live red so the badge, the rule and the score block cannot drift
  into three different reds. `LiveRed` is tuned for fills; small red sentences need
  `LiveRedText`.

## Live TV details worth keeping

- **The guide draws gaps as gaps.** A tuner publishes one listing per fixture, so most of a
  48-hour grid is filler; the cells keep their slot (the grid is a calendar) but render as quiet
  space with no "No data" type and no white flash on focus. Twelve games now read as twelve
  games instead of thirty rows of the same words.
- Focus opens on the **first real programme**, not on slot 0, which on a sports tuner is usually
  a gap before kickoff.
- The programme header is a fixed 132dp slot — tall enough for a wrapped title, the time line and
  two lines of description — and the view-options disc lives in that slot beside the channel art
  rather than in a row of its own, which cost the schedule 44dp. The programme image is cropped
  into a 208×117 window: at its natural size it drew a stray thumbnail floating at the far right.
- **Channels** (`ui/detail/livetv/TvChannelBrowser.kt`) is the tuner itself: every channel as a
  16:9 card with its own art and a bottom band, playable immediately, with the count above it.
  The band carries the programme airing right now when it differs from the channel name, so the
  list doubles as a now/next board without a second request. Two lines for the name: a sports
  tuner's channel name *is* the fixture, and one line sliced it to "Washington Comm…".

## Watch together (SyncPlay)

- **Watch together is the app's own room, not a second player.** `WatchPartyService` (singleton)
  owns the room: it opens/joins/leaves through `SyncPlayApi`, subscribes to the socket's
  `SyncPlayGroupUpdateCommandMessage` and `SyncPlayCommandMessage`, and exposes `party`, `rooms`,
  a `joinTarget` and a stream of player actions. It never navigates — only the page that knows
  where the viewer is can open a title, so the dialog/player consumes `joinTarget` and calls
  `consumeJoinTarget()` once. Two consumers both opening the host's title is two pages and two
  players.
- **The host publishes; guests follow.** `PlaybackViewModel.play()` reports every item it starts
  through `onLocalPlaybackStarted`, and the service sends `SetNewQueue` + `Unpause` only when this
  client is the host. The queue is one title long: a party is "watch this together", and a host
  who moves to the next episode publishes it the same way. A guest that published would fight the
  host for the queue.
- **The transport is read, not instrumented.** The follower ticks every 700 ms and compares
  `playWhenReady` and the position against the previous tick. Pause/seek reach the player from the
  D-pad, the transport, media keys and the trickplay bar; a party wired to one button would
  silently desync from every other path. An action applied from the group re-baselines the ticker
  (`partyTick = null`), which is the echo suppression — do not also add an `applyingRemote` flag in
  the UI, or a viewer press will be dropped twice.
- **Tolerances are deliberately different.** A pause/unpause corrects position only beyond 4 s
  (`PARTY_POSITION_TOLERANCE_MS`) — landing on the exact frame is worse than starting naturally
  when both clients are already close. A seek is corrected beyond 1.5 s
  (`WatchPartyProtocol.DRIFT_TOLERANCE_MS`). A drift of more than 2 s against the playback clock
  (`PARTY_SEEK_TOLERANCE_MS`) counts as the viewer seeking and is broadcast; buffering is skipped,
  because a stalled buffer would otherwise look like a seek. These are pinned in
  `WatchPartyProtocolTest`.
- **A guest opening the host's title starts paused when the room is paused.** `play()` checks the
  party after `player.play()` and pauses immediately; playing first would broadcast an unpause and
  resume everyone. Joining a room that is `WAITING` is treated the same as paused.
- **Leaving a player leaves the room, after a grace period.** `release()` calls
  `leaveIfSessionUnchanged()`; the service waits 2.5 s and leaves only if no new playback page
  opened (`notePlaybackSessionStart()` bumps the generation). Without the grace, a guest joining
  from inside a player would leave the room it just joined as the old page disposed. A host's
  leave sends `syncPlayStop` first, so the other televisions are not stranded on a title nobody can
  restart; a guest's leave does not stop anyone.
- **A party is a session, not a preference.** Nothing is stored: no proto field, no `KeyValueService`
  key, and continuity never brings one back. State clears on profile change, when the group ends,
  and when the server denies the room.
- **The dialog is the only place a room is opened, joined or left**
  (`ui/watchparty/WatchPartyDialog.kt`, `BasicDialog` + `glassDialogSurface` fallback — a dialog
  window cannot sample the shell's backdrop). Entry points: the "Watch together" pill in
  `ExpandablePlayButtons` (movie and episode pages only; the series footer does not show it) and
  the deck capsule. The capsule replaces the "Ends at" readout while a room exists rather than
  sitting beside it: the header row has one spare slot, and "who is in this with me" outranks the
  end time while the room controls the transport. It is also the way back to the dialog without
  leaving the player.
- **Failures are honest and quiet.** No SyncPlay on the server, a denied permission, or a socket
  that never confirms the room all surface as one line in the dialog (`This server does not
  support watch parties` / `This profile is not allowed…`) — never a spinning dialog and never a
  dead Play button. Only the failure class is logged, never a request URL.
- **The room outlives the player only within the app process.** Closing the player leaves the
  room after the grace period, and a cold start begins with no room at all. That is deliberate: a
  room from yesterday is a surprise rather than a resume, and the server evicts the session
  anyway when the app goes away.

## Fork releases and updates

- This fork publishes its own releases from `FrenchToast-i/BetterWholphin`, not upstream. The
  default update URL (`AppPreference.UpdateUrl`), `UpdateChecker.getRelease`, and
  `.github/workflows/fork-release.yml` all name that repository: a build that offered an upstream
  release would hand the viewer an APK signed with somebody else's key, and the install would be
  refused.
- One tag, one release. `v1.2.0` builds `assembleDefaultRelease`, renames the APKs to
  `Wholphin.apk` / `Wholphin-<abi>.apk`, and publishes them as the latest release. The updater
  compares the **release title** with `versionName`, so the title stays the bare tag.
- The signing key is not in the repository. It lives at `~/.config/betterwholphin/release.jks`
  locally and in the repository secrets `SIGNING_KEY` (base64), `KEY_ALIAS`, `KEY_PASSWORD` and
  `KEY_STORE_PASSWORD`. The first release-signed install must be a clean uninstall of any debug
  build: Android will not overlay one signature with another.
- Updates stay **manual**, as in upstream: Settings → About → check for updates, then install.
  Nothing polls in the background and installs by itself.
- `.github/workflows/release.yml` was removed from the fork so a tag does not try to publish twice;
  `fork-release.yml` is the only release workflow.

## Upstream merges

- Upstream's Jellyfin 12 SDK bump (`41657241`) is deliberately **not** taken: it drops Jellyfin
  10.10/10.11 support and renames API surface across the whole app. Revisit only when the
  household server moves to Jellyfin 12.
- Taken from upstream so far: the HDHomeRun Live TV direct-play fix (`liveStreamId` in the direct
  play URL), the `ApiRequestPager` serializer fix for artist requests, the Seerr add-server
  endless-loading fix, and the 2026-11 translations. Dependency-only bumps (AGP, nav3, DataStore)
  are skipped while the fork is still on SDK 1.7.1.
