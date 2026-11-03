"""Generate deterministic, synthetic upload fixtures; requires Pillow (test tooling only)."""
from pathlib import Path
import hashlib
import json
import shutil
import struct

from PIL import Image, ImageDraw, features

ROOT = Path(__file__).resolve().parents[1] / "docs" / "test-data" / "listing-images"
ROOT.mkdir(parents=True, exist_ok=True)


def photo(name, size=(1200, 900), color=(60, 125, 170), format=None, **options):
    image = Image.new("RGB", size, color)
    draw = ImageDraw.Draw(image)
    if size[0] <= 2000 and size[1] <= 2000:
        draw.rectangle((size[0] // 4, size[1] // 5, size[0] * 3 // 4, size[1] * 4 // 5),
                       fill=(210, 210, 215), outline="black", width=5)
        draw.line((size[0] // 4, size[1] // 2, size[0] * 3 // 4, size[1] // 2), fill="black", width=5)
        draw.text((25, 25), name, fill="white")
    path = ROOT / name
    image.save(path, format=format, **options)
    image.close()
    return path


front = photo("valid-front.jpg", quality=92)
photo("valid-back.png", color=(120, 80, 140))
if not features.check("webp"):
    raise RuntimeError("Pillow must support WebP to generate the genuine WebP fixtures")
photo("valid-label.webp", color=(80, 140, 80), quality=90)
photo("valid-bottom.jpg", color=(100, 80, 150), quality=92)
photo("valid-left.jpg", color=(90, 70, 150), quality=92)
photo("valid-right.jpg", color=(80, 160, 150), quality=92)
photo("boundary-600.png", size=(800, 600))
photo("too-small-599.png", size=(800, 599))
photo("too-many-pixels-8000x4000.png", size=(8000, 4000))
photo("too-wide-16001x600.png", size=(16001, 600))
photo("valid-large-4000x3000.jpg", size=(4000, 3000), quality=90)
photo("valid-blank-white.jpg", color=(255, 255, 255), quality=90)
shutil.copyfile(front, ROOT / "duplicate-front.jpg")
(ROOT / "empty.jpg").write_bytes(b"")
(ROOT / "fake-jpeg.jpg").write_text("This is plain text, not a JPEG image.", encoding="utf-8")
(ROOT / "truncated.jpg").write_bytes(front.read_bytes()[:100])
(ROOT / "truncated-no-eoi.jpg").write_bytes(front.read_bytes()[:-2])
(ROOT / "truncated-entropy-tail.jpg").write_bytes(front.read_bytes()[:-200])
(ROOT / "truncated.png").write_bytes((ROOT / "valid-back.png").read_bytes()[:40])
shutil.copyfile(ROOT / "valid-back.png", ROOT / "png-renamed-as-jpeg.jpg")
(ROOT / "unsupported.svg").write_text('<svg xmlns="http://www.w3.org/2000/svg" width="800" height="600"><rect width="800" height="600" fill="red"/></svg>', encoding="utf-8")

exif = Image.Exif()
exif[274] = 6
exif[270] = "Metadata must not appear in the stored listing image"
photo("phone-orientation-6.jpg", size=(1200, 800), exif=exif, quality=92)
photo("phone-orientation-6.png", size=(1200, 800), exif=exif)
photo("phone-orientation-6.webp", size=(1200, 800), exif=exif, quality=92)
gps = Image.Exif()
gps[34853] = {1: "N", 2: (0, 0, 0), 3: "E", 4: (0, 0, 0)}
photo("phone-with-gps.jpg", exif=gps)
same_pixels = Image.Exif()
same_pixels[274] = 1
same_pixels[270] = "Metadata-only duplicate"
payload = same_pixels.tobytes()
original = front.read_bytes()
(ROOT / "duplicate-front-exif.jpg").write_bytes(original[:2] + b"\xff\xe1" + struct.pack(">H", len(payload) + 2) + payload + original[2:])
transparent = Image.new("RGBA", (800, 600), (0, 0, 0, 0))
ImageDraw.Draw(transparent).rectangle((200, 150, 600, 450), fill=(200, 0, 0, 255))
transparent.save(ROOT / "valid-transparent.png")
transparent.close()
for orientation in range(1, 9):
    tag = Image.Exif()
    tag[274] = orientation
    quadrants = Image.new("RGB", (800, 600))
    draw = ImageDraw.Draw(quadrants)
    for box, color in (((0, 0, 399, 299), "red"), ((400, 0, 799, 299), "green"),
                       ((0, 300, 399, 599), "blue"), ((400, 300, 799, 599), "yellow")):
        draw.rectangle(box, fill=color)
    quadrants.save(ROOT / f"exif-orientation-{orientation}.jpg", exif=tag, quality=95)
    quadrants.close()

frames = [Image.new("RGB", (800, 600), color) for color in ("red", "blue")]
for name, format in (("animated.gif", "GIF"), ("animated.png", "PNG"), ("animated.webp", "WEBP")):
    frames[0].save(ROOT / name, format=format, save_all=True, append_images=frames[1:], duration=200, loop=0)
for frame in frames:
    frame.close()

# Padding after a valid JPEG makes byte-limit fixtures without decoding a huge bitmap.
original = front.read_bytes()
for name, length in (("boundary-exact-10MiB.jpg", 10 * 1024 * 1024),
                     ("too-large-10MiB-plus-1.jpg", 10 * 1024 * 1024 + 1)):
    (ROOT / name).write_bytes(original + b"\0" * (length - len(original)))

manifest = []
for path in sorted(ROOT.iterdir()):
    if path.suffix == ".json":
        continue
    raw = path.read_bytes()
    entry = {"file": path.name, "bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    try:
        with Image.open(path) as image:
            entry.update(format=image.format, width=image.width, height=image.height,
                         frames=getattr(image, "n_frames", 1), orientation=image.getexif().get(274, 1))
    except Exception:
        entry["decodable"] = False
    manifest.append(entry)
(ROOT / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
print(f"Generated {len(manifest)} fixtures in {ROOT}")
