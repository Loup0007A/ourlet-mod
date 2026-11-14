#!/usr/bin/env python3
"""Le Carnet de couture : les scènes du tutoriel (façon « Ponder » de Create), dessinées en code.

Chaque chapitre est une petite maquette isométrique posée sur une plaque de lin, brodée au point
de croix. Le script écrit :
  - les images des scènes : src/main/resources/assets/ourlet/textures/gui/carnet/<chapitre>_<n>.png
  - leur description : src/main/resources/assets/ourlet/carnet/chapters.json
    (étapes, textes à traduire, points visés par les bulles, objets qui se déplacent).
Les objets animés (diamant, flèche…) sont dessinés en jeu, par-dessus l'image, avec leur vraie icône.

Usage : python3 tools/carnet.py
"""
import json
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'ourlet')
OUT_IMG = os.path.join(RES, 'textures', 'gui', 'carnet')
OUT_JSON = os.path.join(RES, 'carnet', 'chapters.json')

W, H = 208, 150          # taille d'une scène (pixels de l'interface)
TW, TH, BH = 12, 6, 14   # demi-largeur et demi-hauteur d'une case, hauteur d'un bloc
OX, OY = W // 2, 44      # origine (coin nord-ouest de la plaque, au niveau 0)

# couleurs : (dessus, face gauche, face droite, motif)
BLOCKS = {
    'linen': ((236, 226, 204), (214, 200, 172), (196, 180, 150), (226, 214, 190)),
    'stone': ((150, 150, 156), (120, 120, 128), (104, 104, 112), (136, 136, 144)),
    'wool': ((240, 238, 232), (214, 210, 202), (196, 192, 184), (228, 224, 216)),
    'grass': ((118, 170, 92), (128, 98, 68), (110, 84, 58), (98, 150, 76)),
    'dirt': ((134, 102, 70), (118, 88, 60), (102, 76, 52), (124, 94, 64)),
    'netherrack': ((150, 56, 56), (122, 42, 46), (104, 34, 40), (134, 48, 50)),
    'bricks': ((76, 40, 48), (60, 30, 38), (50, 24, 32), (92, 52, 60)),
    'diamond': ((150, 150, 156), (120, 120, 128), (104, 104, 112), (96, 220, 220)),
    'piston': ((178, 146, 96), (120, 118, 122), (104, 102, 106), (150, 120, 80)),
    'head': ((178, 146, 96), (178, 146, 96), (150, 122, 80), (150, 120, 80)),
    'cobble': ((128, 128, 132), (106, 106, 112), (92, 92, 98), (150, 150, 154)),
    'redstone': ((200, 40, 36), (160, 28, 28), (140, 22, 22), (240, 80, 70)),
    'planks': ((196, 160, 104), (170, 136, 86), (150, 118, 74), (184, 148, 94)),
}
THREAD = {
    'rose': ((224, 122, 149), (150, 62, 92)),
    'indigo': ((84, 88, 194), (44, 42, 120)),
    'saffron': ((240, 169, 46), (166, 98, 20)),
    'sage': ((127, 174, 136), (70, 110, 80)),
    'basting': ((250, 246, 232), (150, 140, 120)),
}
INK = (70, 52, 46)


def proj(x, y, z):
    return (OX + (x - z) * TW, OY + (x + z) * TH - y * BH)


FACES = {  # sommets de chaque face visible
    'top': lambda x, y, z: [(x, y + 1, z), (x + 1, y + 1, z), (x + 1, y + 1, z + 1), (x, y + 1, z + 1)],
    'south': lambda x, y, z: [(x, y + 1, z + 1), (x + 1, y + 1, z + 1), (x + 1, y, z + 1), (x, y, z + 1)],   # +Z, à gauche
    'east': lambda x, y, z: [(x + 1, y + 1, z), (x + 1, y + 1, z + 1), (x + 1, y, z + 1), (x + 1, y, z)],    # +X, à droite
}


