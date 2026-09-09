// Add canonical roster artwork and seasonal features to the Android generated-source pipeline.
apply(from = rootProject.file("tools/canonical-puppy-assets.gradle.kts"))

// Original game sources, shared artwork and other platform projects are untouched.
tasks.named("generateProtectedPuppySources").configure {
    val patch = rootProject.file("tools/patch_seasonal_events.py")
    inputs.file(patch)
    doLast {
        project.exec {
            commandLine(
                "python3", patch.absolutePath,
                layout.buildDirectory.dir("generated/protected-puppies/source").get().asFile.absolutePath
            )
        }
    }
}

// Release builds also run the pure JVM calendar regression tests.
tasks.matching { it.name == "assembleRelease" }.configureEach {
    dependsOn("testDebugUnitTest")
}
