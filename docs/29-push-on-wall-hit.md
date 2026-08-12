# 29 — Push: on-wall-hit effects

Before this pass, a `Push` that got stopped early by a wall or another
entity just fizzled silently (`Rejection.Blocked`, a log line, nothing
else) — no way to author "slam them into the wall for extra damage," a
staple of the genre this game is modeled on.

## Model

- `EffectTemplate.Push` gains `onWallHit: List<EffectTemplate> = emptyList()`
  — authored the same way `RollSave.onSuccess`/`onFail` are, and edited with
  the identical nested-editor UI in `:designer` (`EffectTemplateEditor.kt`'s
  new "ON WALL HIT" section under a Push row).
- `Effect.Push`/`Effect.MoveAlong` both gain a resolved `onWallHit: List<Effect>`
  — `Push`'s handler doesn't fire it directly; it threads it onto the
  `MoveAlong` effect it spawns (the same composition `Push` already used —
  see `Effect.Push`'s own doc comment), and `MoveAlong`'s handler is what
  actually detects a blocked tile and spawns `onWallHit` at that point,
  alongside the unchanged `Fizzled` event/log line.
- Ordinary movement (walking, the Move action) never sets `onWallHit`, so it
  defaults to empty and behaves exactly as before — only `Push` populates it.

## Ref scoping

`EffectTemplateInstantiate.kt`'s Push branch instantiates `onWallHit`
against a `ctx` scoped to just the one pushed target (`ctx.copy(targets =
listOf(targetId))`) — the same per-target scoping `RollSave.onSuccess`/
`onFail` already use, for the same reason: a multi-target Push (e.g. a cone
that shoves every enemy in it) must not have one target's `Ref.EachTarget`
inside `onWallHit` accidentally resolve to every OTHER target too. `EachTarget`
inside `onWallHit` means "the entity that just hit the wall." `Ref.Caster`
still resolves normally, so a wall-slam bonus can be sourced from the
caster (e.g. a Str-based bonus) as well as the pushed target.

## Non-goals

- No distinction between "hit a wall" and "hit another entity" —
  `Rejection.Blocked` already covers both, and the user's own ask ("a wall
  or similar") treats them the same. Splitting them into separate hooks is
  easy to add later if content actually needs it, but nothing does yet.
- No partial-push-distance info in `onWallHit`'s effects (e.g. "pushed 2 of
  3 tiles") — `Ref.EachTarget`'s position at instantiate time is enough for
  today's content (bonus damage), and the target's live `pos` is always
  readable from `state` if a future effect needs it.
