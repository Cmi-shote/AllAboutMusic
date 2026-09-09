#!/usr/bin/env python3
"""Regenerates the AllAboutMusic app icon set from one design definition.

The mark: five rounded waveform bars inside a soft disc, on a violet -> magenta
diagonal gradient. Everything is rendered at 4x and downsampled with Lanczos.

Requires Pillow (`pip3 install pillow`). Run from anywhere:
    python3 tools/generate_app_icons.py

Outputs:
    composeApp/src/androidMain/res/mipmap-*/   legacy + adaptive + monochrome layers
    iosApp/.../AppIcon.appiconset/             1024 marketing, dark, tinted
    playstore/                                 512 store icon + 1024 master + previews
"""
from PIL import Image, ImageDraw, ImageFilter
import os

# --- design constants: fractions of the canvas edge --------------------------
DISC_R, RING_W = 0.345, 0.009
BAR_W, BAR_GAP, BAR_MAX_H = 0.070, 0.049, 0.455
HEIGHTS = [0.44, 0.72, 1.00, 0.72, 0.44]
SHADOW_BLUR, SHADOW_DY, SHADOW_A = 0.022, 0.016, 0.26
DISC_A, RING_A = 30, 64                      # white overlay alphas, 0-255
STOPS = [(0.0, '3B0F7A'), (0.45, '7C3AED'), (1.0, 'F0468F')]
SS = 4                                       # supersample factor

WHITE, BLACK = (255, 255, 255), (0, 0, 0)


def hx(h):
    h = h.lstrip('#')
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(t):
    stops = [(p, hx(c)) for p, c in STOPS]
    if t <= stops[0][0]:
        return stops[0][1]
    for (p0, c0), (p1, c1) in zip(stops, stops[1:]):
        if t <= p1:
            f = (t - p0) / (p1 - p0)
            return tuple(round(c0[i] + (c1[i] - c0[i]) * f) for i in range(3))
    return stops[-1][1]


def radial(s, cx, cy, r, peak):
    """Soft radial falloff mask, `peak` at the centre."""
    m = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(m)
    steps = 220
    for i in range(steps, 0, -1):
        t = i / steps
        rr = r * t
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=round(peak * (1 - t) ** 2))
    return m.filter(ImageFilter.GaussianBlur(s * 0.02))


# --- mark component masks ----------------------------------------------------
def disc_mask(s, k, a=DISC_A):
    m = Image.new('L', (s, s), 0)
    r = s * DISC_R * k
    ImageDraw.Draw(m).ellipse([s / 2 - r, s / 2 - r, s / 2 + r, s / 2 + r], fill=a)
    return m


def ring_mask(s, k, a=RING_A):
    m = Image.new('L', (s, s), 0)
    r = s * DISC_R * k
    ImageDraw.Draw(m).ellipse([s / 2 - r, s / 2 - r, s / 2 + r, s / 2 + r],
                              outline=a, width=max(1, int(s * RING_W * k)))
    return m


def bars_mask(s, k):
    m = Image.new('L', (s, s), 0)
    d = ImageDraw.Draw(m)
    bw, gap, mh = s * BAR_W * k, s * BAR_GAP * k, s * BAR_MAX_H * k
    total = len(HEIGHTS) * bw + (len(HEIGHTS) - 1) * gap
    x = (s - total) / 2
    for f in HEIGHTS:
        h = mh * f
        d.rounded_rectangle([x, s / 2 - h / 2, x + bw, s / 2 + h / 2],
                            radius=bw / 2, fill=255)
        x += bw + gap
    return m


def shadow_mask(s, k, bars):
    sh = bars.filter(ImageFilter.GaussianBlur(s * SHADOW_BLUR * k))
    sh = sh.point(lambda v: int(v * SHADOW_A))
    return sh.transform(sh.size, Image.AFFINE, (1, 0, 0, 0, 1, -s * SHADOW_DY * k))


def layers(s, k):
    """The mark as (color, mask) pairs in paint order."""
    bars = bars_mask(s, k)
    return [(WHITE, disc_mask(s, k)),
            (WHITE, ring_mask(s, k)),
            (BLACK, shadow_mask(s, k, bars)),
            (WHITE, bars)]


# --- renderers ---------------------------------------------------------------
def background(n):
    """Gradient background layer, no mark."""
    s = n * SS
    img = Image.new('RGB', (s, s))
    d = ImageDraw.Draw(img)
    for i in range(2 * s + 1):                       # sweep the TL->BR diagonal
        d.line([(i, 0), (0, i)], fill=ramp(i / (2 * s)), width=2)
    for mask, color in ((radial(s, s * .28, s * .20, s * .85, 46), WHITE),
                        (radial(s, s * .92, s * 1.02, s * .90, 60), BLACK)):
        img = Image.composite(Image.new('RGB', (s, s), color), img, mask)
    return img


