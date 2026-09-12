# Puppy Clicker Discord Forum Bot

One-shot Discord forum poster for the **Meet the Puppies** roster.

## Target

- Guild: `1547285473397837985`
- Forum channel: `1548116554674085948`
- Default roster range: **#03 Mochi through #27 HarleyTG Puppy**

The script checks existing active and archived forum posts and skips matching thread names.

## Security

Do **not** commit a Discord bot token to this repository.

Set a freshly generated token through the `DISCORD_BOT_TOKEN` environment variable.

## Install

```bash
python -m pip install -r Bot/requirements.txt
```

## Verify without posting

```bash
export DISCORD_BOT_TOKEN="YOUR_NEW_TOKEN"
export PUPPY_MODE="verify"
python Bot/discord_puppy_forum_poster.py
```

## One-time posting run

```bash
export PUPPY_MODE="post"
python Bot/discord_puppy_forum_poster.py
```

The bot automatically disconnects after the run.

## Optional settings

- `PUPPY_START_AT=3`
- `PUPPY_END_AT=27`
- `PUPPY_FORUM_TAG="Meet the Puppies"`
- `PUPPY_POST_DELAY=1.25`
