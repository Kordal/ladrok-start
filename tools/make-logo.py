#!/usr/bin/env python3
"""Make the Launcher's background logo from the Ladrok logo: silver line art on a dark background,
the same source file as ladrok-tv's docs/ladrok-logo-source.png.

  python3 tools/make-logo.py docs/ladrok-logo-source.png app/src/main/res/drawable-nodpi/ladrok_logo.png

Writes white line art on transparent, cropped to the logo. Brightness becomes coverage (as in
ladrok-tv's tools/make-logo-assets.py), so there is no box around it; the Launcher tints and dims it.
Needs Pillow.
"""
import sys

from PIL import Image

src, out = sys.argv[1:3]
img = Image.open(src).convert("L")


def pixels(im):
    return list(im.get_flattened_data() if hasattr(im, "get_flattened_data") else im.getdata())


# Background level: median brightness of the four corners.
w, h = img.size
corners = []
for x0, y0 in ((0, 0), (w - 64, 0), (0, h - 64), (w - 64, h - 64)):
    corners += pixels(img.crop((x0, y0, x0 + 64, y0 + 64)))
bg = sorted(corners)[len(corners) // 2]
peak = max(pixels(img))
# Stretch [bg + margin, peak] to [0, 255]; everything at or below the background is transparent.
lo = bg + 14
alpha = img.point(lambda v: 0 if v <= lo else min(255, int((v - lo) * 255 / (peak - lo))))

# Crop to clearly bright pixels, so faint noise in the background doesn't widen the box.
x0, y0, x1, y1 = alpha.point(lambda v: 255 if v > 90 else 0).getbbox()
box = (max(0, x0 - 4), max(0, y0 - 4), min(w, x1 + 4), min(h, y1 + 4))
alpha = alpha.crop(box)

logo = Image.new("RGBA", alpha.size, (255, 255, 255, 0))
logo.putalpha(alpha)
logo.save(out, optimize=True)
print("wrote", out, logo.size)