class Scene:
    def __init__(self):
        self.blocks = {}
        self.seams = []      # (pos, face, thread, scale)
        self.marks = []      # traits pointillés (fil entre deux points 3D)
        self.points = {}

    def put(self, x, y, z, kind):
        self.blocks[(x, y, z)] = kind
        return self

    def plate(self, kinds=None):
        for x in range(7):
            for z in range(7):
                k = 'linen'
                if kinds:
                    k = kinds(x, z)
                self.put(x, 0, z, k)
        return self

    def seam(self, pos, face, thread, scale=1.0, name=None):
        self.seams.append((pos, face, thread, scale))
        if name:
            self.points[name] = face_center(pos, face)
        return self

    def thread_arc(self, p, q, thread):
        self.marks.append((p, q, thread))
        return self

    def point(self, name, x, y, z):
        self.points[name] = proj(x, y, z)
        return self

    def copy(self):
        s = Scene()
        s.blocks = dict(self.blocks)
        s.seams = list(self.seams)
        s.marks = list(self.marks)
        s.points = dict(self.points)
        return s

    def render(self):
        img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        # ombre douce sous la plaque
        sh = [proj(0, 0, 0), proj(7, 0, 0), proj(7, 0, 7), proj(0, 0, 7)]
        d.polygon([(x + 3, y + 4) for x, y in sh], fill=(60, 44, 30, 60))
        order = sorted(self.blocks.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1]))
        for (x, y, z), kind in order:
            top, left, right, motif = BLOCKS[kind]
            for face, col in (('south', left), ('east', right), ('top', top)):
                if face == 'top' and (x, y + 1, z) in self.blocks:
                    continue
                if face == 'south' and (x, y, z + 1) in self.blocks:
                    continue
                if face == 'east' and (x + 1, y, z) in self.blocks:
                    continue
                poly = [proj(*v) for v in FACES[face](x, y, z)]
                d.polygon(poly, fill=col)
                stitch_face(img, poly, motif, kind)
                d.line(poly + [poly[0]], fill=shade(col, 0.72), width=1)
        for pos, face, thread, scale in self.seams:
            draw_seam(img, pos, face, thread, scale)
        for p, q, thread in self.marks:
            draw_arc(d, proj(*p), proj(*q), THREAD[thread][0])
        return img


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3])


