#!/usr/bin/env python3
"""
PocketQuest asset normalization.

Additive, not a from-scratch rebuild: most raw source packs get deleted from
the repo once their output is normalized (established practice — the raw
packs are large third-party downloads, not project source), so this script
can never assume the full original source tree is present. It loads the
existing OUT/assets.json (if any), only ADDS entries for ids it doesn't
already know about, and never deletes/overwrites prior output — running it
again after a raw pack has been deleted and cleaned up must be a no-op for
that pack, not data loss for everything normalized before it.

docs/23-sprite-rendering.md: OUT points directly at :ui's own composeResources
tree — the one location actually bundled cross-platform — not a repo-root
duplicate a human has to remember to copy afterward. SRC (raw, pre-normalize
source packs) stays at the repo-root `assets/` folder; only the *output* of
normalizing moved.

Run from the repo root: `python3 tools/normalize_assets.py`.
"""
import json
import os
import re
from PIL import Image

SRC = "assets"
OUT = "ui/src/commonMain/composeResources/files/normalized"
TILE = 64
SRC_TILE_DUNGEON = 70  # the original "Free Flat Greyscale Dungeon Assets" pack's tile size
SRC_TILE_DECOR = 300  # "Classic Dungeon Map Symbols - High Res"'s tile size


def ensure(path):
    os.makedirs(path, exist_ok=True)


def load_manifest():
    path = os.path.join(OUT, "assets.json")
    if os.path.exists(path):
        with open(path) as f:
            return json.load(f)
    return {"tile": TILE, "characters": [], "props": [], "overlays": []}


def existing_ids(manifest, key):
    return {entry["id"] for entry in manifest[key]}


FOOTPRINT_RE = re.compile(r"[-_]?(\d+)x(\d+)")
MAX_FOOTPRINT = 8  # nothing in these packs is legitimately bigger than 8 tiles


def footprint_for(name, w, h, src_tile):
    """Tiles wide/high. Filename wins, but only if it's plausibly a footprint —
    Compass...-1400x1400.png encodes PIXELS, not tiles, and would otherwise ask
    for an 89600px resize."""
    m = FOOTPRINT_RE.search(name)
    if m:
        tw, th = int(m.group(1)), int(m.group(2))
        if 1 <= tw <= MAX_FOOTPRINT and 1 <= th <= MAX_FOOTPRINT:
            return tw, th
    return max(1, round(w / src_tile)), max(1, round(h / src_tile))


# not grid props — decorative or shader-ish, passed through at native size
NON_TILE = {"fire.png", "effectsgradientlinear.png", "effectsgradientradial.png",
            "effectsgradientradial2.png"}


def norm_prop_folder(manifest, src_dir, src_tile, label):
    """Shared by norm_props() (the original pack) and norm_decorations() (the
    new one) — same footprint-from-filename + LANCZOS-resize shape, just a
    different source folder/tile size. Skips any id already in the manifest,
    so re-running after the raw folder is deleted is a safe no-op."""
    if not os.path.isdir(src_dir):
        print(f"  {label}: no source folder at {src_dir!r}, skipping")
        return
    outdir = os.path.join(OUT, "props")
    ensure(outdir)
    known = existing_ids(manifest, "props")
    added = 0
    for fn in sorted(os.listdir(src_dir)):
        if not fn.lower().endswith(".png"):
            continue
        prop_id = fn[:-4]
        if prop_id in known:
            continue
        im = Image.open(os.path.join(src_dir, fn)).convert("RGBA")
        w, h = im.size

        if fn in NON_TILE:
            im.save(os.path.join(outdir, fn))
            manifest["props"].append({"id": prop_id, "file": f"props/{fn}",
                                      "tilesW": None, "tilesH": None,
                                      "kind": "decal"})
            added += 1
            continue

        tw, th = footprint_for(fn, w, h, src_tile)
        target = (tw * TILE, th * TILE)
        out = im.resize(target, Image.LANCZOS)
        out.save(os.path.join(outdir, fn))

        kind = "compass" if fn.lower().startswith("compass") else \
               "number" if fn.lower().startswith("number") else \
               "floor" if "tile" in fn.lower() else "prop"
        manifest["props"].append({
            "id": prop_id, "file": f"props/{fn}",
            "tilesW": tw, "tilesH": th, "kind": kind,
        })
        added += 1
    print(f"  {label}: {added} new prop(s)")


def norm_props(manifest):
    norm_prop_folder(manifest, os.path.join(SRC, "Free Flat Greyscale Dungeon Assets(1)"), SRC_TILE_DUNGEON, "props")


def norm_decorations(manifest):
    norm_prop_folder(manifest, os.path.join(SRC, "Classic Dungeon Map Symbols - High Res"), SRC_TILE_DECOR, "decorations")


if __name__ == "__main__":
    ensure(OUT)
    manifest = load_manifest()
    print("normalizing (additive) into", OUT)
    norm_props(manifest)
    norm_decorations(manifest)
    with open(os.path.join(OUT, "assets.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print("wrote", os.path.join(OUT, "assets.json"))
