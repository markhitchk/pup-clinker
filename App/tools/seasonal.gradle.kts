// Add canonical roster artwork, dynamic manifest rosters, seasonal features and final UI integration.
apply(from = rootProject.file("tools/canonical-puppy-assets.gradle.kts"))
apply(from = rootProject.file("tools/dynamic-puppy-roster.gradle.kts"))

// Original game sources, shared artwork and other platform projects are untouched.
tasks.named("generateProtectedPuppySources").configure {
    val seasonalPatch = rootProject.file("tools/patch_seasonal_events.py")
    val transparencyPatch = rootProject.file("tools/patch_roster_transparency.py")
    val uxPatch = rootProject.file("tools/patch_puppy_ux.py")
    val pupEyeSecurityPatch = rootProject.file("tools/patch_pupeye_security.py")
    val importReloadPatch = rootProject.file("tools/patch_import_reload.py")
    val settingsSetupCorePatch = rootProject.file("tools/patch_settings_setup_revamp.py")
    val settingsSetupPatch = rootProject.file("tools/patch_settings_setup_revamp_runner.py")
    val developerConsolePatch = rootProject.file("tools/patch_developer_console.py")
    inputs.files(
        seasonalPatch,
        transparencyPatch,
        uxPatch,
        pupEyeSecurityPatch,
        importReloadPatch,
        settingsSetupCorePatch,
        settingsSetupPatch,
        developerConsolePatch
    )
    doLast {
        val generatedSourceRoot = layout.buildDirectory
            .dir("generated/protected-puppies/source")
            .get()
            .asFile
            .absolutePath
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
        // Developer Console integration is the final transform so it sees the finished Settings
        // surface and can route the final generated Kotlin diagnostics through PuppyDebugLog.
        project.exec {
            commandLine("python3", developerConsolePatch.absolutePath, generatedSourceRoot)
        }
    }
}

// Release builds also run the pure JVM calendar and developer-console regression tests.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}
