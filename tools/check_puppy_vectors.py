#!/usr/bin/env python3
"""Validate the flat Android vector subset used by protected Puppy Clicker art.

This checks source integrity and renderer compatibility, not subjective art quality.
Run before encryption so malformed or unsupported artwork cannot ship silently.
"""
import argparse
import hashlib
import json
import math
import re
import sys
import tempfile
from pathlib import Path
from xml.etree import ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
V1 = "classic golden poodle spotty midnight cloud aurora cocoa snowball galaxy neon_buddy golden_night halloween santa birthday dev_pup secret_snoot classic_forever".split()
V2 = "v2_frost v2_honey v2_biscuit v2_onyx v2_domino v2_chestnut v2_prism v2_flurry".split()
ARITY = dict(zip("MmLlHhVvCcSsQqTtAaZz", [2,2,2,2,1,1,1,1,6,6,4,4,4,4,2,2,7,7,0,0]))
TOKEN = re.compile(r"[MmLlHhVvCcSsQqTtAaZz]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?")
COLOR = re.compile(r"#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?\Z")
ROOT_ATTRS = {"width", "height", "viewportWidth", "viewportHeight", "name"}
PATH_ATTRS = {"name", "pathData", "fillColor", "fillAlpha", "fillType", "strokeColor", "strokeAlpha", "strokeWidth", "strokeLineCap", "strokeLineJoin", "strokeMiterLimit"}


def fail(message):
    raise ValueError(message)


def attr(element, name, default=None):
    return element.get("{%s}%s" % (ANDROID, name), default)


def number(value, name, minimum=0, maximum=1e6, strict=False):
    try:
        result = float(value)
    except (TypeError, ValueError):
        fail("Invalid %s: %r" % (name, value))
    if not math.isfinite(result) or result > maximum or (result <= minimum if strict else result < minimum):
        fail("Out-of-range %s: %r" % (name, value))
    return result


def color(value):
    if value is None:
        return None
    if value.lower() in ("transparent", "@android:color/transparent"):
        return 0
    if not COLOR.fullmatch(value):
        fail("Unsupported color: %r" % value)
    return int(value[1:], 16) if len(value) == 9 else (0xff000000 | int(value[1:], 16))


def check_attrs(element, allowed):
    for key in element.attrib:
        if not key.startswith("{%s}" % ANDROID) or key.split("}", 1)[1] not in allowed:
            fail("Unsupported attribute: %s" % key)


def path_commands(data):
    tokens = []
    end = 0
    for match in TOKEN.finditer(data):
        if data[end:match.start()].strip(" \t\r\n,"):
            fail("Invalid pathData near %r" % data[end:match.start()][:32])
        tokens.append(match.group())
        end = match.end()
    if data[end:].strip(" \t\r\n,") or not tokens:
        fail("Invalid or empty pathData")
    i, command, count = 0, None, 0
    while i < len(tokens):
        if len(tokens[i]) == 1 and tokens[i] in ARITY:
            command = tokens[i]
            i += 1
        elif command is None:
            fail("Path coordinates without a command")
        arity = ARITY[command]
        if arity == 0:
            count += 1
            command = None
            continue
        groups = 0
        while i < len(tokens) and tokens[i] not in ARITY:
            if i + arity > len(tokens) or any(t in ARITY for t in tokens[i:i + arity]):
                fail("Incomplete %s path command" % command)
            values = [float(t) for t in tokens[i:i + arity]]
            if any(not math.isfinite(v) or abs(v) > 1e6 for v in values):
                fail("Non-finite or excessive path coordinate")
            if command in "Aa" and (values[0] < 0 or values[1] < 0 or values[3] not in (0, 1) or values[4] not in (0, 1)):
                fail("Invalid arc radii or flags")
            i += arity
            groups += 1
            count += 1
            if command == "M":
                command = "L"
            elif command == "m":
                command = "l"
        if groups == 0:
            fail("Path command without coordinates")
    return count


