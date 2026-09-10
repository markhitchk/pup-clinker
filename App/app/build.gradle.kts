import java.net.URI
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val puppySourceCommit = "ab844c9f5fd09f5f17b9bf577e75524f0b6edcd1"
val generatedSourceRes = layout.buildDirectory.dir("generated/source-assets/res")
val generatedSourceDrawables = layout.buildDirectory.dir("generated/source-assets/res/drawable-nodpi")
val generatedSourceVectorDrawables = layout.buildDirectory.dir("generated/source-assets/res/drawable")
val generatedProtectedAssets = layout.buildDirectory.dir("generated/protected-puppies/assets")
val generatedProtectedSource = layout.buildDirectory.dir("generated/protected-puppies/source")
val originalMainSourceDir = layout.projectDirectory.dir("src/main/java")
val puppySvgSourceDir = layout.projectDirectory.dir("src/main/puppy-svg")
val protectedPuppySourceDir = layout.buildDirectory.dir("generated/puppy-vectors").get()
val legalSourceDir = rootProject.file("../assets/legal")

val v1ProtectedPuppyIds = listOf(
    "classic",
    "golden",
    "poodle",
    "spotty",
    "midnight",
    "cloud",
    "aurora",
    "cocoa",
    "snowball",
    "galaxy",
    "neon_buddy",
    "golden_night",
    "halloween",
    "santa",
    "birthday",
    "dev_pup",
    "secret_snoot",
    "classic_forever"
)

val v2ProtectedPuppyIds = listOf(
    "v2_frost",
    "v2_honey",
    "v2_biscuit",
    "v2_onyx",
    "v2_domino",
    "v2_chestnut",
    "v2_prism",
    "v2_flurry"
)

val PUPPY_KEY_MASK_A = "f382c0752e0bda1c7ac539661e2a2eb12a01202e848a1f2fd925bceddd3e9ca8"
val PUPPY_KEY_MASK_B = "aad54e1243c251683af5c3709dfb34ff888e9f8de7b81104716bb120c239984f"

fun puppyHexToBytes(hex: String): ByteArray = ByteArray(hex.length / 2) { index ->
    hex.substring(index * 2, index * 2 + 2).toInt(16).toByte()
}

fun puppyAssetKey(): ByteArray {
    val a = puppyHexToBytes(PUPPY_KEY_MASK_A)
    val b = puppyHexToBytes(PUPPY_KEY_MASK_B)
    return ByteArray(a.size) { index -> (a[index].toInt() xor b[index].toInt()).toByte() }
}

fun protectPuppyAsset(assetId: String, plain: ByteArray): ByteArray {
    val nonce = ByteArray(12).also { SecureRandom().nextBytes(it) }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(puppyAssetKey(), "AES"), GCMParameterSpec(128, nonce))
    cipher.updateAAD("puppy-clicker:$assetId".toByteArray(Charsets.UTF_8))
    val encrypted = cipher.doFinal(plain)
    return "PCP1".toByteArray(Charsets.US_ASCII) + nonce + encrypted
}