def stitch_face(img, poly, motif, kind):
    """Petits points de croix réguliers sur la face (texture de canevas)."""
    px = img.load()
    xs = [p[0] for p in poly]
    ys = [p[1] for p in poly]
    mask = Image.new('L', img.size, 0)
    ImageDraw.Draw(mask).polygon(poly, fill=255)
    m = mask.load()
    for y in range(int(min(ys)) + 1, int(max(ys))):
        for x in range(int(min(xs)) + 1, int(max(xs))):
            if not m[x, y]:
                continue
            if kind == 'diamond':
                if (x // 2 + y // 2) % 4 == 0:
                    px[x, y] = motif + (255,)
            elif kind == 'redstone':
                if (x + y) % 3 == 0:
                    px[x, y] = motif + (255,)
            elif (x + y) % 4 == 0 and (x - y) % 4 == 0:
                px[x, y] = motif + (255,)


def face_center(pos, face):
    x, y, z = pos
    vs = FACES[face](x, y, z)
    cx = sum(v[0] for v in vs) / 4
    cy = sum(v[1] for v in vs) / 4
    cz = sum(v[2] for v in vs) / 4
    return proj(cx, cy, cz)


def draw_seam(img, pos, face, thread, scale):
    """La couture : un cadre de points de fil sur la face, resserré selon l'échelle."""
    main, dark = THREAD[thread]
    x, y, z = pos
    vs = FACES[face](x, y, z)
    c = [sum(v[i] for v in vs) / 4 for i in range(3)]
    k = 0.42 * scale
    pts = [proj(*(c[i] + (v[i] - c[i]) * (k / 0.5) for i in range(3))) for v in vs]
    d = ImageDraw.Draw(img)
    gauze = main + (70,)
    d.polygon(pts, fill=gauze)
    for i in range(4):
        a, b = pts[i], pts[(i + 1) % 4]
        n = 6
        for s in range(n):
            if s % 2 == 0:
                p = (a[0] + (b[0] - a[0]) * s / n, a[1] + (b[1] - a[1]) * s / n)
                q = (a[0] + (b[0] - a[0]) * (s + 1) / n, a[1] + (b[1] - a[1]) * (s + 1) / n)
                d.line([p, q], fill=main, width=1)
    cc = proj(*c)
    d.line([(cc[0] - 1, cc[1] - 1), (cc[0] + 1, cc[1] + 1)], fill=dark)
    d.line([(cc[0] + 1, cc[1] - 1), (cc[0] - 1, cc[1] + 1)], fill=dark)
    for p in pts:
        d.point(p, fill=dark)


def draw_arc(d, p, q, color):
    """Le fil qui relie deux coutures : un arc en points avant."""
    import math
    pts = []
    for i in range(41):
        t = i / 40
        x = p[0] + (q[0] - p[0]) * t
        y = p[1] + (q[1] - p[1]) * t - math.sin(t * math.pi) * 18
        pts.append((x, y))
    for i in range(0, 40, 3):
        d.line([pts[i], pts[i + 1]], fill=color, width=1)


# ───────────────────────── les chapitres ─────────────────────────

CHAPTERS = []
IMAGES = {}


def frame(chapter, n, scene):
    name = f'{chapter}_{n}'
    IMAGES[name] = scene.render()
    return name


def p3(x, y, z):
    return list(proj(x, y, z))


def chapter(cid, icon, steps):
    CHAPTERS.append({'id': cid, 'icon': icon, 'steps': steps})


def step(frame_name, key, point=None, ticks=70, items=None):
    s = {'frame': frame_name, 'text': f'ourlet.carnet.{key}', 'ticks': ticks}
    if point is not None:
        s['point'] = list(point)
    if items:
        s['items'] = items
    return s


def move(item, *pts):
    return {'item': item, 'path': [list(p) if p is not None else None for p in pts]}


def build():
    # 1. Coudre deux faces
    base = Scene().plate()
    for y in (1, 2):
        base.put(1, y, 4, 'stone')
        base.put(5, y, 1, 'stone')
    base.put(5, 3, 1, 'stone')
    a = ((1, 1, 4), 'east')
    b = ((5, 1, 1), 'south')
    f0 = frame('sew', 0, base)
    s1 = base.copy().seam(*a, 'rose')
    f1 = frame('sew', 1, s1)
    s2 = s1.copy().seam(*b, 'rose').thread_arc((2, 1.5, 4.5), (5.5, 1.5, 2), 'rose')
    f2 = frame('sew', 2, s2)
    pa, pb = face_center(*a), face_center(*b)
    chapter('sew', 'ourlet:spatial_needle', [
        step(f0, 'sew.1', pa, 60),
        step(f1, 'sew.2', pa, 70, [move('ourlet:spatial_needle', (pa[0] + 14, pa[1] - 16), (pa[0] + 4, pa[1] - 4))]),
        step(f2, 'sew.3', pb, 80, [move('ourlet:spatial_needle', (pb[0] - 18, pb[1] - 14), (pb[0] - 6, pb[1] - 4))]),
        step(f2, 'sew.4', None, 70),
        step(f2, 'sew.5', pa, 70),
    ])

    # 2. Le passage : un sol cousu à un mur
    s = Scene().plate()
    for y in (1, 2):
        s.put(5, y, 1, 'stone')
    s.seam((1, 0, 4), 'top', 'indigo')
    s.seam((5, 1, 1), 'east', 'indigo')
    f = frame('pass', 0, s)
    top = face_center((1, 0, 4), 'top')
    out = face_center((5, 1, 1), 'east')
    fall = [(top[0], top[1] - 46), (top[0], top[1] - 4), None, (out[0], out[1]), (out[0] + 34, out[1] + 14)]
    chapter('pass', 'minecraft:diamond', [
        step(f, 'pass.1', top, 80, [move('minecraft:diamond', *fall)]),
        step(f, 'pass.2', out, 90, [move('minecraft:diamond', *fall)]),
        step(f, 'pass.3', out, 90, [move('minecraft:arrow', (out[0] + 46, out[1] + 20), (out[0] + 2, out[1]), None,
                                         (top[0], top[1]), (top[0], top[1] - 50))]),
        step(f, 'pass.4', None, 70),
    ])

    # 3. Les pistons : le piston pousse vers l'ouest, contre la face est cousue
    s = Scene().plate()
    s.put(1, 1, 5, 'stone').put(2, 1, 5, 'cobble').put(3, 1, 5, 'piston')
    for y in (1, 2):
        s.put(5, y, 1, 'stone')
    s.seam((1, 1, 5), 'east', 'saffron')
    s.seam((5, 1, 1), 'south', 'saffron')
    f0 = frame('piston', 0, s)
    e = s.copy()
    e.put(2, 1, 5, 'head')
    e.put(5, 1, 2, 'cobble')
    f1 = frame('piston', 1, e)
    sa = face_center((1, 1, 5), 'east')
    sb = face_center((5, 1, 1), 'south')
    chapter('piston', 'minecraft:piston', [
        step(f0, 'piston.1', face_center((3, 1, 5), 'east'), 70),
        step(f0, 'piston.2', sa, 70, [move('minecraft:cobblestone', (sa[0] + 14, sa[1] + 6), (sa[0], sa[1]), None,
                                          (sb[0], sb[1]), (sb[0] - 8, sb[1] + 8))]),
        step(f1, 'piston.3', face_center((5, 1, 2), 'south'), 80),
    ])

    # 4. À travers le pli : la maison à gauche, la mine à droite (la plaque est pliée en deux)
    def halves(x, z):
        return 'linen' if x - z < 0 else ('stone' if x - z > 0 else 'wool')
    s = Scene().plate(halves)
    for y in (1, 2):
        s.put(1, y, 4, 'wool')
        s.put(5, y, 1, 'stone')
    s.put(5, 1, 3, 'diamond')
    s.seam((1, 1, 4), 'east', 'sage')
    s.seam((5, 1, 1), 'south', 'sage')
    f0 = frame('relay', 0, s)
    e = s.copy()
    del e.blocks[(5, 1, 3)]
    f1 = frame('relay', 1, e)
    sa = face_center((1, 1, 4), 'east')
    sb = face_center((5, 1, 1), 'south')
    ore = face_center((5, 1, 3), 'top')
    chapter('relay', 'minecraft:diamond_pickaxe', [
        step(f0, 'relay.1', sa, 70),
        step(f0, 'relay.2', ore, 80, [move('minecraft:diamond_pickaxe', (sa[0] + 16, sa[1] - 10), (sa[0] + 6, sa[1] - 2))]),
        step(f1, 'relay.3', sa, 80, [move('minecraft:diamond', (ore[0], ore[1] - 4), (sb[0], sb[1]), None,
                                          (sa[0], sa[1]), (sa[0] + 18, sa[1] + 10))]),
        step(f1, 'relay.4', sa, 80, [move('minecraft:torch', (sa[0] + 16, sa[1] - 10), (sa[0] + 4, sa[1] - 2))]),
        step(f1, 'relay.5', None, 70),
    ])

    # 5. Le dé à coudre : la maison à gauche, une grotte lointaine à droite
    s = Scene().plate()
    for x in (0, 1):
        for y in (1, 2):
            s.put(x, y, 4, 'planks')
    s.put(0, 3, 4, 'planks').put(1, 3, 4, 'planks')
    s.put(5, 1, 1, 'stone').put(5, 2, 1, 'stone').put(6, 1, 2, 'stone')
    home = ((1, 1, 4), 'east')
    far = ((5, 1, 1), 'south')
    f0 = frame('thimble', 0, s)
    f1 = frame('thimble', 1, s.copy().seam(*home, 'basting').seam(*far, 'basting')
               .thread_arc((2, 1.5, 4.5), (5.5, 1.5, 2), 'basting'))
    ph, pf = face_center(*home), face_center(*far)
    chapter('thimble', 'ourlet:thimble', [
        step(f0, 'thimble.1', ph, 80, [move('ourlet:thimble', (ph[0] + 14, ph[1] - 12), (ph[0] + 4, ph[1] - 3))]),
        step(f1, 'thimble.2', pf, 80, [move('ourlet:thimble', (pf[0] - 16, pf[1] - 12), (pf[0] - 5, pf[1] - 3))]),
        step(f1, 'thimble.3', pf, 80, [move('minecraft:ender_pearl', (pf[0] - 26, pf[1] + 12), (pf[0], pf[1]), None,
                                            (ph[0], ph[1]), (ph[0] + 22, ph[1] + 10))]),
        step(f0, 'thimble.4', ph, 70),
    ])

    # 6. Ciseaux et redstone
    s = Scene().plate()
    for y in (1, 2):
        s.put(1, y, 4, 'stone')
        s.put(5, y, 1, 'stone')
    a = ((1, 1, 4), 'east')
    b = ((5, 1, 1), 'south')
    f0 = frame('control', 0, s.copy().seam(*a, 'rose').seam(*b, 'rose'))
    f1 = frame('control', 1, s.copy().seam(*a, 'rose').seam(*b, 'rose', 0.65))
    f2 = frame('control', 2, s.copy().seam(*a, 'rose', 0.45).seam(*b, 'rose', 0.45))
    f3 = frame('control', 3, s.copy().put(1, 1, 5, 'redstone').seam(*a, 'rose', 0.45).seam(*b, 'rose', 0.45))
    pa, pb = face_center(*a), face_center(*b)
    rs = face_center((1, 1, 5), 'top')
    chapter('control', 'minecraft:shears', [
        step(f0, 'control.1', pa, 70, [move('minecraft:shears', (pa[0] + 16, pa[1] - 12), (pa[0] + 4, pa[1] - 3))]),
        step(f1, 'control.2', pb, 80),
        step(f2, 'control.3', pa, 70),
        step(f3, 'control.4', rs, 80, [move('minecraft:redstone_block', (rs[0] + 20, rs[1] - 20), (rs[0], rs[1] - 6))]),
        step(f0, 'control.5', None, 70),
    ])

    # 7. Entre les dimensions : moitié Surface, moitié Nether
    def worlds(x, z):
        return 'grass' if x - z < 0 else ('netherrack' if x - z > 0 else 'linen')
    s = Scene().plate(worlds)
    for y in (1, 2):
        s.put(1, y, 4, 'stone')
        s.put(5, y, 1, 'bricks')
    s.seam((1, 1, 4), 'east', 'indigo')
    s.seam((5, 1, 1), 'south', 'indigo')
    f0 = frame('dimensions', 0, s)
    pa, pb = face_center((1, 1, 4), 'east'), face_center((5, 1, 1), 'south')
    chapter('dimensions', 'minecraft:obsidian', [
        step(f0, 'dimensions.1', pa, 80),
        step(f0, 'dimensions.2', pb, 90, [move('minecraft:gold_ingot', (pb[0] - 18, pb[1] + 12), (pb[0], pb[1]), None,
                                              (pa[0], pa[1]), (pa[0] + 22, pa[1] + 10))]),
        step(f0, 'dimensions.3', None, 70),
    ])


def main():
    build()
    os.makedirs(OUT_IMG, exist_ok=True)
    for f in os.listdir(OUT_IMG):
        if f.endswith('.png'):
            os.remove(os.path.join(OUT_IMG, f))
    for name, img in IMAGES.items():
        img.save(os.path.join(OUT_IMG, name + '.png'))
    os.makedirs(os.path.dirname(OUT_JSON), exist_ok=True)
    with open(OUT_JSON, 'w', encoding='utf-8') as fh:
        json.dump({'width': W, 'height': H, 'chapters': CHAPTERS}, fh, ensure_ascii=False, indent=1)
    print(f'{len(IMAGES)} images, {len(CHAPTERS)} chapitres, '
          f'{sum(len(c["steps"]) for c in CHAPTERS)} étapes')
    keys = sorted({s['text'] for c in CHAPTERS for s in c['steps']})
    print('clés :', ' '.join(keys))


if __name__ == '__main__':
    main()
