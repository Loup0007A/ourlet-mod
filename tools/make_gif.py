#!/usr/bin/env python3
"""Monte l'animation de la description (curseforge/ourlet.gif) à partir des rafales du test client.

Les images viennent de la branche ci-logs : main/forge-1.21.1/gif/gif_<scène>_<n>.jpg
  a : on regarde un mur cousu, on marche dedans, on ressort à 3 000 blocs
  b : la vitrine — une grande couture cirée où un mouton tombe sans fin
  c : le Carnet de couture qui s'anime
Chaque scène reçoit une légende sur une bande de lin, le logo dans un coin, et un fondu au passage.

Usage : python3 tools/make_gif.py <dossier des images> [sortie.gif]
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H = 480, 270
FRAME_MS = 100  # une capture toutes les 2 ticks

SCENES = [  # (scène, première image, dernière image (exclue, None = jusqu'au bout), légende)
    ('a', 24, None, 'Walk into a seam... and step out 3,000 blocks away'),
    ('b', 2, None, 'Waxed seams: an endless, gentle fall'),
    ('c', 0, None, 'Hold [W]: the Sewing Notebook explains everything'),
]
LINEN = (237, 227, 207)
INK = (58, 40, 38)
ROSE = (224, 122, 149)


def font(size):
    for name in ('DejaVuSans-Bold.ttf', '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
                 '/usr/share/fonts/dejavu/DejaVuSans-Bold.ttf'):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def caption(img, text, f):
    """Une bande de lin en bas de l'image, bordée d'un point avant rose, avec la légende."""
    d = ImageDraw.Draw(img)
    band = 26
    d.rectangle([0, H - band, W, H], fill=LINEN)
    for x in range(4, W, 8):
        d.line([(x, H - band + 2), (x + 4, H - band + 2)], fill=ROSE, width=1)
    tw = d.textlength(text, font=f)
    d.text(((W - tw) / 2, H - band + 6), text, fill=INK, font=f)
    return img


def logo():
    p = os.path.join(ROOT, 'curseforge', 'avatar.png')
    if not os.path.exists(p):
        return None
    return Image.open(p).convert('RGBA').resize((46, 46), Image.LANCZOS)


def load(folder, scene, start, end):
    names = sorted(n for n in os.listdir(folder) if n.startswith(f'gif_{scene}_') and n.endswith('.jpg'))
    names = names[start:end]
    frames = []
    for n in names:
        im = Image.open(os.path.join(folder, n)).convert('RGB').resize((W, H), Image.LANCZOS)
        # une image identique à la précédente (le rendu logiciel est parfois plus lent que 10 images/s) : on allonge
        if frames and ImageChops.difference(frames[-1][0], im).getbbox() is None:
            frames[-1][1] += FRAME_MS
            continue
        frames.append([im, FRAME_MS])
    return frames


def main(folder, out):
    f = font(13)
    mark = logo()
    timeline = []
    for scene, start, end, text in SCENES:
        frames = load(folder, scene, start, end)
        if not frames:
            print(f'scène {scene} : aucune image')
            continue
        decorated = []
        for im, ms in frames:
            im = caption(im.copy(), text, f)
            if mark is not None:
                im.paste(mark, (8, 8), mark)
            decorated.append([im, ms])
        if timeline:
            # fondu enchaîné de trois images entre deux scènes
            prev = timeline[-1][0]
            for k in (0.25, 0.5, 0.75):
                timeline.append([Image.blend(prev, decorated[0][0], k), 60])
        timeline.extend(decorated)
        print(f'scène {scene} : {len(frames)} images, {sum(ms for _, ms in frames) / 1000:.1f} s')
    if not timeline:
        sys.exit('aucune image')
    timeline[-1][1] += 600  # un temps d'arrêt avant de recommencer
    pal = [im.quantize(colors=160, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
           for im, _ in timeline]
    pal[0].save(out, save_all=True, append_images=pal[1:], duration=[ms for _, ms in timeline], loop=0,
                optimize=True, disposal=1)
    print(f'{out} : {len(timeline)} images, {sum(ms for _, ms in timeline) / 1000:.1f} s, '
          f'{os.path.getsize(out) / 1e6:.1f} Mo')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'curseforge', 'ourlet.gif'))
