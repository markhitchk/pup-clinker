# Puppy Clicker Discord Bot 🐾

A guild-scoped Discord companion for the official [Puppy Clicker website](https://puppy-clicker.vercel.app) and React Native Android game. It uses the **same versioned puppy asset manifests** as the source game and does **not** replace Pup Account/PupEye authentication, cloud saves, or game-economy authority.

## Commands

| Command | Behavior |
| --- | --- |
| /puppy | Official artwork and link to /puppies?pup=<asset_id>; autocomplete supports the catalog |
| /roster | All V1, V2 and Discord Rewards puppies from source manifests |
| /play | Website game link |
| /account | Private account/Discord linking instructions |
| /status | Read-only website connectivity check (not a database/gameplay health check) |
| /help | Command help and security notes |

The bot does not request login credentials, grant rewards, read private player progress or infer ownership from public puppy metadata. Registered Pup Account linking continues through the website's official Discord OAuth flow. Android shares the verified account system; there is no separate "bot account."

## Requirements

- Python **3.11+** and a checkout of markhitchk/pup-clinker, including the assets folder.
- A Discord application/bot installed in the Puppy Clicker server with **bot** and **applications.commands** scopes. Privileged Message Content and Guild Members intents are **not** required.
- Environment variable DISCORD_BOT_TOKEN in your host's secret manager. **Never commit the token.**

Run from the repo root:

    python -m pip install -r Bot/requirements.txt
    export DISCORD_BOT_TOKEN="YOUR_BOT_TOKEN"
    python Bot.py

Or run python Bot/puppy_bot.py directly. The bot stays online and syncs slash commands only to the configured server. It needs a **long-running Python host** (VPS, container or worker), **not** a Vercel serverless function.

Optional variables:

- PUPPY_GUILD_ID=1547285473397837985 — existing Puppy Clicker server
- PUPPY_ASSET_ROOT=/absolute/path/to/assets — overrides the default repository assets folder

Use the correct Puppy Clicker Discord application, not another project's Discord app. If the commands do not appear, verify the invite scopes and guild ID. Command sync happens at startup.

## Maintain the Meet the Puppies forum

The existing one-time forum poster remains available for channel 1548116554674085948 in guild 1547285473397837985. It includes #28 Dev Pup and #29 Rory, and new posts link to the website's corresponding puppy page.

    # Verify without posting:
    PUPPY_MODE=verify python Bot.py

    # Explicitly create posts absent from the forum:
    PUPPY_MODE=post python Bot.py

The first two puppies were posted earlier, so the default import range remains #03–#29. Other options: PUPPY_START_AT=3, PUPPY_END_AT=29, PUPPY_FORUM_TAG="Meet the Puppies", PUPPY_POST_DELAY=1.25.

**Forum posting is never triggered by the always-on slash bot.** Run the posting script deliberately. The poster fails closed if archived threads cannot be read, to avoid accidental duplicates.

## Offline checks

    python -m unittest discover -s Bot/tests -v
    python -m compileall -q Bot

## Website/Android integration boundary

The bot links players to https://puppy-clicker.vercel.app/play, /account, and canonical /puppies?pup=... pages. The roster loads from assets/v1/manifest.json, assets/v2/manifest.json and assets/discord_rewards/manifest.json in the pup-clinker repository.

This is a **read-only public companion bot**. Future cross-platform profile lookup, rewards, moderation and account-specific notifications require a dedicated authenticated, rate-limited backend with verified Discord-to-Pup Account association, proper access controls, and audited server-side actions. Do **not** put a Supabase service-role key into the Discord bot or award puppies directly from slash commands.
