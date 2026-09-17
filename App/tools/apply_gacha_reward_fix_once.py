#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected 1 match, found {count}")
    return text.replace(old, new, 1)

# 1) Gacha: no fallback to already-owned puppies, disable purchases when collection is complete.
gacha_path = ROOT / "App/app/src/main/java/com/harleytg/puppyclicker/PuppyGacha.kt"
gacha = gacha_path.read_text(encoding="utf-8")
gacha = replace_once(
    gacha,
    '''    fun pullPool(
        styles: List<PuppyStyle>,
        unlocked: Set<String>
    ): List<PuppyStyle> {
        val unowned = eligiblePuppies(styles, unlocked)
        return if (unowned.isNotEmpty()) {
            unowned
        } else {
            allEligiblePuppies(styles).filter { it.id in unlocked }
        }
    }
''',
    '''    fun pullPool(
        styles: List<PuppyStyle>,
        unlocked: Set<String>
    ): List<PuppyStyle> = eligiblePuppies(styles, unlocked)
''',
    "gacha pull pool",
)
gacha = replace_once(
    gacha,
    '                    "Collection complete · future pulls reveal an owned eligible puppy."',
    '                    "Collection complete · no unowned Gacha puppies remain."',
    "gacha complete copy",
)
count = gacha.count("enabled = eligible.isNotEmpty() &&")
if count != 2:
    raise SystemExit(f"gacha button eligibility: expected 2 matches, found {count}")
gacha = gacha.replace("enabled = eligible.isNotEmpty() &&", "enabled = remaining.isNotEmpty() &&")
gacha_path.write_text(gacha, encoding="utf-8")

# 2) Monthly Rewards: pure helper for first unclaimed goal.
monthly_path = ROOT / "App/app/src/main/java/com/harleytg/puppyclicker/PuppyMonthlyRewards.kt"
monthly = monthly_path.read_text(encoding="utf-8")
monthly = replace_once(
    monthly,
    '''    fun goalToday(id: String, today: LocalDate = LocalDate.now()): PuppyRewardGoal? =
        currentGoals(today).firstOrNull { it.id == id }
''',
    '''    fun nextUnclaimedGoal(
        goals: List<PuppyRewardGoal>,
        claimedIds: Set<String>
    ): PuppyRewardGoal? = goals.firstOrNull { it.id !in claimedIds }

    fun goalToday(id: String, today: LocalDate = LocalDate.now()): PuppyRewardGoal? =
        currentGoals(today).firstOrNull { it.id == id }
''',
    "next unclaimed reward helper",
)
monthly_path.write_text(monthly, encoding="utf-8")

# 3) Play: consume the current streamed/fallback daily goals instead of a hard-coded 75/300 card.
play_path = ROOT / "App/app/src/main/java/com/harleytg/puppyclicker/PuppyMainScreensRevamp.kt"
play = play_path.read_text(encoding="utf-8")
play = replace_once(
    play,
    '''    val tapScale = remember { Animatable(1f) }
    val tapRotation = remember { Animatable(0f) }
    val tapLift = remember { Animatable(0f) }
''',
    '''    val tapScale = remember { Animatable(1f) }
    val tapRotation = remember { Animatable(0f) }
    val tapLift = remember { Animatable(0f) }
    val rewardSchedule by PuppyMonthlyRewards.schedule.collectAsState()
    val today = LocalDate.now()
    val todayGoals = remember(rewardSchedule, today) {
        PuppyMonthlyRewards.currentGoals(today)
    }
    val nextRewardGoal = PuppyMonthlyRewards.nextUnclaimedGoal(todayGoals, state.claimedDailyTasks)
''',
    "play reward state",
)
play = replace_once(
    play,
    '''            Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))
            val target = 75L
            val progressNow = state.dailyTaps.coerceAtMost(target)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = if (tiny) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🎁", fontSize = 22.sp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Next Reward", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                            Text("$progressNow/$target", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                        }
                        LinearProgressIndicator(
                            progress = { progressNow.toFloat() / target.toFloat() },
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("300 🍪", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
''',
    '''            nextRewardGoal?.let { goal ->
                Spacer(Modifier.height(if (tiny) 5.dp else 8.dp))
                val target = goal.target.coerceAtLeast(1L)
                val progressNow = goal.progress(state).coerceIn(0L, target)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = if (tiny) 6.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(goal.emoji.ifBlank { "🎁" }, fontSize = 22.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(goal.title, fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelLarge)
                                Text("$progressNow/$target", fontWeight = FontWeight.Black, style = MaterialTheme.typography.labelMedium)
                            }
                            LinearProgressIndicator(
                                progress = { progressNow.toFloat() / target.toFloat() },
                                modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${goal.rewardTreats} 🍪", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
''',
    "dynamic play next reward",
)
play_path.write_text(play, encoding="utf-8")

# Remove this one-shot transformer and workflow from the final branch tree.
Path(__file__).unlink()
(ROOT / ".github/workflows/apply-gacha-reward-fix-once.yml").unlink()