def validate(path):
    data = path.read_bytes()
    if len(data) > 2 * 1024 * 1024 or not data:
        fail("Missing, empty or oversized vector")
    if b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
        fail("DOCTYPE and entity declarations are prohibited")
    root = ET.fromstring(data)
    if root.tag != "vector":
        fail("Root must be an Android vector")
    check_attrs(root, ROOT_ATTRS)
    width = number(attr(root, "viewportWidth"), "viewportWidth", strict=True)
    height = number(attr(root, "viewportHeight"), "viewportHeight", strict=True)
    for dimension in ("width", "height"):
        value = attr(root, dimension)
        if not value or not value.endswith("dp"):
            fail("Invalid vector %s" % dimension)
        number(value[:-2], dimension, strict=True)
    paths, commands, visible = 0, 0, False
    for child in root:
        if child.tag != "path" or list(child):
            fail("Unsupported vector element or nested group/gradient")
        check_attrs(child, PATH_ATTRS)
        paths += 1
        commands += path_commands(attr(child, "pathData", ""))
        if attr(child, "fillType", "nonZero").lower() not in ("nonzero", "winding", "evenodd", "even_odd"):
            fail("Unsupported fillType")
        if attr(child, "strokeLineCap", "butt").lower() not in ("butt", "round", "square"):
            fail("Unsupported strokeLineCap")
        if attr(child, "strokeLineJoin", "miter").lower() not in ("miter", "round", "bevel"):
            fail("Unsupported strokeLineJoin")
        fill = color(attr(child, "fillColor"))
        stroke = color(attr(child, "strokeColor"))
        fa = number(attr(child, "fillAlpha", "1"), "fillAlpha", maximum=1)
        sa = number(attr(child, "strokeAlpha", "1"), "strokeAlpha", maximum=1)
        sw = number(attr(child, "strokeWidth", "0"), "strokeWidth")
        number(attr(child, "strokeMiterLimit", "4"), "strokeMiterLimit", strict=True)
        visible |= bool((fill is not None and fill >> 24 and fa > 0) or (stroke is not None and stroke >> 24 and sa > 0 and sw > 0))
    if not paths or paths > 8192 or not visible:
        fail("Empty, invisible or excessively complex vector")
    return {"file": path.name, "bytes": len(data), "viewport": [width, height], "paths": paths, "commands": commands, "sha256": hashlib.sha256(data).hexdigest()}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-dir", type=Path, default=None, help="Optional compiled Android XML directory; by default validate repository SVG sources")
    parser.add_argument("--json", type=Path, help="Write a machine-readable validation report")
    args = parser.parse_args()
    temporary = tempfile.TemporaryDirectory()
    if args.source_dir is None:
        from puppy_svg import compile_svg
        svg_dir = Path(__file__).resolve().parents[1] / "app/src/main/puppy-svg"
        args.source_dir = Path(temporary.name)
        for source in svg_dir.glob("*.svg"):
            (args.source_dir / (source.stem + ".xml")).write_bytes(compile_svg(source))
    expected = {"v1_" + name + ".xml" for name in V1} | {name + ".xml" for name in V2}
    actual = {p.name for p in args.source_dir.glob("v1_*.xml")} | {p.name for p in args.source_dir.glob("v2_*.xml")}
    errors, results = [], []
    for name in sorted(expected):
        try:
            results.append(validate(args.source_dir / name))
        except (OSError, ValueError, ET.ParseError) as error:
            errors.append("%s: %s" % (name, error))
    if expected != actual:
        errors.append("Roster mismatch: missing=%s unexpected=%s" % (sorted(expected - actual), sorted(actual - expected)))
    if len({r["sha256"] for r in results if r["file"].startswith("v1_")}) != 18:
        errors.append("V1 artwork must contain 18 distinct source files")
    report = {"ok": not errors, "v1_expected": 18, "v2_expected": 8, "assets": results, "errors": errors}
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    for error in errors:
        print("ERROR:", error, file=sys.stderr)
    print("Validated %d/%d puppy vectors (%d V1, %d V2)" % (len(results), len(expected), len(V1), len(V2)))
    print("Total source bytes:", sum(r["bytes"] for r in results))
    if not errors:
        print("PASS: source integrity and supported vector syntax")
    temporary.cleanup()
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())

