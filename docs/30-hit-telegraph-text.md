# 30 — Hit/miss/save telegraph text

Floating "HIT" / "MISS" / "SAVED" / "FAILED" over an entity the moment an
attack or save roll resolves against it — a miss or a saved roll previously
had no visual at all beyond the dice card and a log line.

## Model

- `TelegraphVisual` (`VisualWorld.kt`) — `pos: Animatable<Offset>` and
  `alpha: Animatable<Float>`, both driven together by `Director.kt`'s
  `showTelegraph`, plus a fixed `text`/`color`. `VisualWorld.telegraphs:
  Map<Long, TelegraphVisual>`, `addTelegraph`/`removeTelegraph` — same
  id-map shape `projectiles` already uses.
- `Director.kt`'s `showTelegraph(entityId, text, color)`: captures the
  entity's current position once (same "doesn't track further movement"
  convention `floatNumber`'s damage numbers use), then animates `pos`
  (rises `TELEGRAPH_RISE_PX`) and `alpha` (fades to 0) **concurrently**
  (`coroutineScope { launch { ... }; launch { ... } }`) over `TELEGRAPH_MS`
  — genuinely animated, not `Overlay`'s static hold-then-remove.
- Wired as a `Timing.Parallel` beat off `GameEvent.AttackRolled`
  (`"HIT"`/`"MISS"`) and `GameEvent.SaveRolled` (`"SAVED"`/`"FAILED"`) —
  Parallel so it rides alongside whatever `DamageTaken`/pulse beats follow a
  hit, rather than adding its own hold to the sequence; a miss has nothing
  else to show at all, so the telegraph is its only feedback.

## Real text, not a placeholder square

`Overlay`'s damage numbers have always been a colored square stand-in
(`drawOverlay`'s own doc comment: "no text-in-Canvas dependency pulled in
for one debug number"). This is the first real text drawn on the board:
`Board` gets a `val textMeasurer = rememberTextMeasurer()` (measured once
per composition), passed into a new `drawTelegraph` `DrawScope` extension
that calls `textMeasurer.measure(...)` + `drawText(...)`, centered on the
telegraph's live (rising/fading) position, bold for legibility. `Overlay`'s
own square is untouched — this is a separate, parallel mechanism, not a
replacement (out of scope for this pass).

## Color

Reuses the palette already established elsewhere (HP bars, the active-turn
tile ring): HIT/FAILED red (bad for the target), SAVED green (good), MISS a
neutral grey (nothing happened to them).

## Non-goals

- No "CRITICAL" telegraph — `AttackRolled.critical` exists but nothing asked
  for a fifth word; easy to add later (same shape) if wanted.
- No distinction drawn for out-of-combat event checks (`docs/22`'s
  ability-check dice card) — those aren't `AttackRolled`/`SaveRolled`
  events at all, so this pass doesn't touch them.
