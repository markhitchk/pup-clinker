#!/usr/bin/env python3
"""
Puppy Clicker — one-shot Discord forum roster poster.

Target:
  Guild: 1547285473397837985
  Forum channel: 1548116554674085948

Modes:
  verify  -> connect, verify the target/forum permissions, print what would post, then exit
  post    -> create one forum post per remaining puppy, skip matching existing posts, then exit

Security:
  Set DISCORD_BOT_TOKEN in your environment. Never paste the token into this file.
"""

from __future__ import annotations

import asyncio
import os
from dataclasses import dataclass

import discord

GUILD_ID = 1547285473397837985
FORUM_CHANNEL_ID = 1548116554674085948

# Defaults to #3 because Buddy (#1) and Sunny (#2) were already handled.
START_AT = int(os.getenv("PUPPY_START_AT", "3"))
END_AT = int(os.getenv("PUPPY_END_AT", "27"))

MODE = os.getenv("PUPPY_MODE", "verify").strip().lower()
FORUM_TAG_NAME = os.getenv("PUPPY_FORUM_TAG", "").strip() or None
DELAY_SECONDS = float(os.getenv("PUPPY_POST_DELAY", "1.25"))

REPO_RAW = "https://raw.githubusercontent.com/markhitchk/pup-clinker/main"


@dataclass(frozen=True)
class Puppy:
    number: int
    name: str
    asset_id: str
    description: str
    generation: str

    @property
    def filename(self) -> str:
        return f"{self.asset_id}.png"

    @property
    def image_url(self) -> str:
        generation_folder = self.generation.lower()
        return f"{REPO_RAW}/assets/{generation_folder}/{self.filename}"

    @property
    def thread_name(self) -> str:
        return f"#{self.number:02d} — {self.name}"


PUPPIES: tuple[Puppy, ...] = (
    Puppy(1, "Buddy", "v1_classic", "The original Puppy Clicker pup.", "V1"),
    Puppy(2, "Sunny", "v1_golden", "The original pup with a sunny flower theme.", "V1"),
    Puppy(3, "Mochi", "v1_poodle", "The original pup with a soft pink bow theme.", "V1"),
    Puppy(4, "Pepper", "v1_spotty", "The original pup with a playful dark-bandana theme.", "V1"),
    Puppy(5, "Midnight", "v1_midnight", "A moonlit version of the original pup.", "V1"),
    Puppy(6, "Cloud", "v1_cloud", "A dreamy cloud version of the original pup.", "V1"),
    Puppy(7, "Aurora", "v1_aurora", "Aurora-purple and teal fur glow.", "V1"),
    Puppy(8, "Cocoa", "v1_cocoa", "Warm cocoa-brown fur with cream highlights.", "V1"),
    Puppy(9, "Snowball", "v1_snowball", "Snow-white fur with an icy blue look.", "V1"),
    Puppy(10, "Galaxy", "v1_galaxy", "Deep violet galaxy fur with a cosmic glow.", "V1"),
    Puppy(11, "Neon Buddy", "v1_neon_buddy", "Electric cyan-and-purple neon Buddy.", "V1"),
    Puppy(12, "Golden Night", "v1_golden_night", "Dark midnight fur with a rich gold treatment.", "V1"),
    Puppy(13, "Pumpkin Pup", "v1_halloween", "Limited Halloween orange-and-purple puppy.", "V1"),
    Puppy(14, "Santa Paws", "v1_santa", "Limited Christmas red-and-snow puppy.", "V1"),
    Puppy(15, "Birthday Buddy", "v1_birthday", "Birthday party puppy with a bright celebration look.", "V1"),
    Puppy(16, "Dev Pup", "v1_dev_pup", "Developer-themed Puppy Clicker variant.", "V1"),
    Puppy(17, "Secret Snoot", "v1_secret_snoot", "A hidden mystery puppy for code hunters.", "V1"),
    Puppy(18, "Classic Forever", "v1_classic_forever", "A special tribute variant of the original Puppy Clicker pup.", "V1"),
    Puppy(19, "Frost", "v2_frost", "V2 fluffy white puppy with bright pink ears.", "V2"),
    Puppy(20, "Honey", "v2_honey", "V2 happy golden puppy with a cream chest and tail tip.", "V2"),
    Puppy(21, "Biscuit", "v2_biscuit", "V2 curly apricot puppy with cream curls.", "V2"),
    Puppy(22, "Onyx", "v2_onyx", "V2 dark navy puppy with warm cream markings.", "V2"),
    Puppy(23, "Domino", "v2_domino", "V2 spotted Dalmatian puppy with a bold eye patch.", "V2"),
    Puppy(24, "Chestnut", "v2_chestnut", "V2 cocoa-brown puppy with cream muzzle and paws.", "V2"),
    Puppy(25, "Prism", "v2_prism", "V2 pastel purple, teal and cream fluffy puppy.", "V2"),
    Puppy(26, "Flurry", "v2_flurry", "V2 fluffy snow-white puppy with a curled tail.", "V2"),
    Puppy(27, "HarleyTG Puppy", "v2_harleytg", "Fluffy golden-cream puppy with cyan-blue eyes and a raised waving paw.", "V2"),
)


