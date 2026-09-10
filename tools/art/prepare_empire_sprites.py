"""Prepare approved Imperial art (Pillow, numpy, scipy).

Usage: python tools/art/prepare_empire_sprites.py /path/to/sources.json
The JSON maps the nine family suffixes to approved source PNG paths.
Without an argument, regenerate overlays and review sheets from committed bases.
"""
import hashlib
import json
from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/ships'
OUT = ROOT / 'docs/assets/empire_refresh'
FAMILIES = ['corvette', 'frigate', 'destroyer', 'cruiser', 'battleship',
            'carrier', 'freight', 'tanker', 'fleet_support']


def prepare(path, ratio):
    im = Image.open(path).convert('RGBA')
    a = np.array(im)
    if a[:, :, 3].min() == 255:
        # Remove only border-connected near-white background, preserving light armor.
        white = np.min(a[:, :, :3], axis=2) > 225
        seed = np.zeros(white.shape, dtype=bool)
        seed[0] = white[0]
        seed[-1] = white[-1]
        seed[:, 0] = white[:, 0]
        seed[:, -1] = white[:, -1]
        background = ndimage.binary_propagation(seed, mask=white)
        a[background, 3] = 0
    # Remove isolated extraction specks and the one-pixel source matte fringe.
    labels, count = ndimage.label(a[:, :, 3] > 32)
    sizes = np.bincount(labels.ravel())
    sizes[0] = 0
    mask = labels == sizes.argmax()
    mask = ndimage.binary_erosion(mask, iterations=1)
    a[:, :, 3] = np.where(mask, a[:, :, 3], 0)
    a[a[:, :, 3] == 0] = 0
    im = Image.fromarray(a)
    im = im.crop(im.getbbox())
    # Engineering L/W is authoritative. Source illustration is presentation only.
    size = (660, round(660 / ratio))
    im = im.resize(size, Image.Resampling.LANCZOS)
    canvas = Image.new('RGBA', (768, 512))
    canvas.paste(im, ((768-size[0])//2, (512-size[1])//2))
    a = np.array(canvas)
    a[a[:, :, 3] < 12] = 0
    return Image.fromarray(a)


def overlays(base, family):
    a = np.array(base)
    rgb = a[:, :, :3].astype(float)
    inside = ndimage.binary_erosion(a[:, :, 3] > 240, iterations=2)
    # Extract actual cyan instrument pixels; never light ivory armor or brass paint.
    cyan = (rgb[:, :, 1] > rgb[:, :, 0] * 1.09) & (rgb[:, :, 2] > rgb[:, :, 0] * 1.10)
    cyan &= (rgb[:, :, 1] > 62) & inside
    emissive = np.zeros_like(a)
    emissive[cyan, :3] = (93, 168, 181)
    emissive[cyan, 3] = 160
    if not cyan.any():
        raise ValueError(f'{family}: no authored instrument pixels found')
    rng = np.random.default_rng(int.from_bytes(hashlib.sha256(family.encode()).digest()[:4], 'big'))
    ys, xs = np.where(inside)
    damage = Image.new('RGBA', base.size)
    draw = ImageDraw.Draw(damage)
    for _ in range(22):
        i = rng.integers(len(xs))
        x, y = int(xs[i]), int(ys[i])
        radius = int(rng.integers(2, 7))
        draw.ellipse((x-radius, y-radius//2, x+radius, y+radius//2), fill=(27, 23, 23, 100))
        draw.line((x-radius, y+1, x+radius+3, y-2), fill=(39, 33, 30, 180), width=1)
        draw.line((x, y+2, x+radius, y), fill=(159, 145, 123, 120), width=1)
    d = np.array(damage)
    d[~inside] = 0
    return Image.fromarray(emissive), Image.fromarray(d)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sources = json.loads(Path(sys.argv[1]).read_text()) if len(sys.argv) > 1 else None
    hulls = json.loads((ROOT / 'src/main/resources/data/content/stage22-empire-engineering-v1.json').read_text())['hulls']
    ratios = {h['id']: h['boundingDimensionsM']['lengthM']/h['boundingDimensionsM']['widthM'] for h in hulls}
    sheet = Image.new('RGB', (1536, 9*260+60), '#182128')
    draw = ImageDraw.Draw(sheet)
    draw.text((40, 20), 'EMPIRE | IMPERIAL NAVY', fill='white')
    draw.text((810, 20), 'INDUSTRIAL UNION | 660 x 220 PREVIEW BOX', fill='white')
    stats = []
    for index, family in enumerate(FAMILIES):
        folder = ASSETS / 'empire/production' / family
        basepath = folder / f'{family}_base.png'
        if sources:
            hull = 'freighter' if family == 'freight' else family
            base = prepare(sources[family], ratios[f'hull.empire_{hull}_v1'])
            base.save(basepath)
        else:
            base = Image.open(basepath).convert('RGBA')
        emissive, damage = overlays(base, family)
        emissive.save(folder / f'{family}_emissive.png')
        damage.save(folder / f'{family}_damage.png')
        for column, faction in enumerate(['empire', 'industrial_union']):
            im = Image.open(ASSETS / faction / 'production' / family / f'{family}_base.png').convert('RGBA')
            im = im.crop(im.getbbox())
            im.thumbnail((660, 220), Image.Resampling.LANCZOS)
            sheet.paste(im, (column*768+(768-im.width)//2, 60+index*260), im)
        draw.text((30, 62+index*260), family, fill='#cfc9bd')
        a = np.array(base)
        stats.append(dict(family=family, canvas=list(base.size), bounds=base.getbbox(),
                          colors=len(np.unique(a[a[:, :, 3] > 0, :3], axis=0)), bytes=basepath.stat().st_size,
                          opaquePixels=int((a[:, :, 3] > 0).sum())))
    sheet.save(OUT / 'empire_union_comparison.jpg', quality=94)
    (OUT / 'metrics.json').write_text(json.dumps(stats, indent=2)+'\n')
    print(json.dumps(stats, indent=2))


if __name__ == '__main__':
    main()
