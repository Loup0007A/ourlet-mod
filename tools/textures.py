#!/usr/bin/env python3
"""Textures, logo et avatar d'Ourlet, dessinés au pixel près (pas d'image source).

Direction artistique : le monde vu comme une broderie. Toile de lin écrue, fils de coton
(rose, indigo, safran, sauge), points de croix et points avant ; l'aiguille est en laiton.

Usage : python3 tools/textures.py      (écrit dans src/main/resources et curseforge/)
"""
import math
import os
import random

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources')
TEX = os.path.join(RES, 'assets', 'ourlet', 'textures', 'item')

LINEN = (237, 227, 207)
LINEN_D = (214, 200, 172)
LINEN_DD = (182, 165, 134)
INK = (58, 40, 38)
BRASS = (214, 168, 74)
BRASS_L = (247, 220, 140)
BRASS_D = (141, 98, 38)
STEEL = (196, 204, 214)
STEEL_L = (240, 244, 250)
STEEL_D = (110, 118, 132)
THREADS = {
    'rose': ((224, 122, 149), (247, 178, 196), (150, 62, 92)),
    'indigo': ((84, 88, 194), (150, 156, 236), (44, 42, 120)),
    'saffron': ((240, 169, 46), (252, 214, 120), (166, 98, 20)),
    'sage': ((127, 174, 136), (184, 220, 186), (70, 110, 80)),
}


def rgba(c, a=255):
    return (c[0], c[1], c[2], a)


# ───────────────────────── l'aiguille (16×16) ─────────────────────────

