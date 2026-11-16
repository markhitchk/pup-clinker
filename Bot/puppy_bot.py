#!/usr/bin/env python3
"""Guild-scoped Puppy Clicker slash-command bot; never handles Pup Account credentials."""

from __future__ import annotations

import asyncio
import logging
import os
import sys

import aiohttp
import discord
from discord import app_commands

from roster import SITE_URL, Puppy, load_roster, lookup_puppy

LOG = logging.getLogger("puppyclicker.bot")
GUILD_ID = int(os.getenv("PUPPY_GUILD_ID", "1547285473397837985"))
GUILD = discord.Object(id=GUILD_ID)


def puppy_embed(pup: Puppy) -> discord.Embed:
    title = f"🐾 {f'#{pup.number:02d} • ' if pup.number is not None else ''}{pup.name}"
    embed = discord.Embed(
        title=title,
        description=(pup.description or "Explore this puppy in the official Puppy Clicker roster."),
        url=pup.website_url,
        color=discord.Color.from_rgb(0, 196, 224),
    )
    embed.add_field(name="Collection", value=pup.generation, inline=True)
    embed.add_field(name="Asset ID", value=f"{pup.asset_id}", inline=True)
    if pup.unlock_source:
        embed.add_field(name="Listed source", value=pup.unlock_source, inline=False)
    embed.set_image(url=pup.image_url)
    embed.set_footer(text="Puppy Clicker • Artwork does not imply account ownership")
    return embed


class PuppyClickerBot(discord.Client):
    def __init__(self) -> None:
        intents = discord.Intents.none()
        intents.guilds = True
        super().__init__(intents=intents, allowed_mentions=discord.AllowedMentions.none())
        self.tree = app_commands.CommandTree(self)
        self.puppies = load_roster()

    async def setup_hook(self) -> None:
        commands = await self.tree.sync(guild=GUILD)
        LOG.info("Synced %s commands to guild %s", len(commands), GUILD_ID)

    async def on_ready(self) -> None:
        LOG.info("Logged in as %s (%s)", self.user, self.user.id if self.user else "unknown")


bot = PuppyClickerBot()


@bot.tree.command(name="puppy", description="Preview a puppy and open its official website page", guild=GUILD)
@app_commands.describe(name="Puppy name, roster number, or asset ID")
async def puppy(interaction: discord.Interaction, name: str) -> None:
    pup = lookup_puppy(bot.puppies, name)
    if pup is None:
        await interaction.response.send_message(
            "Puppy not found. Use /roster to see the official names and IDs.", ephemeral=True
        )
        return
    await interaction.response.send_message(embed=puppy_embed(pup))


@puppy.autocomplete("name")
async def puppy_autocomplete(interaction: discord.Interaction, current: str) -> list[app_commands.Choice[str]]:
    term = current.casefold().strip()
    matches = (p for p in bot.puppies if term in p.name.casefold() or term in p.asset_id.casefold())
    return [app_commands.Choice(name=f"{p.name} • {p.asset_id}"[:100], value=p.asset_id)
            for p in list(matches)[:25]]


@bot.tree.command(name="roster", description="Show the official V1, V2 and Discord reward puppies", guild=GUILD)
async def roster(interaction: discord.Interaction) -> None:
    embed = discord.Embed(title="🐶 Meet the Puppies", url=f"{SITE_URL}/play",
                          description="Official game roster • use /puppy for artwork and details.",
                          color=discord.Color.from_rgb(0, 196, 224))
    for generation in ("V1", "V2", "Discord Rewards"):
        items = [p for p in bot.puppies if p.generation == generation]
        rows = [f"#{p.number:02d} {p.name}" if p.number is not None else p.name for p in items]
        embed.add_field(name=f"{generation} ({len(items)})", value="\n".join(rows) or "None", inline=False)
    embed.set_footer(text="Inventory and unlocks are verified only by Pup Account/PupEye")
    await interaction.response.send_message(embed=embed)


@bot.tree.command(name="play", description="Open Puppy Clicker on the web", guild=GUILD)
async def play(interaction: discord.Interaction) -> None:
    await interaction.response.send_message(f"🐾 Play Puppy Clicker: {SITE_URL}/play")


@bot.tree.command(name="account", description="Open Pup Account and manage Discord linking", guild=GUILD)
async def account(interaction: discord.Interaction) -> None:
    await interaction.response.send_message(
        f"🔐 Manage your Pup Account: {SITE_URL}/account\n"
        "Sign in there to link or reconnect Discord. The bot never asks for passwords, "
        "verification codes, or account recovery tokens.", ephemeral=True
    )


@bot.tree.command(name="status", description="Check whether the Puppy Clicker website responds", guild=GUILD)
async def status(interaction: discord.Interaction) -> None:
    await interaction.response.defer(ephemeral=True, thinking=True)
    try:
        timeout = aiohttp.ClientTimeout(total=7)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.get(SITE_URL, allow_redirects=True, max_redirects=3) as result:
                ok = 200 <= result.status < 400
                detail = f"Website returned HTTP {result.status}."
    except (aiohttp.ClientError, asyncio.TimeoutError):
        ok = False
        detail = "Website could not be reached from the bot host."
    await interaction.followup.send(
        f"{'✅ Website reachable' if ok else '⚠️ Website check failed'} — {detail}\n"
        "This checks website connectivity, not live login, Supabase, PupEye, or gameplay health.",
        ephemeral=True
    )


@bot.tree.command(name="help", description="List Puppy Clicker bot commands", guild=GUILD)
async def help_command(interaction: discord.Interaction) -> None:
    embed = discord.Embed(
        title="🐾 Puppy Clicker Bot",
        description="A companion for the official website and Android game.",
        color=discord.Color.from_rgb(0, 196, 224),
    )
    embed.add_field(name="Commands", value=(
        "/puppy — puppy artwork and official page\n"
        "/roster — view the official puppy catalog\n"
        "/play — play on the website\n"
        "/account — manage Pup Account/Discord linking\n"
        "/status — check website reachability\n"
        "/help — this guide"
    ), inline=False)
    embed.add_field(name="Security", value=(
        "The bot never changes Treats, rewards, puppy ownership, bans, or cloud saves. "
        "Those actions require server-side Pup Account and PupEye validation."
    ), inline=False)
    embed.url = SITE_URL
    await interaction.response.send_message(embed=embed, ephemeral=True)


@bot.tree.error
async def on_app_error(interaction: discord.Interaction, error: app_commands.AppCommandError) -> None:
    LOG.exception("Slash command failed", exc_info=error)
    message = "⚠️ The command failed. Please try again later."
    if interaction.response.is_done():
        await interaction.followup.send(message, ephemeral=True)
    else:
        await interaction.response.send_message(message, ephemeral=True)


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    token = os.getenv("DISCORD_BOT_TOKEN", "").strip()
    if not token:
        print("DISCORD_BOT_TOKEN is missing. Store the bot token in your host's secret environment variables.", file=sys.stderr)
        return 2
    bot.run(token, log_handler=None)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
