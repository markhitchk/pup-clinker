// Add canonical roster artwork, dynamic manifest rosters, seasonal features and final UI integration.
apply(from = rootProject.file("tools/canonical-puppy-assets.gradle.kts"))
apply(from = rootProject.file("tools/dynamic-puppy-roster.gradle.kts"))

// Puppy Exchange uses WebRTC DataChannels only. Camera/microphone permissions are not requested.
dependencies {
    add("implementation", "io.github.webrtc-sdk:android:150.7871.01")
}

// Original game sources, shared artwork and other platform projects are untouched.
tasks.named("generateProtectedPuppySources").configure {
    val rosterRevampPatch = rootProject.file("tools/patch_roster_revamp.py")
    val seasonalPatch = rootProject.file("tools/patch_seasonal_events.py")
    val transparencyPatch = rootProject.file("tools/patch_roster_transparency.py")
    val uxPatch = rootProject.file("tools/patch_puppy_ux.py")
    val pupEyeSecurityPatch = rootProject.file("tools/patch_pupeye_security.py")
    val importReloadPatch = rootProject.file("tools/patch_import_reload.py")
    val settingsSetupCorePatch = rootProject.file("tools/patch_settings_setup_revamp.py")
    val settingsSetupPatch = rootProject.file("tools/patch_settings_setup_revamp_runner.py")
    val developerConsolePatch = rootProject.file("tools/patch_developer_console.py")
    val rosterNavigationPatch = rootProject.file("tools/patch_roster_navigation.py")
    val puppyExchangePatch = rootProject.file("tools/patch_puppy_exchange.py")
    val compactRosterSettingsPatch = rootProject.file("tools/patch_compact_roster_settings.py")
    val puppyCodeV2Patch = rootProject.file("tools/patch_puppy_codes_v2.py")
    val mainUiRevampPatch = rootProject.file("tools/patch_main_ui_revamp.py")
    inputs.files(
        rosterRevampPatch,
        seasonalPatch,
        transparencyPatch,
        uxPatch,
        pupEyeSecurityPatch,
        importReloadPatch,
        settingsSetupCorePatch,
        settingsSetupPatch,
        developerConsolePatch,
        rosterNavigationPatch,
        puppyExchangePatch,
        compactRosterSettingsPatch,
        puppyCodeV2Patch,
        mainUiRevampPatch
    )
    doLast {
        val generatedSourceRoot = layout.buildDirectory
            .dir("generated/protected-puppies/source")
            .get()
            .asFile
            .absolutePath
        project.exec {
            commandLine("python3", rosterRevampPatch.absolutePath, generatedSourceRoot)
        }
        project.exec {
            commandLine("python3", seasonalPatch.absolutePath, generatedSourceRoot)
        }
        project.exec {
            commandLine("python3", transparencyPatch.absolutePath, generatedSourceRoot)
        }
        project.exec {
            commandLine("python3", uxPatch.absolutePath, generatedSourceRoot)
        }
        project.exec {
            commandLine("python3", pupEyeSecurityPatch.absolutePath, generatedSourceRoot)
        }
        project.exec {
            commandLine("python3", importReloadPatch.absolutePath, generatedSourceRoot)
        }
        // Run the Settings/onboarding integration after all established compatibility patches.
        project.exec {
            commandLine("python3", settingsSetupPatch.absolutePath, generatedSourceRoot)
        }
        // Developer Console sees the finished Settings surface and routes final diagnostics.
        project.exec {
            commandLine("python3", developerConsolePatch.absolutePath, generatedSourceRoot)
        }
        // Navigation is last among the legacy patches so established anchors remain unchanged while
        // the app shell promotes Roster/Rewards and moves Settings/Prestige internally.
        project.exec {
            commandLine("python3", rosterNavigationPatch.absolutePath, generatedSourceRoot)
        }
        // Puppy Exchange integrates against the finished navigation shell.
        project.exec {
            commandLine("python3", puppyExchangePatch.absolutePath, generatedSourceRoot)
        }
        // Compact the approved roster controls and move the full Account & Profile experience
        // into the expandable identity card at the top of Settings.
        project.exec {
            commandLine("python3", compactRosterSettingsPatch.absolutePath, generatedSourceRoot)
        }
        // Schema-2 Puppy Code claims run last so no earlier compatibility patch can restore
        // the deprecated local claim fallback.
        project.exec {
            commandLine("python3", puppyCodeV2Patch.absolutePath, generatedSourceRoot)
        }
        // The visual revamp runs last against the fully integrated app shell so it cannot
        // disrupt Exchange, Settings/onboarding, compact roster, or live Puppy Code behavior.
        project.exec {
            commandLine("python3", mainUiRevampPatch.absolutePath, generatedSourceRoot)
        }
    }
}

// Release builds also run the pure JVM calendar and developer-console regression tests.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}
