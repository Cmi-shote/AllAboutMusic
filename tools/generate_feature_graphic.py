#!/usr/bin/env python3
"""Generates the Play Store feature graphic (1024x500) from the app icon's design.

Reuses the gradient and the waveform mark from generate_app_icons.py so the store
listing and the launcher icon stay in sync. The mark + text lockup is measured and
centred as one group, so changing the wording or font size rebalances it.

Requires Pillow. Run: python3 tools/generate_feature_graphic.py
"""
from PIL import Image, ImageDraw, ImageFilter, ImageFont
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from generate_app_icons import ramp, radial, mark_only, DISC_R, WHITE, BLACK  # noqa: E402

W, H = 1024, 500
SS = 3                    # supersample factor for the background pass
INSET = 64                # hard limit: Play can crop into the outer edges

FONT = '/System/Library/Fonts/Avenir Next.ttc'
HEAVY, MEDIUM = 8, 5      # face indices within the collection

MARK_BOX = 400            # square holding the mark; the disc spans 0.69 of it
DISC_W = MARK_BOX * DISC_R * 2
GAP = 58                  # space between the mark and the text column

TITLE = 'AllAboutMusic'
LINE2 = 'Stream · Download · Mix'
LINE3 = 'Cue-pointed mixes · Offline playback'
SIZES = {'title': 68, 'line2': 34, 'line3': 25}
LEADING = {'title': 20, 'line2': 14}


def fit(text, index, ideal, max_w):
    """Largest point size at or below `ideal` that keeps `text` inside max_w."""
    for size in range(ideal, 8, -1):
        f = ImageFont.truetype(FONT, size, index=index)
        if f.getlength(text) <= max_w:
            return f
    return ImageFont.truetype(FONT, 8, index=index)


def layout():
    """Measure the lockup and centre it as one group. Returns (mark_cx, spans)."""
    max_text_w = W - 2 * INSET - DISC_W - GAP
    fonts = {'title': fit(TITLE, HEAVY, SIZES['title'], max_text_w),
             'line2': fit(LINE2, MEDIUM, SIZES['line2'], max_text_w),
             'line3': fit(LINE3, MEDIUM, SIZES['line3'], max_text_w)}
    lines = [('title', TITLE, 255), ('line2', LINE2, 232), ('line3', LINE3, 176)]

    text_w = max(fonts[k].getlength(t) for k, t, _ in lines)
    group_w = DISC_W + GAP + text_w
    left = (W - group_w) / 2
    mark_cx = left + DISC_W / 2
    text_x = left + DISC_W + GAP

    heights = [fonts[k].getbbox(t)[3] - fonts[k].getbbox(t)[1] for k, t, _ in lines]
    block = sum(heights) + sum(LEADING.values())
    y = (H - block) / 2

    spans = []
    for (k, t, a), h in zip(lines, heights):
        y += h
        spans.append(((round(text_x), round(y)), t, fonts[k], a))
        y += LEADING.get(k, 0)
    return mark_cx, spans


def wide_gradient(w, h):
    """The icon's diagonal gradient, stretched across a landscape canvas."""
    img = Image.new('RGB', (w, h))
    d = ImageDraw.Draw(img)
    n = 2 * w
    for i in range(n + 1):
        t = i / n
        # Lines of constant x/w + y/h, so the ramp runs corner to corner.
        d.line([(t * 2 * w, 0), (0, t * 2 * h)], fill=ramp(t), width=3)
    return img


def stretched_radial(w, h, cx, cy, r, peak):
    """radial() is square-only; build it square then stretch to the canvas."""
    side = max(w, h)
    return radial(side, cx / w * side, cy / h * side, r * side, peak).resize(
        (w, h), Image.LANCZOS)


def background(w, h, mark_cx):
    img = wide_gradient(w, h)
    img = Image.composite(Image.new('RGB', (w, h), WHITE), img,
                          stretched_radial(w, h, w * .20, h * .18, .85, 44))
    img = Image.composite(Image.new('RGB', (w, h), BLACK), img,
                          stretched_radial(w, h, w * .95, h * 1.05, .80, 62))

    # Sound radiating from the mark: concentric rings that bleed off the edges.
    rings = Image.new('L', (w, h), 0)
    rd = ImageDraw.Draw(rings)
    base = DISC_W / 2 * SS
    cx, cy = mark_cx * SS, h / 2
    for factor, alpha in ((1.38, 32), (1.80, 21), (2.28, 13)):
        r = base * factor
        rd.ellipse([cx - r, cy - r, cx + r, cy + r], outline=alpha, width=int(2.2 * SS))
    return Image.composite(Image.new('RGB', (w, h), WHITE), img, rings)


def text_mask(size, spans):
    m = Image.new('L', size, 0)
    d = ImageDraw.Draw(m)
    for xy, txt, font, alpha in spans:
        d.text(xy, txt, font=font, fill=alpha, anchor='ls')
    return m


def build():
    mark_cx, spans = layout()
    img = background(W * SS, H * SS, mark_cx).resize((W, H), Image.LANCZOS).convert('RGBA')

    mark = mark_only(MARK_BOX, 1.0)
    img.alpha_composite(mark, (round(mark_cx - MARK_BOX / 2), round(H / 2 - MARK_BOX / 2)))

    shadow = text_mask((W, H), [(xy, t, f, 150) for xy, t, f, _ in spans])
    shadow = shadow.filter(ImageFilter.GaussianBlur(5)).point(lambda v: int(v * 0.55))
    sh = Image.new('RGBA', (W, H), BLACK + (0,))
    sh.putalpha(shadow.transform(shadow.size, Image.AFFINE, (1, 0, 0, 0, 1, -2)))
    img = Image.alpha_composite(img, sh)

    txt = Image.new('RGBA', (W, H), WHITE + (0,))
    txt.putalpha(text_mask((W, H), spans))
    img = Image.alpha_composite(img, txt)

    for (x, y), t, f, _ in spans:
        print(f'  {f.size:>3}pt  x {x}-{x + f.getlength(t):.0f}  '
              f'(safe band {INSET}-{W - INSET})')
    return img.convert('RGB')


if __name__ == '__main__':
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    out = os.path.join(root, 'playstore')
    os.makedirs(out, exist_ok=True)
    g = build()
    path = f'{out}/feature-graphic-1024x500.png'
    g.save(path, 'PNG', optimize=True)

    # Previews: full size, a 16:9 centre crop (height-filling, so the sides go),
    # and the small size the listing actually renders it at.
    cw = round(H * 16 / 9)
    crop = g.crop(((W - cw) // 2, 0, (W + cw) // 2, H))
    prev = Image.new('RGB', (W, H + 16 + 250 + 16), (238, 238, 242))
    prev.paste(g, (0, 0))
    prev.paste(crop.resize((cw // 2, H // 2), Image.LANCZOS), (0, H + 16))
    prev.paste(g.resize((328, 160), Image.LANCZOS), (cw // 2 + 24, H + 16))
    prev.save(f'{out}/_preview-feature.png')
    print(f'{path}  {os.path.getsize(path) // 1024} KB')
