#!/usr/bin/env python3
"""Compare Kotlin and React Native Android screenshot *pairs* without rescaling.

Usage:
  python3 -m pip install Pillow
  python3 Mobile/scripts/android_visual_audit.py \
    --kotlin screenshots/kotlin --react screenshots/react \
    --output screenshots/report --tolerance 8

Only identical screenshot resolutions and device configuration are comparable.
The script does NOT claim screenshots are equivalent and does not capture data
from either app. Never use it with production account data without permission.
"""
import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Optional

try:
    from PIL import Image, ImageChops, ImageDraw, ImageStat
except ImportError as exc:
    raise SystemExit("Pillow required: python3 -m pip install Pillow") from exc


def audit(kotlin: Path, react: Path, output: Path, tolerance: int,
          crop_top: int, crop_bottom: int, fail_fraction: Optional[float]) -> int:
    output.mkdir(parents=True, exist_ok=True)
    left = {f.name: f for f in kotlin.glob("*.png") if f.is_file()}
    right = {f.name: f for f in react.glob("*.png") if f.is_file()}
    if not left or not right:
        raise SystemExit("No screenshot pairs: both input directories must contain PNGs")
    results = []
    print("Screen                  Changed%  Mean error  Dimensions    Status")
    print("-" * 78)
    for name in sorted(left.keys() | right.keys()):
        if name not in left or name not in right:
            missing = "Kotlin" if name not in left else "React"
            results.append({"screen": name, "status": f"MISSING_{missing.upper()}"})
            print(f"{name[:23]:23} {'-':>8} {'-':>11} {'-':>13}   Missing {missing}")
            continue
        with Image.open(left[name]) as ksource, Image.open(right[name]) as rsource:
            k = ksource.convert("RGB")
            r = rsource.convert("RGB")
        if k.size != r.size:
            results.append({
                "screen": name, "status": "SIZE_MISMATCH",
                "kotlin_size": list(k.size), "react_size": list(r.size),
            })
            print(f"{name[:23]:23} {'-':>8} {'-':>11} {'-':>13}   SIZE MISMATCH")
            continue
        width, height = k.size
        if width < 1 or height - crop_bottom - crop_top < 1:
            raise SystemExit(f"Invalid crop for {name}: {(width, height)}")
        if crop_top or crop_bottom:
            region = (0, crop_top, width, height - crop_bottom)
            k = k.crop(region)
            r = r.crop(region)
        delta_rgb = ImageChops.difference(k, r)
        channels = delta_rgb.split()
        maximum = ImageChops.lighter(ImageChops.lighter(channels[0], channels[1]), channels[2])
        mask = maximum.point([255 if v > tolerance else 0 for v in range(256)])
        histogram = mask.histogram()
        changed = histogram[255]
        fraction = changed / (k.width * k.height)
        mean_error = sum(ImageStat.Stat(delta_rgb).mean) / 3
        bounds = mask.getbbox()
        status = "DIFFERENCES" if changed else "EXACT_WITHIN_TOLERANCE"
        if fail_fraction is not None and fraction > fail_fraction:
            status = "OVER_LIMIT"
        results.append({
            "screen": name, "status": status,
            "size": [k.width, k.height],
            "changed_pixels": changed, "total_pixels": k.width * k.height,
            "changed_fraction": round(fraction, 7),
            "changed_percent": round(fraction * 100, 4),
            "mean_channel_error": round(mean_error, 4),
            "difference_bounds_xyxy": list(bounds) if bounds else None,
            "tolerance_per_channel": tolerance,
        })
        # A diagnostic overlay, not a substituted or AI-generated game screenshot.
        overlay = Image.composite(Image.new("RGB", k.size, (255, 28, 45)), r, mask)
        overlay.save(output / f"{Path(name).stem}.diff.png")
        pair = Image.new("RGB", (k.width * 3, k.height + 26), (21, 24, 29))
        draw = ImageDraw.Draw(pair)
        for pos, label, im in [(0, "KOTLIN", k), (1, "REACT", r), (2, "DIFFERENCE", overlay)]:
            pair.paste(im, (pos * k.width, 26))
            draw.text((pos * k.width + 8, 7), label, fill=(255, 255, 255))
        pair.save(output / f"{Path(name).stem}.comparison.png")
        print(f"{name[:23]:23} {fraction * 100:>7.2f}% {mean_error:>11.2f} "
              f"{k.width:>4}x{k.height:<8} {status}")
    report = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "source": {"kotlin": str(kotlin), "react": str(react)},
        "tolerance_per_channel": tolerance,
        "crop_top_px": crop_top,
        "crop_bottom_px": crop_bottom,
        "fail_fraction": fail_fraction,
        "scope": "identical resolution screenshots; no scaling, warping or screenshot inference",
        "visual_parity_certified": False,
        "pairs": results,
    }
    (output / "report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf8")
    issues = [x for x in results if x["status"].startswith("MISSING_")
              or x["status"] in ("SIZE_MISMATCH", "OVER_LIMIT")]
    print(f"Compared {len(results)} name(s), {len(issues)} blocking issue(s); report: {output / 'report.json'}")
    return 1 if issues else 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--kotlin", required=True, type=Path, help="Original Kotlin screenshot PNG directory")
    parser.add_argument("--react", required=True, type=Path, help="React preview screenshot PNG directory")
    parser.add_argument("--output", required=True, type=Path, help="Diagnostic output directory")
    parser.add_argument("--tolerance", type=int, default=8, help="0-255 channel difference tolerance")
    parser.add_argument("--crop-top", type=int, default=0, help="Trim identical system status bar (px)")
    parser.add_argument("--crop-bottom", type=int, default=0, help="Trim identical Android nav bar (px)")
    parser.add_argument("--fail-fraction", type=float, default=None,
                        help="Fail if any changed fraction exceeds this ratio, e.g. 0.01")
    args = parser.parse_args()
    if args.tolerance < 0 or args.tolerance > 255:
        parser.error("tolerance must be in 0..255")
    if args.crop_top < 0 or args.crop_bottom < 0:
        parser.error("crop values cannot be negative")
    if args.fail_fraction is not None and not (0 <= args.fail_fraction <= 1):
        parser.error("fail-fraction must be 0..1")
    if not args.kotlin.is_dir() or not args.react.is_dir():
        parser.error("both --kotlin and --react must point to existing directories")
    raise SystemExit(audit(args.kotlin, args.react, args.output, args.tolerance,
                           args.crop_top, args.crop_bottom, args.fail_fraction))
