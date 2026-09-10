#!/usr/bin/env python3
from pathlib import Path

activity_path = Path("app/src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt")
activity = activity_path.read_text(encoding="utf-8")
play_start = activity.index("@Composable\nprivate fun V6Play")
play_end = activity.index("\n@Composable\nprivate fun V6CareAndDaily", play_start)
play = activity[play_start:play_end]

old_timer = '''            delay(2_000)
            ticketVisible = false'''
if play.count(old_timer) != 1:
    raise SystemExit(f"ticket timer anchor count={play.count(old_timer)}")
play = play.replace(old_timer, '''            delay(2_750)
            ticketVisible = false''', 1)

old_banner = '''        AnimatedVisibility(ticketVisible, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut() + scaleOut(targetScale = 0.9f)) {
            val rarity = state.lastTicketDrop ?: TicketRarity.COMMON
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = rarityContainerV6(rarity)) {
                Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(rarity.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("${rarity.displayName} Upgrade Ticket!", fontWeight = FontWeight.Black)
                        Text("Added to Ticket Upgrades.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        if (ticketVisible) Spacer(Modifier.height(8.dp))

'''
if play.count(old_banner) != 1:
    raise SystemExit(f"old ticket banner anchor count={play.count(old_banner)}")
play = play.replace(old_banner, "", 1)

column_anchor = '''    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {'''
if play.count(column_anchor) != 1:
    raise SystemExit(f"Play Column anchor count={play.count(column_anchor)}")
play = play.replace(
    column_anchor,
    '''    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {''',
    1,
)

tail = '''        Spacer(Modifier.height(18.dp))
    }
}'''
if play.count(tail) != 1:
    raise SystemExit(f"Play tail anchor count={play.count(tail)}")
play = play.replace(
    tail,
    '''            Spacer(Modifier.height(18.dp))
        }

        V6TicketDropOverlay(
            visible = ticketVisible,
            rarity = state.lastTicketDrop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 142.dp, start = 24.dp, end = 24.dp)
        )
    }
}''',
    1,
)

activity = activity[:play_start] + play + activity[play_end:]
activity_path.write_text(activity, encoding="utf-8")

patch_path = Path("tools/patch_puppy_ux.py")
patch = patch_path.read_text(encoding="utf-8")
marker = '        "ticket overlay",\n'
marker_pos = patch.find(marker)
if marker_pos < 0:
    raise SystemExit("ticket overlay transform marker not found")
block_start = patch.rfind("    source = replace_once(\n", 0, marker_pos)
block_end = patch.find("    )\n\n", marker_pos)
if block_start < 0 or block_end < 0:
    raise SystemExit("ticket overlay transform boundaries not found")
block_end += len("    )\n\n")
patch = patch[:block_start] + patch[block_end:]
patch_path.write_text(patch, encoding="utf-8")

build_path = Path("app/build.gradle.kts")
build = build_path.read_text(encoding="utf-8")
dep = '    androidTestImplementation("androidx.compose.ui:ui-test-junit4")\n'
if dep not in build:
    anchor = '    androidTestImplementation("androidx.test.ext:junit:1.2.1")\n'
    if build.count(anchor) != 1:
        raise SystemExit(f"AndroidX JUnit dependency anchor count={build.count(anchor)}")
    build = build.replace(anchor, anchor + dep, 1)
build_path.write_text(build, encoding="utf-8")

print("Patched ticket host, generator transform, and Compose test dependency")
