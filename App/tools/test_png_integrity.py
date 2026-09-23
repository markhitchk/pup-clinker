#!/usr/bin/env python3
"""Fail CI when an Android drawable PNG is truncated or has a bad chunk CRC."""

from __future__ import annotations

import struct
import unittest
import zlib
from pathlib import Path

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
DRAWABLE_DIR = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "res" / "drawable"


def validate_png(path: Path) -> None:
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        raise AssertionError(f"{path}: invalid PNG signature")

    offset = len(PNG_SIGNATURE)
    saw_ihdr = False
    saw_iend = False

    while offset < len(data):
        if offset + 12 > len(data):
            raise AssertionError(f"{path}: truncated PNG chunk header at byte {offset}")

        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_data_start = offset + 8
        chunk_data_end = chunk_data_start + length
        crc_end = chunk_data_end + 4

        if crc_end > len(data):
            name = chunk_type.decode("latin1", errors="replace")
            raise AssertionError(
                f"{path}: truncated {name} chunk; expected {length} data bytes"
            )

        chunk_data = data[chunk_data_start:chunk_data_end]
        expected_crc = struct.unpack(">I", data[chunk_data_end:crc_end])[0]
        actual_crc = zlib.crc32(chunk_type)
        actual_crc = zlib.crc32(chunk_data, actual_crc) & 0xFFFFFFFF
        if actual_crc != expected_crc:
            name = chunk_type.decode("latin1", errors="replace")
            raise AssertionError(
                f"{path}: bad {name} CRC "
                f"(expected {expected_crc:08x}, got {actual_crc:08x})"
            )

        if chunk_type == b"IHDR":
            if saw_ihdr or offset != len(PNG_SIGNATURE):
                raise AssertionError(f"{path}: malformed IHDR placement")
            if length != 13:
                raise AssertionError(f"{path}: IHDR must be 13 bytes")
            saw_ihdr = True

        if chunk_type == b"IEND":
            if length != 0:
                raise AssertionError(f"{path}: IEND must be empty")
            saw_iend = True
            offset = crc_end
            break

        offset = crc_end

    if not saw_ihdr:
        raise AssertionError(f"{path}: missing IHDR")
    if not saw_iend:
        raise AssertionError(f"{path}: missing IEND")
    if offset != len(data):
        raise AssertionError(f"{path}: unexpected bytes after IEND")


class PngIntegrityTest(unittest.TestCase):
    def test_android_drawable_pngs_are_structurally_valid(self) -> None:
        pngs = sorted(DRAWABLE_DIR.glob("*.png"))
        self.assertTrue(pngs, f"No PNG resources found in {DRAWABLE_DIR}")

        failures: list[str] = []
        for path in pngs:
            try:
                validate_png(path)
            except AssertionError as error:
                failures.append(str(error))

        if failures:
            self.fail("\n".join(failures))


if __name__ == "__main__":
    unittest.main()
