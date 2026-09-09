// Add canonical roster artwork, dynamic manifest rosters, and seasonal features.
apply(from = rootProject.file("tools/canonical-puppy-assets.gradle.kts"))
apply(from = rootProject.file("tools/dynamic-puppy-roster.gradle.kts"))

// Original game sources, shared artwork and other platform projects are untouched.
tasks.named("generateProtectedPuppySources").configure {
    val seasonalPatch = rootProject.file("tools/patch_seasonal_events.py")
    val transparencyPatch = rootProject.file("tools/patch_roster_transparency.py")
    val uxPatch = rootProject.file("tools/patch_puppy_ux.py")
    inputs.files(seasonalPatch, transparencyPatch, uxPatch)
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
    }
}

// Release builds also run the pure JVM calendar regression tests.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}
