#!/usr/bin/env python3
"""Integrate PupEye save-integrity and external-clicker protections into generated V6 sources."""
from pathlib import Path
import sys

PACKAGE = Path("com/harleytg/puppyclicker")


def replace_once(source: str, old: str, new: str, label: str) -> str:
    count = source.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected one integration anchor, found {count}")
    return source.replace(old, new, 1)


def patch_view_model(source: str) -> str:
    source = replace_once(
        source,
        "    private val recentTapTimes = ArrayDeque<Long>()\n",
        "    private val recentTapTimes = ArrayDeque<Long>()\n    private val automationDetector = PupEyeAutomationDetector()\n",
        "automation detector field",
    )
    if "        val suspiciousThisTap = enforcePupEyeFairPlay && looksAutomated(now)\n" in source:
        source = replace_once(
            source,
            "        val suspiciousThisTap = enforcePupEyeFairPlay && looksAutomated(now)\n",
            "        val suspiciousThisTap = enforcePupEyeFairPlay && (automationDetector.recordTapAndCheck() || looksAutomated(now))\n",
            "identity-aware automation detector tap hook",
        )
    else:
        source = replace_once(
            source,
            "        val suspiciousThisTap = looksAutomated(now)\n",
            "        val suspiciousThisTap = automationDetector.recordTapAndCheck() || looksAutomated(now)\n",
            "automation detector tap hook",
        )
    source = replace_once(
        source,
        "                recentTapTimes.clear()\n                suspicionHits = 0\n",
        "                recentTapTimes.clear()\n                automationDetector.reset()\n                suspicionHits = 0\n",
        "automation detector cooldown reset",
    )
    return source


def patch_activity(source: str) -> str:
    # Legacy V6 Settings rendered PupEye directly in PuppyClickerV6Activity.kt.
    # The routed PuppySettingsUi.kt now owns PupEye Protection and already uses
    # StreamedPupEyeBranding plus save/fair-play status. Preserve compatibility
    # with older generated sources without requiring those obsolete anchors.
    branding_anchor = '''                Text("🐶👁️ Pup Eye Fair Play", fontWeight = FontWeight.Black)'''
    if branding_anchor in source:
        source = replace_once(
            source,
            branding_anchor,
            '''                Row(verticalAlignment = Alignment.CenterVertically) {
                    StreamedPupEyeBranding(
                        modifier = Modifier.size(58.dp),
                        contentDescription = "PupEye Anti-Cheat"
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("PupEye Fair Play", fontWeight = FontWeight.Black)
                }''',
            "streamed PupEye Settings branding",
        )

    security_copy_anchor = '''                Text("Fast human tapping is allowed. Repeated machine-like timing and extreme sustained rates trigger a short cooldown.", style = MaterialTheme.typography.bodySmall)
                Text("Suspicious taps do not count toward ticket awards. No permanent bans or uploads.", style = MaterialTheme.typography.bodySmall)
                Text("Confirmed cooldowns: ${state.pupEyeStrikes}", style = MaterialTheme.typography.labelMedium)'''
    if security_copy_anchor in source:
        source = replace_once(
            source,
            security_copy_anchor,
            '''                Text("Fast human tapping is allowed. PupEye checks both short bursts and sustained machine-periodic timing used by Android auto-clickers/scripts.", style = MaterialTheme.typography.bodySmall)
                Text("Suspicious taps do not count toward ticket awards. Accessibility services are not blocked just for being enabled.", style = MaterialTheme.typography.bodySmall)
                Text("Encrypted saves use AES-GCM authentication; modified save ciphertext is rejected and quarantined.", style = MaterialTheme.typography.bodySmall)
                Text("Confirmed clicker cooldowns: ${state.pupEyeStrikes}", style = MaterialTheme.typography.labelMedium)
                Text("Save integrity events: ${PupEyeSaveGuard.state(context).tamperEvents}", style = MaterialTheme.typography.labelMedium)''',
            "PupEye settings security copy",
        )
    return source

def main(root: Path) -> None:
    view_model = root / PACKAGE / "PuppyClickerV6ViewModel.kt"
    activity = root / PACKAGE / "PuppyClickerV6Activity.kt"
    view_model.write_text(patch_view_model(view_model.read_text(encoding="utf-8")), encoding="utf-8")
    activity.write_text(patch_activity(activity.read_text(encoding="utf-8")), encoding="utf-8")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: patch_pupeye_security.py GENERATED_SOURCE_ROOT")
    main(Path(sys.argv[1]))
