#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
completion = ROOT / "App/tools/patch_android_1_0_completion.py"
text = completion.read_text(encoding="utf-8")

start = text.index("def patch_main_screens(source: str) -> str:\n")
end = text.index("\ndef patch_settings(source: str) -> str:\n", start)
text = text[:start] + '''def patch_main_screens(source: str) -> str:
    # Player XP and Achievements belong to Settings > Profile, not Rewards.
    return source
''' + text[end:]

old_tail = '''    profile_badge = profile_anchor + \'''            if (PuppyReleaseMilestones.RELEASE_1_0_BADGE_ID in gameState.profileBadgeIds) {
                StatusLine("Badge", PuppyReleaseMilestones.RELEASE_1_0_BADGE_NAME)
            }
\'''
    source = replace_once(source, profile_anchor, profile_badge, "profile release badge")
    return source
'''
new_tail = '''    profile_badge = profile_anchor + \'''            if (PuppyReleaseMilestones.RELEASE_1_0_BADGE_ID in gameState.profileBadgeIds) {
                StatusLine("Badge", PuppyReleaseMilestones.RELEASE_1_0_BADGE_NAME)
            }
\'''
    source = replace_once(source, profile_anchor, profile_badge, "profile release badge")

    progression_anchor = \'''    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
\'''
    progression_ui = \'''    Spacer(Modifier.height(12.dp))
    PuppyPlayerProgressCard(gameState)
    Spacer(Modifier.height(10.dp))
    PuppyAchievementsSection(gameState)

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
\'''
    source = replace_once(source, progression_anchor, progression_ui, "profile progression section")
    if "import androidx.compose.material3.LinearProgressIndicator\\n" not in source:
        source = replace_once(
            source,
            "import androidx.compose.material3.HorizontalDivider\\n",
            "import androidx.compose.material3.HorizontalDivider\\nimport androidx.compose.material3.LinearProgressIndicator\\n",
            "profile progression import",
        )

    source += \'''\n
@Composable
private fun PuppyPlayerProgressCard(state: V6GameState) {
    val nextLevelXp = runCatching {
        val level = state.level.toLong().coerceAtLeast(1L)
        Math.multiplyExact(Math.multiplyExact(level, level), 100L)
    }.getOrDefault(Long.MAX_VALUE)
    val progressBase = if (state.level <= 1) 0L else {
        val previous = (state.level - 1).toLong()
        previous * previous * 100L
    }
    val span = (nextLevelXp - progressBase).coerceAtLeast(1L)
    val current = (state.playerXp - progressBase).coerceIn(0L, span)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Player Level ${state.level}", fontWeight = FontWeight.Black)
                Text("${state.playerXp} XP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { current.toFloat() / span.toFloat() },
                modifier = Modifier.fillMaxWidth().height(6.dp)
            )
            Text(
                if (nextLevelXp == Long.MAX_VALUE) "Maximum tracked level" else "$current / $span XP toward next level",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PuppyAchievementsSection(state: V6GameState) {
    val statuses = PuppyAchievementsV6.statuses(state, state.achievementRewardedIds)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Achievements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black)
                Text(
                    statuses.count { it.completed }.toString() + "/" + statuses.size,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(8.dp))
            statuses.forEachIndexed { index, status ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(status.definition.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(status.definition.title, fontWeight = FontWeight.Black)
                        Text(status.definition.description, style = MaterialTheme.typography.bodySmall)
                        Text(
                            status.progress.coerceAtMost(status.definition.target).toString() + "/" + status.definition.target,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (status.completed) "✓ Complete" else status.definition.rewardDescription,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (status.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
\'''
    return source
'''
if old_tail not in text:
    raise SystemExit("profile patch tail anchor not found")
text = text.replace(old_tail, new_tail, 1)
completion.write_text(text, encoding="utf-8")

# Remove this one-shot helper and its workflow from the committed result.
Path(__file__).unlink()
(ROOT / ".github/workflows/apply-profile-relocation-once.yml").unlink()
