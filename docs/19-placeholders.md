# 19 — Placeholders and the missing-asset registry

Interim solution for the gaps listed in
[16-art-direction.md](16-art-direction.md#missing-assets): no enemy art, no
ability or status icons, no UI frames.

## Placeholders are generated, never shipped

The obvious approach is a folder of `placeholder_*.png` files. Don't. Shipped
placeholder files have two failure modes: they quietly survive into release
because nothing flags them, and they need manual maintenance every time content
is added.

Draw them **in code instead**, from the content id:

```kotlin
fun placeholderToken(id: EntityId, archetype: ArchetypeId, tier: Tier): DrawScope.() -> Unit
fun placeholderIcon(id: ActionId): DrawScope.() -> Unit
fun placeholderPip(id: StatusId): DrawScope.() -> Unit
```

Three properties follow, and all three matter:

- **Nothing is missing, ever.** A new enemy or spell renders on the day it is
  authored. Content work never blocks on art.
- **Stable identity.** The glyph derives deterministically from the id, so the
  same goblin always looks the same across sessions and screenshots.
- **Visibly provisional.** Placeholders carry a diagonal hatch. They are obvious
  in a screenshot, which is the point — a placeholder nobody notices is a
  placeholder that ships.

### Glyph derivation

Two initials from the content id (`goblin_warrior` → `Gb`, `fire_bolt` → `FB`),
uppercased. Collisions are acceptable and mostly harmless; the catalog validator
can warn when two ids in the same category collapse to the same glyph.

Enemy tokens follow the ink-token style from doc 16 — parchment fill, heavy ink
outline, an inner ring for elite and two for boss. Icons are rounded squares with
hatching. Status pips are small circles with a single letter.

One thing the sample render made obvious: **the elite and boss rings crowd the
label.** Either shrink the glyph at higher tiers or move the tier marker outside
the token as a corner notch. Worth fixing before this is used in anger, because
an unreadable enemy label is worse than no label.

## The registry writes the shopping list

The list of icons to commission cannot be written now — it depends on the action
and status catalogs, which are docs 12 and 13. Guessing at it produces a list
that is simultaneously incomplete and full of things we never build.

So let the code record it. Every placeholder call registers the request:

```kotlin
object MissingAssets {
    fun record(kind: AssetKind, id: String, context: String)
    fun report(): List<MissingAsset>   // kind, id, times requested, first seen
}
```

Wire it into the placeholder functions, play through the content, and export the
report. That is the commission brief: real ids, actually used, sorted by how
often they appear. It costs one afternoon and it removes the guesswork
completely.

Two rules to keep it honest:

- A debug overlay shows the live count, so it is visible how much of the screen
  is still provisional.
- A release build fails if the registry is non-empty for shipping content. This
  is the check that makes "temporary" actually temporary.

## Scope

Placeholders cover enemy tokens, ability icons, status pips, and item icons.
They do **not** cover the player's party — those use the real 32 px sprites,
which exist. Keeping the party real and everything else provisional also makes
the visual hierarchy from doc 16 legible early: the party is what is really
there, everything else is drawn on the mat.