def needle():
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    metal = {}
    # tige fine en diagonale : un pixel clair, un pixel d'ombre
    for i in range(10):
        x, y = 2 + i, 13 - i
        metal[(x, y)] = STEEL_L if i < 7 else STEEL
        metal[(x + 1, y)] = STEEL_D
    metal[(1, 14)] = STEEL_L  # la pointe
    # le chas, en laiton, avec son trou
    for (x, y), c in {(12, 2): BRASS_L, (13, 1): BRASS_L, (14, 1): BRASS, (15, 2): BRASS_D, (14, 3): BRASS_D,
                      (13, 3): BRASS}.items():
        metal[(x, y)] = c
    for (x, y), c in metal.items():
        px[x, y] = rgba(c)
    # contour
    for y in range(16):
        for x in range(16):
            if (x, y) in metal or (x, y) == (14, 2):
                continue
            if any((x + dx, y + dy) in metal for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                px[x, y] = rgba(INK, 230)
    px[14, 2] = (0, 0, 0, 0)
    # le fil rose passe dans le chas et retombe en boucle jusqu'à un nœud
    rose, rose_l, rose_d = THREADS['rose']
    thread = [(14, 2), (15, 3), (15, 4), (14, 5), (14, 6), (15, 7), (15, 8), (14, 9), (13, 10), (13, 11), (14, 12),
              (14, 13)]
    for i, (x, y) in enumerate(thread):
        px[x, y] = rgba(rose_l if i % 3 == 1 else rose)
    for (x, y) in ((13, 14), (14, 14), (13, 15)):
        px[x, y] = rgba(rose_d)
    return img


# ───────────────────────── la couture (32×32, animée) ─────────────────────────

def seam(name, frames=8):
    main, light, dark = THREADS[name]
    sheet = Image.new('RGBA', (32, 32 * frames), (0, 0, 0, 0))
    rnd = random.Random(name)
    for f in range(frames):
        img = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        px = img.load()
        # gaze de lin : trame translucide, plus dense vers le bord
        for y in range(32):
            for x in range(32):
                edge = min(x, y, 31 - x, 31 - y)
                weave = (x + y) % 2 == 0
                a = 70 if weave else 40
                if edge < 3:
                    a = 150
                c = LINEN if weave else LINEN_D
                px[x, y] = rgba(c, a)
        # le pli : des points avant qui convergent vers le centre et glissent vers lui
        cx = cy = 15.5
        for k in range(8):
            ang = k * math.pi / 4 + math.pi / 8
            for s in range(4):
                r = 13.0 - ((s * 3.5 + f * 3.5 / frames) % 13.0)
                if r < 2.2:
                    continue
                for d in (0.0, 0.8, 1.6):
                    x = int(round(cx + math.cos(ang) * (r - d)))
                    y = int(round(cy + math.sin(ang) * (r - d)))
                    if 3 <= x <= 28 and 3 <= y <= 28:
                        px[x, y] = rgba(main if s % 2 else light, 230)
        # le cœur du pli : une fronce sombre
        for (x, y) in ((15, 15), (16, 16), (15, 16), (16, 15)):
            px[x, y] = rgba(dark, 255)
        for (x, y) in ((14, 15), (17, 16), (15, 14), (16, 17)):
            px[x, y] = rgba(main, 220)
        # le bord : une rangée de points de croix (× tous les 4 pixels)
        for t in range(1, 30, 5):
            for (x0, y0) in ((t, 0), (t, 28), (0, t), (28, t)):
                for i in range(3):
                    if 0 <= x0 + i < 32 and 0 <= y0 + i < 32:
                        px[x0 + i, y0 + i] = rgba(main)
                    if 0 <= x0 + 2 - i < 32 and 0 <= y0 + i < 32:
                        px[x0 + 2 - i, y0 + i] = rgba(light if i == 1 else main)
        # quatre nœuds aux coins
        for (x, y) in ((1, 1), (29, 1), (1, 29), (29, 29)):
            for dx in range(2):
                for dy in range(2):
                    px[x + dx, y + dy] = rgba(dark)
        sheet.paste(img, (0, 32 * f))
    return sheet


# ───────────────────────── logo et avatar ─────────────────────────

FONT = {  # 5×7, en points de croix
    'O': ["01110", "10001", "10001", "10001", "10001", "10001", "01110"],
    'U': ["10001", "10001", "10001", "10001", "10001", "10001", "01110"],
    'R': ["11110", "10001", "10001", "11110", "10100", "10010", "10001"],
    'L': ["10000", "10000", "10000", "10000", "10000", "10000", "11111"],
    'E': ["11111", "10000", "10000", "11110", "10000", "10000", "11111"],
    'T': ["11111", "00100", "00100", "00100", "00100", "00100", "00100"],
}


def cross(d, x, y, s, color, shade):
    """Un point de croix de s×s pixels."""
    d.line([(x, y), (x + s - 1, y + s - 1)], fill=shade, width=max(1, s // 3))
    d.line([(x + s - 1, y), (x, y + s - 1)], fill=color, width=max(1, s // 3))


def canvas(size):
    img = Image.new('RGBA', (size, size), rgba(LINEN))
    px = img.load()
    rnd = random.Random(7)
    for y in range(size):
        for x in range(size):
            if (x // 2 + y // 2) % 2 == 0:
                px[x, y] = rgba(LINEN_D)
            if rnd.random() < 0.03:
                px[x, y] = rgba(LINEN_DD)
    return img


def logo(size=400):
    img = canvas(size)
    d = ImageDraw.Draw(img)
    u = size / 100.0
    # le cerceau à broder : deux anneaux de bois
    for r, w, c in ((47, 5, (150, 104, 60)), (44, 2, (196, 146, 92)), (49, 1, (96, 62, 34))):
        d.ellipse([size / 2 - r * u, size / 2 - r * u, size / 2 + r * u, size / 2 + r * u], outline=c, width=int(w * u))
    d.rectangle([size / 2 - 6 * u, 1 * u, size / 2 + 6 * u, 6 * u], fill=(120, 84, 48))
    d.rectangle([size / 2 - 3 * u, 0, size / 2 + 3 * u, 3 * u], fill=BRASS)
    # deux carrés cousus (les deux faces) et le fil qui les relie par un pli
    s = int(3 * u)
    for (gx, gy), name in (((22, 30), 'rose'), ((60, 30), 'indigo')):
        main, light, dark = THREADS[name]
        for i in range(6):
            for (x, y) in ((gx + i * 3, gy), (gx + i * 3, gy + 15), (gx, gy + i * 3), (gx + 15, gy + i * 3)):
                cross(d, int(x * u), int(y * u), s, main, dark)
        cross(d, int((gx + 7.5) * u), int((gy + 7.5) * u), s, dark, dark)
    # le fil : il sort d'un carré, plonge dans la toile (pointillés) et ressort dans l'autre
    rose = THREADS['rose'][0]
    pts = []
    for i in range(41):
        t = i / 40
        x = 30 + t * 38
        y = 38 + math.sin(t * math.pi) * -14
        pts.append((x * u, y * u))
    for i in range(0, len(pts) - 1, 2):
        d.line([pts[i], pts[i + 1]], fill=rose, width=int(1.3 * u))
    # l'aiguille
    d.line([(64 * u, 22 * u), (82 * u, 10 * u)], fill=STEEL_D, width=int(2.2 * u))
    d.line([(64 * u, 21 * u), (81 * u, 9.5 * u)], fill=STEEL_L, width=int(0.8 * u))
    d.ellipse([79 * u, 7 * u, 85 * u, 12 * u], outline=BRASS, width=int(1.2 * u))
    # OURLET en points de croix
    word = "OURLET"
    cell = 2.1
    total = len(word) * 6 * cell - cell
    x0 = 50 - total / 2
    for li, ch in enumerate(word):
        for ry, row in enumerate(FONT[ch]):
            for rx, bit in enumerate(row):
                if bit == '1':
                    x = x0 + (li * 6 + rx) * cell
                    y = 58 + ry * cell
                    cross(d, int(x * u), int(y * u), int(cell * u), INK, (110, 70, 64))
    # une ligne de points avant sous le titre
    for i in range(14):
        x = 22 + i * 4.3
        d.line([(x * u, 82 * u), ((x + 2.6) * u, 82 * u)], fill=THREADS['saffron'][0], width=int(1.1 * u))
    # masque rond (le cerceau)
    mask = Image.new('L', (size, size), 0)
    ImageDraw.Draw(mask).ellipse([size / 2 - 50 * u, size / 2 - 50 * u, size / 2 + 50 * u, size / 2 + 50 * u], fill=255)
    out = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    out.paste(img, (0, 0), mask)
    return out


def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print('écrit', os.path.relpath(path, ROOT))


if __name__ == '__main__':
    save(needle(), os.path.join(TEX, 'spatial_needle.png'))
    for name in THREADS:
        save(seam(name), os.path.join(TEX, f'seam_{name}.png'))
        with open(os.path.join(TEX, f'seam_{name}.png.mcmeta'), 'w') as f:
            f.write('{\n  "animation": {\n    "frametime": 4,\n    "interpolate": true\n  }\n}\n')
    save(logo(400), os.path.join(ROOT, 'curseforge', 'avatar.png'))
    save(logo(256), os.path.join(RES, 'ourlet_logo.png'))
