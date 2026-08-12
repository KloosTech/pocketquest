# 28 — Sprite import from the editor

Automates the first two steps of the manual process docs/23 left as a
checklist: copy the PNG into `sprites/`, hand-edit `assets.json`. An
"Import…" button next to the Archetype editor's SPRITE picker and the Action
editor's PROJECTILE SPRITE picker does both and auto-selects the result —
no manual JSON edit, and the picker dropdowns update immediately. Docs/23's
third step ("restart to see it") still applies to Playtest specifically —
see "What still needs a restart" below; this pass didn't remove that.

## Flow

1. `chooseImageFile()` — a `JFileChooser` filtered to png/jpg (same pattern
   `DesignerFileIo` already uses for catalog files).
2. `AssetManifest.importSprite(source, kind)`:
   - id = the source filename, lowercased, non-alphanumerics replaced with
     `_`, deduped against existing ids (`_2`, `_3`, …) — `chosen_id.png` next
     to `assets.json`'s own `sprites/` folder, not the source's original
     name (which could collide or contain characters the JSON id shouldn't).
   - copies the file, appends one `props` entry (`id`, `file`, `kind` —
     neither "character" nor "projectile" carries `tilesW`/`tilesH`, matching
     every existing entry of those kinds).
   - writes the manifest back.
3. The caller (`ArchetypePanel`/`ActionPanel`) sets `spriteId`/
   `projectileSprite` to the new id immediately.

## Why raw JSON surgery, not `AssetManifestFile`'s own serializer

`assets.json` has a top-level `characters` array `AssetManifestFile` doesn't
model (confirmed unused by any Kotlin code, but still real, checked-in
content — a leftover tile-sheet scheme from before docs/23 decided sprites
arrive pre-sliced). Round-tripping through the typed model to append one
`props` entry would silently drop `characters` from disk. `importSprite`
instead parses the file as a raw `JsonObject`, only touches the `props`
array, and writes everything else back byte-for-byte unchanged in structure.

## Live update without restart — picker dropdowns only

`AssetManifest.file` moved from `by lazy` to a Compose `mutableStateOf`, and
every derived list (`characterSprites`, `projectileSprites`, etc.) moved
from `by lazy` to a plain `get()` that reads `file.props` fresh. A composable
reading e.g. `AssetManifest.characterSprites` during composition registers as
a reader of the underlying `mutableStateOf`; `importSprite`'s final
`file = load()` (re-parsing the just-written manifest) triggers
recomposition in every open picker, immediately. This is real because
`AssetManifest`/`SpriteLoader` (`:designer`, desktop-only) read straight off
disk via `java.io.File` — no build step between "file written" and "visible."

## What still needs a restart: Playtest

Playtest runs `:ui`'s real gameplay code in the same already-running
`:designer` process (`Main.kt` just swaps Compose content, no new process).
That code loads art through `GameAssetManifest`/`GameSpriteLoader`
(`ui/.../assets/`), which reads via Compose Resources' `Res.readBytes(...)`
— resources Gradle copies into a build output directory and bakes into the
running JVM's classpath at **build time**, not a live disk read. A sprite
imported into the already-running process's session isn't on that classpath
yet, so Playtest won't find it no matter how long you wait or how many times
you reopen the Playtest tab in that same session — only a full restart
(`:designer:run` again, a fresh Gradle build) re-copies the new file into
the packaged resources and makes it visible there. This isn't specific to
`importSprite`: it's true of any change to `composeResources/files/` while
`:designer` is running, import feature or not. The Import button's own
picker dropdowns don't have this problem (see above) — only rendering in an
actual live encounter (Playtest, or the real `:app`) does.

## Non-goals

- No `kind = "prop"`/`"floor"` import button (map-editor's prop/floor
  pickers need `tilesW`/`tilesH` + footprint, a different authoring flow) —
  only the two pickers this pass actually touches (Archetype/Action) got the
  button, not a fully generic "import any asset kind" tool.
- No image validation (dimensions, aspect ratio) — copies whatever file is
  chosen; docs/23's "256×256" is a content convention, not an enforced rule.
