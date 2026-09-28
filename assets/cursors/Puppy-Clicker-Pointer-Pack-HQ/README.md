# Puppy Clicker Pointer Pack HQ

High-quality Puppy Clicker cursor artwork stored as a dedicated source folder in the repository.

## Contents

- **24 transparent HQ PNG masters**
- **12 Light-theme states**
- **12 Dark-theme states**
- Cursor pack design-board preview
- Hotspot metadata
- Export helper for 64 / 48 / 32 / 24 / 16 px derivatives

## States

`default`, `hover`, `click`, `text_select`, `link`, `grab`,
`grabbing`, `not_allowed`, `working`, `precision`, `alternate`, `secondary`

## Folder layout

```text
Puppy-Clicker-Pointer-Pack-HQ/
├── masters/
│   ├── light/
│   └── dark/
├── preview/
│   └── Puppy-Clicker-Cursor-Pack-Board.png
├── README.md
├── manifest.json
├── hotspots.json
├── validation.json
└── export_sizes.py
```

## Export sizes

Run:

```bash
python export_sizes.py
```

The script generates transparent PNGs at **64, 48, 32, 24, and 16 px** while preserving the Light/Dark and state structure.

The canonical 64 px hotspot is **(16, 16)** and smaller outputs scale proportionally.

## Runtime safety

This folder does **not** overwrite the existing `assets/cursors/` SVG source assets or Android runtime pointer resource. It is a separate HQ pack that can be integrated selectively.
