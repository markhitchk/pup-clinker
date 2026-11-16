"""Read-only official Puppy Clicker roster, shared by Android, web and Discord.

Data comes from the versioned pup-clinker asset manifests, never user-controlled
messages or client-side ownership claims.
"""

from __future__ import annotations

import json
import os
import re
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import quote

ASSET_ROOT = Path(os.environ.get("PUPPY_ASSET_ROOT", Path(__file__).resolve().parents[1] / "assets"))
SITE_URL = "https://puppy-clicker.vercel.app"
RAW_URL = "https://raw.githubusercontent.com/markhitchk/pup-clinker/main/assets"
VALID_ASSET = re.compile(r"^[a-z0-9_]{1,64}$")
VALID_IMAGE = re.compile(r"^[a-z0-9_\-]{1,90}\.png$")


@dataclass(frozen=True)
class Puppy:
    number: int | None
    name: str
    asset_id: str
    generation: str
    filename: str
    description: str = ""
    unlock_source: str = ""

    @property
    def image_url(self) -> str:
        folder = "discord_rewards" if self.generation == "Discord Rewards" else self.generation.lower()
        return f"{RAW_URL}/{folder}/{self.filename}"

    @property
    def website_url(self) -> str:
        return f"{SITE_URL}/puppies?pup={quote(self.asset_id, safe='')}"

    @property
    def thread_name(self) -> str:
        if self.number is None:
            raise ValueError("Reward puppies are not numbered in the main forum roster")
        return f"#{self.number:02d} — {self.name}"


def load_roster(root: Path | None = None) -> tuple[Puppy, ...]:
    root = root or ASSET_ROOT
    puppies: list[Puppy] = []
    seen_ids: set[str] = set()
    seen_numbers: set[int] = set()
    for folder, generation in (("v1", "V1"), ("v2", "V2"), ("discord_rewards", "Discord Rewards")):
        data = json.loads((root / folder / "manifest.json").read_text(encoding="utf-8"))
        entries = data if isinstance(data, list) else data["puppies"]
        if not isinstance(entries, list) or len(entries) > 500:
            raise ValueError(f"Invalid {folder} roster")
        for entry in entries:
            asset_id = entry["asset_id"]
            filename = entry.get("filename", entry.get("file", ""))
            name = entry["name"]
            number = None if folder == "discord_rewards" else entry["number"]
            if not isinstance(asset_id, str) or not VALID_ASSET.fullmatch(asset_id):
                raise ValueError(f"Invalid asset ID in {folder}")
            if not isinstance(filename, str) or not VALID_IMAGE.fullmatch(filename):
                raise ValueError(f"Invalid image filename for {asset_id}")
            if not isinstance(name, str) or not 1 <= len(name) <= 80:
                raise ValueError(f"Invalid name for {asset_id}")
            if number is not None and (type(number) is not int or number < 1 or number > 999):
                raise ValueError(f"Invalid roster number for {asset_id}")
            if asset_id in seen_ids or (number is not None and number in seen_numbers):
                raise ValueError(f"Duplicate puppy in official manifests: {asset_id}")
            seen_ids.add(asset_id)
            if number is not None:
                seen_numbers.add(number)
            puppies.append(Puppy(
                number=number,
                name=name,
                asset_id=asset_id,
                generation=generation,
                filename=filename,
                description=str(entry.get("description", ""))[:500],
                unlock_source=str(entry.get("unlock_source", ""))[:120],
            ))
    return tuple(sorted(puppies, key=lambda p: (p.number is None, p.number or 0, p.name)))


def lookup_puppy(puppies: tuple[Puppy, ...], query: str) -> Puppy | None:
    needle = query.strip().casefold()
    if not needle:
        return None
    for pup in puppies:
        if needle == pup.asset_id.casefold() or (pup.number is not None and needle == f"#{pup.number}"):
            return pup
    named = [pup for pup in puppies if pup.name.casefold() == needle]
    return named[0] if len(named) == 1 else None
