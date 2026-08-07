#!/usr/bin/env python3
"""
PocketQuest asset normalization.

Brings three source packs onto one 64x64 logical tile:

  characters/   32px pixel art  -> 2x NEAREST  (exact, stays crisp)
  dungeon/      70px ink art    -> LANCZOS to n*64 (footprint from filename)
  textures/     grid overlays   -> retiled to 64

Emits normalized PNGs plus assets.json, a manifest the app loads instead of
hardcoding sheet layouts.
"""
import json
import os
import re
import shutil
import sys
from PIL import Image

SRC = "/home/claude/assets/pq_assets"
OUT = "/home/claude/assets/normalized"
TILE = 64
SRC_TILE_DUNGEON = 70
SRC_TILE_CHAR = 32

# characters: (sheet width, sheet height) in source px -> (cols, rows) of frames
CHAR_ANIMS = {
    "idle":    dict(cols=1, rows=4, fps=0),
    "walk":    dict(cols=4, rows=4, fps=8),
    "sprint":  dict(cols=4, rows=4, fps=12),
    "sleep":   dict(cols=1, rows=1, fps=0),
    "fishing": dict(cols=2, rows=4, fps=6),
}
# row order in every sheet, verified visually
FACINGS = ["South", "West", "East", "North"]

manifest = {"tile": TILE, "characters": [], "props": [], "overlays": []}


def ensure(path):
    os.makedirs(path, exist_ok=True)


def norm_characters():
    outdir = os.path.join(OUT, "characters")
    ensure(outdir)
    for group in sorted(os.listdir(os.path.join(SRC, "characters"))):
        gdir = os.path.join(SRC, "characters", group)
        if not os.path.isdir(gdir) or group.startswith("_"):
            continue
        for fn in sorted(os.listdir(gdir)):
            if not fn.endswith(".png"):
                continue
            m = re.match(r"(.+)_([a-z])_([a-z]+)\.png$", fn)
            if not m:
                print(f"  ?? skipped {fn}")
                continue
            family, variant, anim = m.groups()
            spec = CHAR_ANIMS.get(anim)
            if spec is None:
                print(f"  ?? unknown anim {anim}")
                continue

            im = Image.open(os.path.join(gdir, fn)).convert("RGBA")
            w, h = im.size
            exp_w = spec["cols"] * SRC_TILE_CHAR
            exp_h = spec["rows"] * SRC_TILE_CHAR
            if (w, h) != (exp_w, exp_h):
                print(f"  !! {fn}: expected {exp_w}x{exp_h}, got {w}x{h} — "
                      f"deriving layout from actual size")
                spec = dict(spec, cols=w // SRC_TILE_CHAR, rows=h // SRC_TILE_CHAR)

            # exact 2x integer upscale — no resampling artefacts on pixel art
            big = im.resize((w * 2, h * 2), Image.NEAREST)
            out_name = f"{family}_{variant}_{anim}.png"
            big.save(os.path.join(outdir, out_name))

            manifest["characters"].append({
                "id": f"{family}_{variant}",
                "anim": anim,
                "file": f"characters/{out_name}",
                "frameW": TILE,
                "frameH": TILE,
                "cols": spec["cols"],
                "rows": spec["rows"],
                "fps": spec["fps"],
                "facings": FACINGS[:spec["rows"]] if spec["rows"] > 1 else ["South"],
            })
    print(f"  characters: {len(manifest['characters'])} sheets")


FOOTPRINT_RE = re.compile(r"[-_]?(\d+)x(\d+)")


MAX_FOOTPRINT = 8  # nothing in this pack is legitimately bigger than 8 tiles


def footprint_for(name, w, h):
    """Tiles wide/high. Filename wins, but only if it's plausibly a footprint —
    Compass...-1400x1400.png encodes PIXELS, not tiles, and would otherwise ask
    for an 89600px resize."""
    m = FOOTPRINT_RE.search(name)
    if m:
        tw, th = int(m.group(1)), int(m.group(2))
        if 1 <= tw <= MAX_FOOTPRINT and 1 <= th <= MAX_FOOTPRINT:
            return tw, th
    return max(1, round(w / SRC_TILE_DUNGEON)), max(1, round(h / SRC_TILE_DUNGEON))


# not grid props — decorative or shader-ish, passed through at native size
NON_TILE = {"fire.png", "effectsgradientlinear.png", "effectsgradientradial.png",
            "effectsgradientradial2.png"}


def norm_props():
    outdir = os.path.join(OUT, "props")
    ensure(outdir)
    src = os.path.join(SRC, "dungeon_assets")
    for fn in sorted(os.listdir(src)):
        if not fn.endswith(".png"):
            continue
        im = Image.open(os.path.join(src, fn)).convert("RGBA")
        w, h = im.size

        if fn in NON_TILE:
            im.save(os.path.join(outdir, fn))
            manifest["props"].append({"id": fn[:-4], "file": f"props/{fn}",
                                      "tilesW": None, "tilesH": None,
                                      "kind": "decal"})
            continue

        tw, th = footprint_for(fn, w, h)
        target = (tw * TILE, th * TILE)
        # LANCZOS: ink line art tolerates non-integer downscale, pixel art would not
        out = im.resize(target, Image.LANCZOS)
        out.save(os.path.join(outdir, fn))

        kind = "compass" if fn.lower().startswith("compass") else \
               "number" if fn.lower().startswith("number") else \
               "floor" if "tile" in fn.lower() else "prop"
        manifest["props"].append({
            "id": fn[:-4], "file": f"props/{fn}",
            "tilesW": tw, "tilesH": th, "kind": kind,
        })
    print(f"  props: {len(manifest['props'])} images")


def norm_overlays():
    outdir = os.path.join(OUT, "overlays")
    ensure(outdir)
    src = os.path.join(SRC, "textures")
    for fn in sorted(os.listdir(src)):
        if not fn.lower().endswith(".png"):
            continue
        im = Image.open(os.path.join(src, fn)).convert("RGBA")
        w, h = im.size
        # grid overlays: rescale so one grid cell lands on TILE
        cells_w = max(1, round(w / SRC_TILE_DUNGEON))
        cells_h = max(1, round(h / SRC_TILE_DUNGEON))
        out = im.resize((cells_w * TILE, cells_h * TILE), Image.LANCZOS)
        out.save(os.path.join(outdir, fn))
        manifest["overlays"].append({"id": fn[:-4], "file": f"overlays/{fn}",
                                     "tilesW": cells_w, "tilesH": cells_h})
    print(f"  overlays: {len(manifest['overlays'])} images")


if __name__ == "__main__":
    if os.path.exists(OUT):
        shutil.rmtree(OUT)
    ensure(OUT)
    print("normalizing to a", TILE, "px tile")
    norm_characters()
    norm_props()
    norm_overlays()
    with open(os.path.join(OUT, "assets.json"), "w") as f:
        json.dump(manifest, f, indent=2)
    print("wrote", os.path.join(OUT, "assets.json"))
