#!/usr/bin/env python3
"""Réduit les rafales de captures du test client (gif_<scène>_<n>.png) en JPEG 640×360.

Usage : python3 tools/gif_frames.py <dossier des captures> <dossier de sortie>
L'animation elle-même est montée par tools/make_gif.py (légendes, logo, couleurs).
"""
import os
import sys

from PIL import Image


def main(src, out):
    os.makedirs(out, exist_ok=True)
    names = sorted(n for n in os.listdir(src) if n.startswith('gif_') and n.endswith('.png'))
    for n in names:
        try:
            im = Image.open(os.path.join(src, n)).convert('RGB')
        except OSError:
            continue  # capture pas encore écrite
        im = im.resize((640, 360), Image.LANCZOS)
        im.save(os.path.join(out, n[:-4] + '.jpg'), quality=90)
    print(f'{len(names)} images -> {out}')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
