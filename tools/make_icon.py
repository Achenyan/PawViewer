from PIL import Image, ImageDraw, ImageFont
import os

BRAND = (0x63, 0xA4, 0xA8, 255)
WHITE = (255, 255, 255, 255)
FONT = r"C:\Windows\Fonts\arialbd.ttf"
OUT = r"D:\work_zone\pawviewer\app\src\main\res"


def draw_text(draw, size, text, ratio, fill):
    font = ImageFont.truetype(FONT, int(size * ratio))
    bbox = draw.textbbox((0, 0), text, font=font)
    w = bbox[2] - bbox[0]
    h = bbox[3] - bbox[1]
    x = (size - w) / 2 - bbox[0]
    y = (size - h) / 2 - bbox[1]
    draw.text((x, y), text, font=font, fill=fill)


def legacy(size, path):
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=int(size * 0.22), fill=BRAND)
    draw_text(d, size, "Pw", 0.44, WHITE)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def foreground(size, path):
    # 自适应图标前景：安全区约 66/108，字要留在中间
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    draw_text(d, size, "Pw", 0.26, WHITE)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


for name, s in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    legacy(s, os.path.join(OUT, f"mipmap-{name}", "ic_launcher.png"))

for name, s in [("mdpi", 108), ("hdpi", 162), ("xhdpi", 216), ("xxhdpi", 324), ("xxxhdpi", 432)]:
    foreground(s, os.path.join(OUT, f"mipmap-{name}", "ic_launcher_foreground.png"))

print("icons written")
