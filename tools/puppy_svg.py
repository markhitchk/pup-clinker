#!/usr/bin/env python3
"""Compile the repository's flat SVG artwork for the existing Android renderer.

No rasterization, tracing, path simplification, or character generation occurs.
Only the solid-color path subset actually used by this roster is supported.
"""
import argparse
import re
from pathlib import Path
from xml.etree import ElementTree as ET

SVG = 'http://www.w3.org/2000/svg'
ANDROID = 'http://schemas.android.com/apk/res/android'
MAPPING = {
    'd': 'pathData', 'fill': 'fillColor', 'stroke': 'strokeColor',
    'fill-opacity': 'fillAlpha', 'stroke-opacity': 'strokeAlpha',
    'stroke-width': 'strokeWidth', 'stroke-linecap': 'strokeLineCap',
    'stroke-linejoin': 'strokeLineJoin', 'stroke-miterlimit': 'strokeMiterLimit',
    'fill-rule': 'fillType',
}


def compile_svg(source):
    data = source.read_bytes()
    if len(data) > 2 * 1024 * 1024 or b'<!DOCTYPE' in data.upper() or b'<!ENTITY' in data.upper():
        raise ValueError(f'{source}: unsafe or oversized SVG')
    root = ET.fromstring(data)
    if root.tag != f'{{{SVG}}}svg' or set(root.attrib) - {'width', 'height', 'viewBox'}:
        raise ValueError(f'{source}: unsupported SVG root')
    view = root.attrib['viewBox'].split()
    if len(view) != 4 or view[:2] != ['0', '0']:
        raise ValueError(f'{source}: expected a zero-origin viewBox')
    vector = ET.Element('vector', {f'{{{ANDROID}}}'+k: v for k, v in {
        'width': root.attrib['width'] + 'dp', 'height': root.attrib['height'] + 'dp',
        'viewportWidth': view[2], 'viewportHeight': view[3],
    }.items()})
    for child in root:
        if child.tag != f'{{{SVG}}}path' or list(child) or set(child.attrib) - MAPPING.keys():
            raise ValueError(f'{source}: unsupported SVG element/attribute')
        attrs = {}
        for key, value in child.attrib.items():
            if key in ('fill', 'stroke'):
                if value == 'none':
                    value = '@android:color/transparent'
                elif not re.fullmatch(r'#[0-9a-fA-F]{6}', value):
                    raise ValueError(f'{source}: expected #RRGGBB or none')
            elif key == 'fill-rule':
                value = {'evenodd': 'evenOdd', 'nonzero': 'nonZero'}[value]
            attrs[f'{{{ANDROID}}}'+MAPPING[key]] = value
        # SVG defaults to black fill; VectorDrawable defaults to transparent.
        attrs.setdefault(f'{{{ANDROID}}}fillColor', '#000000')
        ET.SubElement(vector, 'path', attrs)
    ET.register_namespace('android', ANDROID)
    ET.indent(vector, space='    ')
    return ET.tostring(vector, encoding='utf-8', xml_declaration=True) + b'\n'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-dir', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    sources = sorted(args.source_dir.glob('*.svg'))
    if not sources:
        raise ValueError('No SVG sources found')
    # Validate every result before changing the output directory.
    from check_puppy_vectors import validate
    import tempfile
    compiled = {p.stem + '.xml': compile_svg(p) for p in sources}
    with tempfile.TemporaryDirectory() as temp:
        for name, data in compiled.items():
            candidate = Path(temp) / name
            candidate.write_bytes(data)
            validate(candidate)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for stale in args.output_dir.glob('*.xml'):
        if stale.name not in compiled:
            stale.unlink()
    for name, data in compiled.items():
        (args.output_dir / name).write_bytes(data)
    print(f'Compiled {len(compiled)} SVG assets without changing path geometry')


if __name__ == '__main__':
    main()
