from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parent
SIZES = (64, 48, 32, 24, 16)
THEMES = ("light", "dark")

for theme in THEMES:
    src_dir = ROOT / "masters" / theme
    for src in sorted(src_dir.glob("cursor_*.png")):
        image = Image.open(src).convert("RGBA")
        for size in SIZES:
            out_dir = ROOT / "exports" / f"{size}x{size}" / theme
            out_dir.mkdir(parents=True, exist_ok=True)
            out = image.resize((size, size), Image.Resampling.LANCZOS)
            out.save(out_dir / src.name, optimize=True)

print("Generated Puppy Clicker pointer exports:", ", ".join(f"{s}x{s}" for s in SIZES))
