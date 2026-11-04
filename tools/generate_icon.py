#!/usr/bin/env python3
"""Generate launcher assets from one dollar-sign design (requires Pillow)."""
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
GREEN = '#253B32'
IVORY = '#F5F3ED'
# Cubic curves in a 108-unit viewport, shared with the Android vector.
CURVES = [
    ((69, 38), (64, 29), (39, 29), (39, 43)),
    ((39, 43), (39, 57), (69, 51), (69, 65)),
    ((69, 65), (69, 79), (44, 79), (39, 70)),
]


def draw_icon(size, shape='rounded'):
    scale = max(size, 1024) / 108
    edge = round(108 * scale)
    img = Image.new('RGBA', (edge, edge))
    draw = ImageDraw.Draw(img)
    bounds = (0, 0, edge - 1, edge - 1)
    if shape == 'round':
        draw.ellipse(bounds, fill=GREEN)
    elif shape == 'square':
        draw.rectangle(bounds, fill=GREEN)
    else:
        draw.rounded_rectangle(bounds, radius=21 * scale, fill=GREEN)
    points = []
    for p0, p1, p2, p3 in CURVES:
        for step in range(101):
            t = step / 100
            points.append(tuple(scale * ((1-t)**3*p0[i] + 3*(1-t)**2*t*p1[i]
                                         + 3*(1-t)*t*t*p2[i] + t**3*p3[i]) for i in (0, 1)))
    width = round(7 * scale)
    draw.line(points, fill=IVORY, width=width)
    # Overlap disks along the curve to keep thick strokes smooth at every size.
    for x, y in points:
        r = width / 2
        draw.ellipse((x-r, y-r, x+r, y+r), fill=IVORY)
    draw.line([(54*scale, 25*scale), (54*scale, 83*scale)], fill=IVORY, width=round(5*scale))
    for x, y in [points[0], points[-1]]:
        r = width / 2
        draw.ellipse((x-r, y-r, x+r, y+r), fill=IVORY)
    return img.resize((size, size), Image.Resampling.LANCZOS)


def main():
    for density, size in [('mdpi', 48), ('hdpi', 72), ('xhdpi', 96), ('xxhdpi', 144), ('xxxhdpi', 192)]:
        directory = ROOT / f'androidApp/src/main/res/mipmap-{density}'
        directory.mkdir(parents=True, exist_ok=True)
        draw_icon(size).save(directory / 'ic_launcher.png')
        draw_icon(size, 'round').save(directory / 'ic_launcher_round.png')
    # iOS applies its own corner mask; its source icon must be opaque.
    draw_icon(1024, 'square').convert('RGB').save(ROOT / 'iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png')
    resources = ROOT / 'desktopApp/src/main/resources'
    resources.mkdir(parents=True, exist_ok=True)
    icon = draw_icon(1024)
    icon.resize((256, 256), Image.Resampling.LANCZOS).save(resources / 'app-icon.png')
    icon.save(resources / 'app-icon.ico', sizes=[(16, 16), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    icon.save(resources / 'app-icon.icns')
    print('Generated Android, iOS, and desktop dollar-sign icons.')


if __name__ == '__main__':
    main()
