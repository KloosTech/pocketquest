# 40 — Status icons

`StatusDef` gains `icon: String?` (a manifest sprite id, new `kind = "status"`) — authored in
`:designer`'s Status tab via the same picker shape `LootPanel.kt`'s closed/open sprite fields and
`ItemPanel.kt`'s icon field already use (`InkSelect` over `AssetManifest.statusSprites` + an
"Import…" button). No icon set means that status just doesn't appear in the row — same missing-
asset-is-never-a-crash contract every other sprite-id field already follows.

## Rendering: a row above the token, one icon per distinct status

`:ui`'s Board draws a small horizontal row of icons centered above each entity's own token —
`entity.statuses` deduplicated by `StatusId` (an entity carrying the same status from two different
sources/stacks still gets one icon, not two identical ones), each resolved through
`catalog.statusDef(id).icon`. Icons are loaded once per catalog (`loadStatusIcons`, mirrors
`loadActionIcons`'s "load every possible one up front, not just what's currently active" discipline
— simpler than reloading as statuses come and go mid-encounter) and threaded into `Board` alongside
the existing `colors`/`sprites` maps, keyed live off `state.entities` each frame (which statuses are
*currently* active does change turn to turn, unlike the icon bitmaps themselves).

Positioned purely as a function of the entity's own token center + `TILE_PX * zoom`, same "world-
space math, not fixed screen pixels" every other Board overlay (highlight rings, hit telegraphs)
already follows — it stays correctly placed under pan/zoom with no separate tracking needed.
