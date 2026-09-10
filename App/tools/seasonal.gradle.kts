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
    val settingsSetupPatch = rootProject.file("tools/patch_settings_setup_revamp.py")
    inputs.files(
        seasonalPatch,
        transparencyPatch,
        uxPatch,
        pupEyeSecurityPatch,
        importReloadPatch,
        settingsSetupPatch
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
        // Run the Settings/onboarding integration last so all established compatibility patches
        // retain their exact anchors and the final generated V6 UI uses the redesigned surfaces.
        project.exec {
            commandLine("python3", settingsSetupPatch.absolutePath, generatedSourceRoot)
        }
    }
}

// Release builds also run the pure JVM calendar regression tests.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}