def full(n, shape='square'):
    """Complete opaque icon. shape: square | rounded | circle."""
    s = n * SS
    img = background(n)
    for color, mask in layers(s, 1.0):
        img = Image.composite(Image.new('RGB', (s, s), color), img, mask)
    img = img.convert('RGBA')
    if shape != 'square':
        m = Image.new('L', (s, s), 0)
        d = ImageDraw.Draw(m)
        if shape == 'circle':
            d.ellipse([0, 0, s - 1, s - 1], fill=255)
        else:
            d.rounded_rectangle([0, 0, s - 1, s - 1], radius=s * 0.22, fill=255)
        img.putalpha(m)
    return img.resize((n, n), Image.LANCZOS)


def mark_only(n, k, mono=False):
    """The mark on a transparent ground (adaptive foreground, iOS dark/tinted)."""
    s = n * SS
    out = Image.new('RGBA', (s, s), (0, 0, 0, 0))
    if mono:
        # Flat silhouette; the launcher supplies the tint.
        alpha = Image.new('L', (s, s))
        ring, bars = ring_mask(s, k, 255), bars_mask(s, k)
        alpha.putdata([max(a, b) for a, b in zip(ring.getdata(), bars.getdata())])
        out = Image.new('RGBA', (s, s), BLACK + (0,))
        out.putalpha(alpha)
    else:
        for color, mask in layers(s, k):
            lyr = Image.new('RGBA', (s, s), color + (0,))
            lyr.putalpha(mask)
            out = Image.alpha_composite(out, lyr)
    return out.resize((n, n), Image.LANCZOS)


# --- outputs -----------------------------------------------------------------
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'composeApp/src/androidMain/res')
IOS = os.path.join(ROOT, 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset')
STORE = os.path.join(ROOT, 'playstore')
SHARED_RES = os.path.join(ROOT, 'composeApp/src/commonMain/composeResources/drawable')

LEGACY = {'mdpi': 48, 'hdpi': 72, 'xhdpi': 96, 'xxhdpi': 144, 'xxxhdpi': 192}
ADAPTIVE = {'mdpi': 108, 'hdpi': 162, 'xhdpi': 216, 'xxhdpi': 324, 'xxxhdpi': 432}

# Adaptive layers are 108dp with only the inner 66dp guaranteed visible. Scale the
# mark so the disc spans 52dp: clear of the safe-zone edge, and the same visual
# weight as the standalone icon once a circular launcher mask is applied.
K_ADAPTIVE = (52.0 / 108.0) / (DISC_R * 2)
K_IOS = (0.69 * 0.80) / (DISC_R * 2)      # slightly inset for the dark/tinted marks


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, 'PNG', optimize=True)


if __name__ == '__main__':
    for dens, px in LEGACY.items():
        save(full(px, 'rounded'), f'{RES}/mipmap-{dens}/ic_launcher.png')
        save(full(px, 'circle'), f'{RES}/mipmap-{dens}/ic_launcher_round.png')

    for dens, px in ADAPTIVE.items():
        save(background(px).resize((px, px), Image.LANCZOS),
             f'{RES}/mipmap-{dens}/ic_launcher_background.png')
        save(mark_only(px, K_ADAPTIVE), f'{RES}/mipmap-{dens}/ic_launcher_foreground.png')
        save(mark_only(px, K_ADAPTIVE, mono=True),
             f'{RES}/mipmap-{dens}/ic_launcher_monochrome.png')

    # iOS: the marketing icon must be opaque; dark and tinted are transparent so
    # the system can draw its own background behind them.
    save(full(1024).convert('RGB'), f'{IOS}/app-icon-1024.png')
    dark = mark_only(1024, K_IOS)
    save(dark, f'{IOS}/app-icon-1024-dark.png')
    g, a = dark.convert('LA').split()
    save(Image.merge('RGBA', (g, g, g, a)), f'{IOS}/app-icon-1024-tinted.png')

    save(full(512).convert('RGBA'), f'{STORE}/playstore-icon-512.png')
    save(full(1024).convert('RGBA'), f'{STORE}/icon-1024.png')

    # Shipped in the app: shown for mixes with no cover, and embedded as the
    # cover art of exported mixes. Opaque, since it ends up in an MP4 covr atom.
    save(full(512).convert('RGB'), f'{SHARED_RES}/default_mix_cover.png')
    print('app icons regenerated')