val signingStoreFilePath = System.getenv("PUPPY_SIGNING_STORE_FILE")
val signingStorePassword = System.getenv("PUPPY_SIGNING_STORE_PASSWORD")
val signingKeyAlias = System.getenv("PUPPY_SIGNING_KEY_ALIAS")
val signingKeyPassword = System.getenv("PUPPY_SIGNING_KEY_PASSWORD")
val hasPermanentSigning = listOf(
    signingStoreFilePath,
    signingStorePassword,
    signingKeyAlias,
    signingKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "com.harleytg.puppyclicker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.harleytg.puppyclicker"
        minSdk = 26
        targetSdk = 35
        versionCode = 22
        versionName = "1.7.9"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = true
        }

        if (hasPermanentSigning) {
            create("update") {
                storeFile = file(signingStoreFilePath!!)
                storePassword = signingStorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("update")?.let { signingConfig = it }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].res.srcDir(generatedSourceRes)
    sourceSets["main"].res.exclude("**/v1_*.xml")
    sourceSets["main"].res.exclude("**/v2_*.xml")
    sourceSets["main"].assets.srcDir(generatedProtectedAssets)
    sourceSets["main"].java.setSrcDirs(listOf(generatedProtectedSource))

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val compilePuppySvg by tasks.registering(Exec::class) {
    description = "Compiles exact SVG paths for the existing Android puppy renderer."
    group = "puppy clicker"
    inputs.dir(puppySvgSourceDir)
    inputs.files(rootProject.file("tools/puppy_svg.py"), rootProject.file("tools/check_puppy_vectors.py"))
    outputs.dir(protectedPuppySourceDir)
    commandLine(
        "python3", rootProject.file("tools/puppy_svg.py").absolutePath,
        "--source-dir", puppySvgSourceDir.asFile.absolutePath,
        "--output-dir", protectedPuppySourceDir.asFile.absolutePath
    )
}

val prepareProtectedPuppyAssets by tasks.registering {
    dependsOn(compilePuppySvg)
    description = "Encrypts V1/V2 puppy artwork and packages repository-managed legal documents into APK assets."
    group = "puppy clicker"
    inputs.files(v1ProtectedPuppyIds.map { protectedPuppySourceDir.file("v1_$it.xml") })
    inputs.files(v2ProtectedPuppyIds.map { protectedPuppySourceDir.file("$it.xml") })
    inputs.dir(legalSourceDir)
    outputs.dir(generatedProtectedAssets)
    outputs.dir(generatedSourceDrawables)
    outputs.dir(generatedSourceVectorDrawables)

    doLast {
        val assetRoot = generatedProtectedAssets.get().asFile
        val puppyAssetDir = assetRoot.resolve("puppies")
        val legalAssetDir = assetRoot.resolve("legal")
        val drawableDir = generatedSourceDrawables.get().asFile
        val vectorDrawableDir = generatedSourceVectorDrawables.get().asFile

        puppyAssetDir.deleteRecursively()
        puppyAssetDir.mkdirs()
        legalAssetDir.deleteRecursively()
        legalAssetDir.mkdirs()
        drawableDir.mkdirs()
        vectorDrawableDir.mkdirs()

        require(legalSourceDir.isDirectory) { "Missing repository legal source directory: ${legalSourceDir.path}" }
        listOf("terms-of-use.txt", "privacy-policy.txt").forEach { fileName ->
            val source = legalSourceDir.resolve(fileName)
            require(source.isFile) { "Missing repository legal document: ${source.path}" }
            source.copyTo(legalAssetDir.resolve(fileName), overwrite = true)
        }

        v1ProtectedPuppyIds.forEach { styleId ->
            val assetId = "v1_$styleId"
            val source = protectedPuppySourceDir.file("$assetId.xml").asFile
            require(source.isFile) { "Missing protected V1 puppy vector: ${source.path}" }
            puppyAssetDir.resolve("$assetId.pup").writeBytes(
                protectPuppyAsset(assetId, source.readBytes())
            )
        }

        v2ProtectedPuppyIds.forEach { assetId ->
            val source = protectedPuppySourceDir.file("$assetId.xml").asFile
            require(source.isFile) { "Missing protected V2 puppy vector: ${source.path}" }
            puppyAssetDir.resolve("$assetId.pup").writeBytes(
                protectPuppyAsset(assetId, source.readBytes())
            )
        }

        val baseUrl = "https://raw.githubusercontent.com/HarleyTG-O/Puppy-Clicker/$puppySourceCommit/Images"
        mapOf(
            "source_logo.png" to "$baseUrl/logo.png",
            "source_htg.png" to "$baseUrl/htg.png"
        ).forEach { (fileName, sourceUrl) ->
            URI(sourceUrl).toURL().openStream().use { input ->
                drawableDir.resolve(fileName).outputStream().use { output -> input.copyTo(output) }
            }
        }

        vectorDrawableDir.resolve("source_pup.xml").writeText(
            """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="256dp" android:height="256dp"
    android:viewportWidth="256" android:viewportHeight="256">
    <path android:fillColor="#00B8F0"
        android:pathData="M128,38C87,38 54,70 54,109c0,27 15,50 38,62 -10,10 -17,24 -17,39 0,7 6,12 13,12h80c7,0 13,-5 13,-12 0,-15 -7,-29 -17,-39 23,-12 38,-35 38,-62 0,-39 -33,-71 -74,-71zM92,94a16,16 0,1 1,0 32,16 16,0 0,1 0,-32zM164,94a16,16 0,1 1,0 32,16 16,0 0,1 0,-32zM104,145c14,12 34,12 48,0 4,-3 9,2 6,6 -16,19 -44,19 -60,0 -3,-4 2,-9 6,-6z"/>
</vector>
"""
        )
    }
}

val generateProtectedPuppySources by tasks.registering {
    description = "Copies app Kotlin sources and routes V6 portraits through streamed PNG artwork."
    group = "puppy clicker"
    inputs.dir(originalMainSourceDir)
    outputs.dir(generatedProtectedSource)

    doLast {
        val targetRoot = generatedProtectedSource.get().asFile
        targetRoot.deleteRecursively()
        targetRoot.mkdirs()

        project.copy {
            from(originalMainSourceDir)
            into(targetRoot)
        }

        val target = targetRoot.resolve("com/harleytg/puppyclicker/PuppyClickerV6Activity.kt")
        require(target.isFile) { "Unable to locate copied PuppyClickerV6Activity.kt" }

        val source = target.readText()
        val portraitStart = source.indexOf("@Composable\nprivate fun V6PuppyPortrait")
        val backgroundStart = source.indexOf("\nprivate fun v6PuppyBackground", portraitStart)
        require(portraitStart >= 0 && backgroundStart > portraitStart) {
            "Unable to locate V6 puppy portrait block for protection transform"
        }

        val replacement = """@Composable
private fun V6PuppyPortrait(styleId: String, size: Dp, accessory: String = "None", unlocked: Boolean = true) {
    StreamedPuppyPortrait(
        styleId = styleId,
        size = size,
        accessory = accessory,
        unlocked = unlocked,
        background = v6PuppyBackground(styleId),
        furFilter = null
    )
}
"""

        target.writeText(source.substring(0, portraitStart) + replacement + source.substring(backgroundStart))
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareProtectedPuppyAssets, generateProtectedPuppySources)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
}

apply(from = rootProject.file("tools/seasonal.gradle.kts"))
