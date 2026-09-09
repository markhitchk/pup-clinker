#!/usr/bin/env python3
"""Patch the generated Android roster renderer to preserve PNG transparency."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one integration anchor, found {count}")
    return source.replace(old, new, 1)


def main(root: Path) -> None:
    target = root / PACKAGE / "StreamedPuppyArt.kt"
    source = target.read_text(encoding="utf-8")

    source = replace_once(
        source,
        "import androidx.compose.foundation.background\n",
        "",
        "background import",
    )
    source = replace_once(
        source,
        "import androidx.compose.ui.draw.clip\n",
        "",
        "clip import",
    )
    source = replace_once(
        source,
        "        Modifier.size(size).clip(CircleShape).background(background),\n",
        "        Modifier.size(size),\n",
        "transparent portrait container",
    )

    source = replace_once(
        source,
        "                decode(disk.readBytes())?.let { bitmap ->\n",
        "                decode(assetId, disk.readBytes())?.let { bitmap ->\n",
        "cached image decode",
    )
    source = replace_once(
        source,
        "            if (bytes.size > MAX_DOWNLOAD_BYTES) null else decode(bytes)?.also { memory.put(id, it) }\n",
        "            if (bytes.size > MAX_DOWNLOAD_BYTES) null else decode(id, bytes)?.also { memory.put(id, it) }\n",
        "bundled image decode",
    )
    source = replace_once(
        source,
        "                        val bitmap = decode(bytes) ?: throw IOException(\"Invalid PNG artwork: $id\")\n",
        "                        val bitmap = decode(id, bytes) ?: throw IOException(\"Invalid PNG artwork: $id\")\n",
        "downloaded image decode",
    )
    source = replace_once(
        source,
        "    private fun decode(bytes: ByteArray): Bitmap? {\n",
        "    private fun decode(assetId: String, bytes: ByteArray): Bitmap? {\n",
        "decode signature",
    )
    source = replace_once(
        source,
        '''        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        })
''',
        '''        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        return LegacyPuppyTransparency.apply(assetId, decoded)
''',
        "legacy matte cleanup",
    )

    target.write_text(source, encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_roster_transparency.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
