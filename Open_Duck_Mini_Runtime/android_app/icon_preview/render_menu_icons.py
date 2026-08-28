"""Icônes menu : line-art blanc gras, fond transparent, 1024².

Les originaux fins restent dans source/. Ce script redessine des
versions simplifiées à trait épais (lisible à ~72 dp).
"""

from __future__ import annotations

import math
import shutil
from pathlib import Path

from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
DRAWABLE = HERE.parent / "android" / "app" / "src" / "main" / "res" / "drawable"
SOURCE = HERE / "source"
SIZE = 1024
WHITE = (255, 255, 255, 255)
W = 96
NAMES = [
    "icon_menu_piloter.png",
    "icon_menu_tests.png",
    "icon_menu_video.png",
    "icon_menu_eteindre.png",
]
CARD = {
    "icon_menu_piloter.png": (13, 110, 253),
    "icon_menu_tests.png": (32, 201, 151),
    "icon_menu_video.png": (13, 202, 240),
    "icon_menu_eteindre.png": (220, 53, 69),
}
VIDEO_TINT = (33, 37, 41, 255)


def ensure_source() -> None:
    SOURCE.mkdir(exist_ok=True)
    for name in NAMES:
        dst = SOURCE / name
        if dst.exists():
            continue
        src = HERE / name
        if src.exists():
            shutil.copy2(src, dst)


def canvas() -> tuple[Image.Image, ImageDraw.ImageDraw]:
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im)


def cap(draw: ImageDraw.ImageDraw, x: float, y: float, w: int = W) -> None:
    r = w / 2
    draw.ellipse((x - r, y - r, x + r, y + r), fill=WHITE)


def line(
    draw: ImageDraw.ImageDraw,
    a: tuple[float, float],
    b: tuple[float, float],
    w: int = W,
) -> None:
    draw.line([a, b], fill=WHITE, width=w)
    cap(draw, a[0], a[1], w)
    cap(draw, b[0], b[1], w)


def circle(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, w: int = W) -> None:
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=WHITE, width=w)


def rrect(
    draw: ImageDraw.ImageDraw,
    box: tuple[float, float, float, float],
    radius: float,
    w: int = W,
) -> None:
    draw.rounded_rectangle(box, radius=radius, outline=WHITE, width=w)


def poly(draw: ImageDraw.ImageDraw, pts: list[tuple[float, float]], w: int = W) -> None:
    for i in range(len(pts)):
        line(draw, pts[i], pts[(i + 1) % len(pts)], w)


def rotate_layer(draw_fn, angle_deg: float) -> Image.Image:
    im, d = canvas()
    draw_fn(d)
    return im.rotate(angle_deg, resample=Image.Resampling.BICUBIC, expand=False)


def composite(base: Image.Image, overlay: Image.Image) -> Image.Image:
    out = base.copy()
    out.alpha_composite(overlay)
    return out


def icon_piloter() -> Image.Image:
    im, d = canvas()
    circle(d, 512, 268, 158, 88)
    cap(d, 448, 252, 64)
    cap(d, 576, 252, 64)
    poly(d, [(512, 300), (470, 360), (512, 398), (554, 360)], 64)
    line(d, (418, 130), (360, 42), 72)
    line(d, (606, 130), (664, 42), 72)
    cap(d, 360, 42, 92)
    cap(d, 664, 42, 92)
    rrect(d, (330, 500, 694, 800), 86, 88)
    line(d, (330, 580), (210, 670), 72)
    cap(d, 198, 682, 84)
    line(d, (694, 580), (814, 670), 72)
    cap(d, 826, 682, 84)
    line(d, (424, 800), (400, 910), 72)
    line(d, (600, 800), (624, 910), 72)
    line(d, (318, 918), (470, 918), 72)
    line(d, (554, 918), (706, 918), 72)
    return im