def selected_puppies() -> list[Puppy]:
    if START_AT < 1 or END_AT < START_AT:
        raise ValueError(f"Invalid range: {START_AT}..{END_AT}")
    return [p for p in PUPPIES if START_AT <= p.number <= END_AT]


def make_embed(puppy: Puppy) -> discord.Embed:
    embed = discord.Embed(
        title=f"🐾 Meet #{puppy.number:02d} — {puppy.name}",
        description=puppy.description,
        colour=discord.Colour.from_rgb(0, 184, 240),
    )
    embed.add_field(name="Roster", value=puppy.generation, inline=True)
    embed.add_field(name="Asset ID", value=f"`{puppy.asset_id}`", inline=True)
    embed.set_image(url=puppy.image_url)
    embed.set_footer(text="Puppy Clicker • Meet the Puppies")
    return embed


async def get_existing_thread_names(channel: discord.ForumChannel) -> set[str]:
    names = {thread.name.casefold() for thread in channel.threads}

    try:
        async for thread in channel.archived_threads(limit=None):
            names.add(thread.name.casefold())
    except discord.Forbidden:
        print("⚠️  Could not read archived threads; duplicate checking will use active posts only.")
    except discord.HTTPException as exc:
        print(f"⚠️  Could not read archived threads: {exc}")

    return names


def resolve_tag(channel: discord.ForumChannel) -> list[discord.ForumTag]:
    if not FORUM_TAG_NAME:
        return []

    wanted = FORUM_TAG_NAME.casefold()
    for tag in channel.available_tags:
        if tag.name.casefold() == wanted:
            return [tag]

    available = ", ".join(tag.name for tag in channel.available_tags) or "(none)"
    raise RuntimeError(
        f'Forum tag "{FORUM_TAG_NAME}" was not found. Available tags: {available}'
    )


def print_permission_report(channel: discord.ForumChannel) -> None:
    me = channel.guild.me
    if me is None:
        print("⚠️  Could not resolve the bot's guild member object.")
        return

    perms = channel.permissions_for(me)
    checks = {
        "view_channel": perms.view_channel,
        "send_messages": perms.send_messages,
        "create_public_threads": perms.create_public_threads,
        "send_messages_in_threads": perms.send_messages_in_threads,
        "embed_links": perms.embed_links,
    }

    print("\nPermissions:")
    for name, allowed in checks.items():
        print(f"  {'✅' if allowed else '❌'} {name}")

    required = (
        checks["view_channel"]
        and checks["send_messages"]
        and checks["create_public_threads"]
        and checks["embed_links"]
    )
    if not required:
        raise RuntimeError("The bot is missing one or more permissions required to create forum posts.")


