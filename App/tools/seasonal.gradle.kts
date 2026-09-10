// Add canonical roster artwork, dynamic manifest rosters, and seasonal features.
apply(from = rootProject.file("tools/canonical-puppy-assets.gradle.kts"))
apply(from = rootProject.file("tools/dynamic-puppy-roster.gradle.kts"))

// Release identity override. Script plugins do not receive the Android plugin's Kotlin DSL
// type-safe accessors, so use the already-created Android extension reflectively. This still runs
// during Gradle configuration, before the manifest/resources are compiled.
val androidExtension = project.extensions.getByName("android")
val releaseDefaultConfig = androidExtension.javaClass.methods
    .first { it.name == "getDefaultConfig" && it.parameterCount == 0 }
    .invoke(androidExtension)
releaseDefaultConfig.javaClass.methods
    .first { it.name == "setVersionCode" && it.parameterCount == 1 }
    .invoke(releaseDefaultConfig, 22)
releaseDefaultConfig.javaClass.methods
    .first { it.name == "setVersionName" && it.parameterCount == 1 }
    .invoke(releaseDefaultConfig, "1.7.9")

// Original game sources, shared artwork and other platform projects are untouched.
tasks.named("generateProtectedPuppySources").configure {
    val seasonalPatch = rootProject.file("tools/patch_seasonal_events.py")
    val transparencyPatch = rootProject.file("tools/patch_roster_transparency.py")
    val uxPatch = rootProject.file("tools/patch_puppy_ux.py")
    val pupEyeSecurityPatch = rootProject.file("tools/patch_pupeye_security.py")
    val importReloadPatch = rootProject.file("tools/patch_import_reload.py")
    inputs.files(seasonalPatch, transparencyPatch, uxPatch, pupEyeSecurityPatch, importReloadPatch)
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
    }
}

// Release builds also run the pure JVM calendar regression tests.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}
