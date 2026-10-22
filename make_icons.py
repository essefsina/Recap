#!/usr/bin/env python3
"""Build Recap's launcher-icon set from Essef's final logo master.

Master (recap-logo-final.png): white faceted handset + white sparkle with a red
gem, on deep violet (#210457), black outside the rounded-square corners.

For every theme we key out the violet background (alpha from color distance,
near-black forced transparent) and re-composite the artwork over that theme's
deep shade. Outputs:
  - adaptive-icon foreground (transparent artwork)  drawable-nodpi/logo_fg.png
  - per-theme adaptive XML                          mipmap-anydpi-v26/ic_launcher_<t>.xml
  - per-theme legacy full-bleed PNGs                mipmap-<dpi>/ic_launcher_<t>.png
  - per-theme in-app logo (rounded, transparent)    drawable-nodpi/logo_<t>.png
  - preview sheet                                   ~/workspace/recap-logo/icon-variants-preview.png
"""
from PIL import Image, ImageDraw
import numpy as np, os

ROOT = os.path.expanduser("~/workspace/recap-android/app/src/main/res")
MASTER = os.path.expanduser("~/workspace/recap-logo/recap-logo-final.png")
BG = np.array([33.0, 4.0, 87.0])  # master background

THEMES = {  # deep shades matching the master's mood
    "forest": (6, 64, 46),
    "ocean": (6, 59, 102),
    "violet": (33, 4, 87),
    "sunset": (110, 58, 8),
    "rose": (94, 31, 49),
}

im = Image.open(MASTER).convert("RGB")
arr = np.asarray(im).astype(np.float32)
dist = np.sqrt(((arr - BG) ** 2).sum(axis=2))
alpha = np.clip((dist - 6.0) / 42.0, 0.0, 1.0)
alpha[arr.max(axis=2) < 45] = 0.0  # black corners are never artwork
W = im.size[0]

def artwork_rgba():
    out = np.dstack([arr, alpha * 255.0]).astype(np.uint8)
    return Image.fromarray(out, "RGBA")

def artwork_scaled(frac):
    """Artwork inset on a transparent canvas. The master artwork nearly
    touches the frame edges; adaptive-icon masks crop ~17% per side, which
    clipped the handset (Essef, Oct 22). Insetting keeps it whole."""
    a = artwork_rgba().resize((int(W * frac), int(W * frac)), Image.LANCZOS)
    canvas = Image.new("RGBA", (W, W), (0, 0, 0, 0))
    off = (W - a.size[0]) // 2
    canvas.alpha_composite(a, (off, off))
    return canvas

def over(bg_rgb, size=None):
    base = Image.new("RGBA", (W, W), bg_rgb + (255,))
    out = Image.alpha_composite(base, artwork_scaled(0.88))
    return out.resize((size, size), Image.LANCZOS) if size else out

art = artwork_rgba()

# --- adaptive foreground: artwork on transparency, 432px ---
os.makedirs(f"{ROOT}/drawable-nodpi", exist_ok=True)
artwork_scaled(0.74).resize((432, 432), Image.LANCZOS).save(f"{ROOT}/drawable-nodpi/logo_fg.png")

# --- per-theme adaptive XML + legacy PNGs + in-app logo ---
DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
os.makedirs(f"{ROOT}/mipmap-anydpi-v26", exist_ok=True)
for name, rgb in THEMES.items():
    hexcol = "#FF%02X%02X%02X" % rgb
    with open(f"{ROOT}/mipmap-anydpi-v26/ic_launcher_{name}.xml", "w") as f:
        f.write('<?xml version="1.0" encoding="utf-8"?>\n'
                '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                f'    <background android:drawable="@color/iconbg_{name}"/>\n'
                '    <foreground android:drawable="@drawable/logo_fg"/>\n'
                '</adaptive-icon>\n')
    for dpi, px in DENS.items():
        d = f"{ROOT}/mipmap-{dpi}"
        os.makedirs(d, exist_ok=True)
        over(rgb, px).convert("RGB").save(f"{d}/ic_launcher_{name}.png")
    # in-app logo: rounded square on transparency, 512px
    full = over(rgb, 512).convert("RGBA")
    mask = Image.new("L", (512, 512), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, 512, 512], radius=114, fill=255)
    full.putalpha(mask)
    full.save(f"{ROOT}/drawable-nodpi/logo_{name}.png")

# --- colors.xml for the adaptive backgrounds ---
vals = os.path.join(ROOT, "values", "icon_colors.xml")
with open(vals, "w") as f:
    f.write('<?xml version="1.0" encoding="utf-8"?>\n<resources>\n')
    for name, rgb in THEMES.items():
        f.write('    <color name="iconbg_%s">#FF%02X%02X%02X</color>\n' % (name, *rgb))
    f.write('</resources>\n')

# --- preview sheet: five variants side by side ---
sheet = Image.new("RGB", (5 * 320 + 6 * 16, 320 + 32), (24, 24, 28))
for i, (name, rgb) in enumerate(THEMES.items()):
    tile = over(rgb, 320).convert("RGB")
    sheet.paste(tile, (16 + i * (320 + 16), 16))
sheet.save(os.path.expanduser("~/workspace/recap-logo/icon-variants-preview.png"))
print("icons written for:", ", ".join(THEMES))
