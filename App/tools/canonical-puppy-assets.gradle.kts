// Packages the exact repository PNG roster into the Android APK as the canonical fallback.
// This keeps roster art correct even when the private GitHub raw endpoint cannot be reached.

val canonicalV1Ids = listOf(
    "v1_classic",
    "v1_golden",
    "v1_poodle",
    "v1_spotty",
    "v1_midnight",
    "v1_cloud",
    "v1_aurora",
    "v1_cocoa",
    "v1_snowball",
    "v1_galaxy",
    "v1_neon_buddy",
    "v1_golden_night",
    "v1_halloween",
    "v1_santa",
    "v1_birthday",
    "v1_dev_pup",
    "v1_secret_snoot",
    "v1_classic_forever"
)

val canonicalV2Ids = listOf(
    "v2_frost",
    "v2_honey",
    "v2_biscuit",
    "v2_onyx",
    "v2_domino",
    "v2_chestnut",
    "v2_prism",
    "v2_flurry",
    "v2_harleytg"
)

val canonicalRepoAssets = rootProject.file("../assets")
val canonicalOutput = layout.buildDirectory.dir("generated/protected-puppies/assets/canonical-puppies")
val canonicalMaxBytes = 8L * 1024L * 1024L
val pngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)

tasks.named("prepareProtectedPuppyAssets").configure {
    inputs.files(canonicalV1Ids.map { canonicalRepoAssets.resolve("v1/$it.png") })
    inputs.files(canonicalV2Ids.map { canonicalRepoAssets.resolve("v2/$it.png") })
    outputs.dir(canonicalOutput)

    doLast {
        val outputRoot = canonicalOutput.get().asFile
        outputRoot.deleteRecursively()
        val v1Output = outputRoot.resolve("v1").apply { mkdirs() }
        val v2Output = outputRoot.resolve("v2").apply { mkdirs() }

        fun copyCanonical(version: String, assetId: String, targetDir: java.io.File) {
            val source = canonicalRepoAssets.resolve("$version/$assetId.png")
            require(source.isFile) { "Missing canonical puppy PNG: ${source.path}" }
            require(source.length() in 1..canonicalMaxBytes) { "Invalid canonical puppy PNG size: ${source.path}" }
            val header = source.inputStream().use { input -> ByteArray(8).also { bytes -> require(input.read(bytes) == 8) } }
            require(header.contentEquals(pngSignature)) { "Canonical puppy asset is not a PNG: ${source.path}" }
            source.copyTo(targetDir.resolve("$assetId.png"), overwrite = true)
        }

        canonicalV1Ids.forEach { copyCanonical("v1", it, v1Output) }
        canonicalV2Ids.forEach { copyCanonical("v2", it, v2Output) }

        require(v1Output.listFiles { file -> file.extension == "png" }?.size == 18) {
            "Canonical V1 roster must contain exactly 18 PNGs"
        }
        require(v2Output.listFiles { file -> file.extension == "png" }?.size == 9) {
            "Canonical V2 roster must contain exactly 9 PNGs"
        }
    }
}