class OneShotForumBot(discord.Client):
    def __init__(self) -> None:
        intents = discord.Intents.none()
        intents.guilds = True
        super().__init__(intents=intents)

    async def on_ready(self) -> None:
        print(f"✅ Logged in as {self.user} ({self.user.id if self.user else 'unknown'})")
        try:
            await self.run_once()
        except Exception as exc:
            print(f"\n❌ {type(exc).__name__}: {exc}")
            await self.close()
            raise

    async def run_once(self) -> None:
        guild = self.get_guild(GUILD_ID)
        if guild is None:
            raise RuntimeError(
                f"Bot cannot see guild {GUILD_ID}. Make sure the bot is installed in that server."
            )

        channel = guild.get_channel(FORUM_CHANNEL_ID)
        if channel is None:
            try:
                channel = await self.fetch_channel(FORUM_CHANNEL_ID)
            except discord.NotFound as exc:
                raise RuntimeError(f"Channel {FORUM_CHANNEL_ID} was not found.") from exc

        if not isinstance(channel, discord.ForumChannel):
            raise RuntimeError(
                f"Channel {FORUM_CHANNEL_ID} is {type(channel).__name__}, not a Discord Forum channel."
            )

        print(f"✅ Server: {guild.name} ({guild.id})")
        print(f"✅ Forum:  {channel.name} ({channel.id})")
        print_permission_report(channel)

        tags = resolve_tag(channel)
        if FORUM_TAG_NAME:
            print(f"✅ Forum tag: {FORUM_TAG_NAME}")

        require_tag = bool(getattr(getattr(channel, "flags", None), "require_tag", False))
        if require_tag and not tags:
            available = ", ".join(tag.name for tag in channel.available_tags) or "(none)"
            raise RuntimeError(
                "This forum requires a tag. Set PUPPY_FORUM_TAG to one of: " + available
            )

        puppies = selected_puppies()
        print(f"\nSelected roster range: #{START_AT:02d}–#{END_AT:02d} ({len(puppies)} puppies)")

        if MODE == "verify":
            print("\nVERIFY MODE — no Discord posts will be created.")
            for puppy in puppies:
                print(f"  • {puppy.thread_name} [{puppy.asset_id}]")
            print("\n✅ Verification complete. Set PUPPY_MODE=post to publish.")
            await self.close()
            return

        if MODE != "post":
            raise RuntimeError('PUPPY_MODE must be either "verify" or "post".')

        existing = await get_existing_thread_names(channel)
        created = 0
        skipped = 0
        failed = 0

        for puppy in puppies:
            if puppy.thread_name.casefold() in existing:
                print(f"⏭️  Skip existing: {puppy.thread_name}")
                skipped += 1
                continue

            try:
                await channel.create_thread(
                    name=puppy.thread_name,
                    content=(
                        f"**Meet {puppy.name}!** 🐾\n"
                        f"Puppy Clicker roster #{puppy.number:02d} • {puppy.generation}"
                    ),
                    embed=make_embed(puppy),
                    applied_tags=tags,
                    reason="Puppy Clicker Meet the Puppies roster import",
                )
                print(f"✅ Posted: {puppy.thread_name}")
                existing.add(puppy.thread_name.casefold())
                created += 1
            except discord.Forbidden:
                print(f"❌ Forbidden while posting: {puppy.thread_name}")
                failed += 1
                break
            except discord.HTTPException as exc:
                print(f"❌ Discord error for {puppy.thread_name}: {exc}")
                failed += 1

            await asyncio.sleep(DELAY_SECONDS)

        print(f"\nFinished — created: {created}, skipped: {skipped}, failed: {failed}")
        await self.close()


def main() -> int:
    token = os.getenv("DISCORD_BOT_TOKEN", "").strip()

    if not token:
        print(
            "DISCORD_BOT_TOKEN is not set.\n"
            "Set a freshly reset Discord bot token in your environment; "
            "do not hard-code it into this script."
        )
        return 2

    if MODE not in {"verify", "post"}:
        print('PUPPY_MODE must be "verify" or "post".')
        return 2

    client = OneShotForumBot()
    try:
        client.run(token, log_handler=None)
    except discord.LoginFailure:
        print("❌ Discord rejected the token. Reset it in the Developer Portal and try again.")
        return 3
    except KeyboardInterrupt:
        print("\nStopped.")
        return 130
    except Exception:
        return 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