def icon_tests() -> Image.Image:
    def wrench(d: ImageDraw.ImageDraw) -> None:
        # Manche plein + mâchoire ouverte en U (clé plate).
        d.rounded_rectangle((340, 458, 880, 566), radius=54, fill=WHITE)
        line(d, (360, 512), (220, 360), 88)
        line(d, (360, 512), (220, 664), 88)
        line(d, (220, 360), (130, 390), 88)
        line(d, (220, 664), (130, 634), 88)

    def screwdriver(d: ImageDraw.ImageDraw) -> None:
        d.rounded_rectangle((90, 430, 400, 594), radius=72, fill=WHITE)
        line(d, (380, 512), (860, 512), 64)
        line(d, (860, 458), (860, 566), 64)
        line(d, (860, 458), (920, 458), 56)
        line(d, (860, 566), (920, 566), 56)

    w_im = rotate_layer(wrench, 38)
    s_im = rotate_layer(screwdriver, -48)
    out = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    out.alpha_composite(w_im)
    out.alpha_composite(s_im)
    return out


def icon_video() -> Image.Image:
    im, d = canvas()
    rrect(d, (110, 340, 680, 790), 90, 92)
    circle(d, 330, 565, 148, 84)
    rrect(d, (240, 200, 520, 350), 52, 80)
    cap(d, 560, 420, 72)
    line(d, (680, 420), (910, 330), 84)
    line(d, (680, 710), (910, 800), 84)
    line(d, (910, 330), (910, 800), 84)
    return im


def icon_eteindre() -> Image.Image:
    im, d = canvas()
    bbox = (168, 238, 856, 926)
    cx = (bbox[0] + bbox[2]) / 2
    cy = (bbox[1] + bbox[3]) / 2
    rx = (bbox[2] - bbox[0]) / 2
    ry = (bbox[3] - bbox[1]) / 2
    stroke = 108
    d.arc(bbox, start=305, end=360, fill=WHITE, width=stroke)
    d.arc(bbox, start=0, end=235, fill=WHITE, width=stroke)
    for ang in (305, 235):
        rad = math.radians(ang)
        cap(d, cx + rx * math.cos(rad), cy + ry * math.sin(rad), stroke)
    line(d, (512, 150), (512, 520), stroke)
    return im


def preview_sheet(icons: dict[str, Image.Image]) -> None:
    cell, pad = 220, 24
    sheet = Image.new("RGB", (4 * cell + pad * 2, 2 * cell + pad * 2 + 36), (33, 37, 41))
    draw = ImageDraw.Draw(sheet)
    for r, px in enumerate((72, 144)):
        draw.text((pad, pad + r * cell - 2), f"{px} dp", fill=(222, 226, 230))
        for c, name in enumerate(NAMES):
            x = pad + c * cell
            y = pad + 22 + r * cell
            draw.rounded_rectangle((x, y, x + cell - 16, y + cell - 36), radius=12, fill=CARD[name])
            icon = icons[name]
            if name == "icon_menu_video.png":
                tinted = Image.new("RGBA", icon.size, VIDEO_TINT)
                tinted.putalpha(icon.split()[3])
                icon = tinted
            thumb = icon.resize((px, px), Image.Resampling.LANCZOS)
            ox = x + (cell - 16 - px) // 2
            oy = y + (cell - 36 - px) // 2
            sheet.paste(thumb, (ox, oy), thumb)
    sheet.save(HERE / "preview_menu_icons.png", "PNG")


def save(im: Image.Image, name: str) -> None:
    im.save(HERE / name, "PNG")
    im.save(DRAWABLE / name, "PNG")
    print("wrote", name)


def main() -> None:
    ensure_source()
    icons = {
        "icon_menu_piloter.png": icon_piloter(),
        "icon_menu_tests.png": icon_tests(),
        "icon_menu_video.png": icon_video(),
        "icon_menu_eteindre.png": icon_eteindre(),
    }
    for name, im in icons.items():
        save(im, name)
    preview_sheet(icons)
    print("wrote preview_menu_icons.png")


if __name__ == "__main__":
    main()
