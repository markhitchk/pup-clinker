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
val originalV6Activity = layout.projectDirectory.file("src/main/java/com/harleytg/puppyclicker/PuppyClickerV6Activity.kt")
val v2PuppySourceDir = layout.projectDirectory.dir("src/main/res/drawable-anydpi")
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

// Build-time counterpart of the split runtime key in ProtectedPuppyArt.kt.
private const val PUPPY_KEY_MASK_A = "f382c0752e0bda1c7ac539661e2a2eb12a01202e848a1f2fd925bceddd3e9ca8"
private const val PUPPY_KEY_MASK_B = "aad54e1243c251683af5c3709dfb34ff888e9f8de7b81104716bb120c239984f"

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
        versionCode = 14
        versionName = "1.7.1"

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
    sourceSets["main"].res.exclude("**/v2_*.xml")
    sourceSets["main"].assets.srcDir(generatedProtectedAssets)
    sourceSets["main"].java.srcDir(generatedProtectedSource)
    sourceSets["main"].java.exclude("**/PuppyClickerV6Activity.kt")

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val prepareProtectedPuppyAssets by tasks.registering {
    description = "Encrypts all V1/V2 puppy artwork into generated APK assets."
    group = "puppy clicker"
    inputs.files(v2ProtectedPuppyIds.map { v2PuppySourceDir.file("$it.xml") })
    outputs.dir(generatedProtectedAssets)
    outputs.dir(generatedSourceDrawables)
    outputs.dir(generatedSourceVectorDrawables)

    doLast {
        val assetRoot = generatedProtectedAssets.get().asFile
        val puppyAssetDir = assetRoot.resolve("puppies")
        val drawableDir = generatedSourceDrawables.get().asFile
        val vectorDrawableDir = generatedSourceVectorDrawables.get().asFile

        puppyAssetDir.deleteRecursively()
        puppyAssetDir.mkdirs()
        drawableDir.mkdirs()
        vectorDrawableDir.mkdirs()

        val baseUrl = "https://raw.githubusercontent.com/HarleyTG-O/Puppy-Clicker/$puppySourceCommit/Images"

        val v1Bytes = URI("$baseUrl/pup.png").toURL().openStream().use { it.readBytes() }
        puppyAssetDir.resolve("v1_base.pup").writeBytes(protectPuppyAsset("v1_base", v1Bytes))

        v2ProtectedPuppyIds.forEach { assetId ->
            val source = v2PuppySourceDir.file("$assetId.xml").asFile
            require(source.isFile) { "Missing protected puppy source: ${source.path}" }
            puppyAssetDir.resolve("$assetId.pup").writeBytes(
                protectPuppyAsset(assetId, source.readBytes())
            )
        }

        mapOf(
            "source_logo.png" to "$baseUrl/logo.png",
            "source_htg.png" to "$baseUrl/htg.png"
        ).forEach { (fileName, sourceUrl) ->
            URI(sourceUrl).toURL().openStream().use { input ->
                drawableDir.resolve(fileName).outputStream().use { output -> input.copyTo(output) }
            }
        }

        vectorDrawableDir.resolve("source_pup.xml").writeText(
            """<?xml version=\"1.0\" encoding=\"utf-8\"?>
<vector xmlns:android=\"http://schemas.android.com/apk/res/android\"
    android:width=\"256dp\" android:height=\"256dp\"
    android:viewportWidth=\"256\" android:viewportHeight=\"256\">
    <path android:fillColor=\"#00B8F0\"
        android:pathData=\"M128,38C87,38 54,70 54,109c0,27 15,50 38,62 -10,10 -17,24 -17,39 0,7 6,12 13,12h80c7,0 13,-5 13,-12 0,-15 -7,-29 -17,-39 23,-12 38,-35 38,-62 0,-39 -33,-71 -74,-71zM92,94a16,16 0,1 1,0 32,16 16,0 0,1 0,-32zM164,94a16,16 0,1 1,0 32,16 16,0 0,1 0,-32zM104,145c14,12 34,12 48,0 4,-3 9,2 6,6 -16,19 -44,19 -60,0 -3,-4 2,-9 6,-6z\"/>
</vector>
"""
        )
    }
}

val generateProtectedPuppyActivity by tasks.registering {
    description = "Generates the V6 activity copy that renders encrypted puppy assets."
    group = "puppy clicker"
    inputs.file(originalV6Activity)
    outputs.dir(generatedProtectedSource)

    doLast {
        val source = originalV6Activity.asFile.readText()
        val portraitStart = source.indexOf("@Composable\nprivate fun V6PuppyPortrait")
        val backgroundStart = source.indexOf("\nprivate fun v6PuppyBackground", portraitStart)
        require(portraitStart >= 0 && backgroundStart > portraitStart) {
            "Unable to locate V6 puppy portrait block for protection transform"
        }

        val replacement = """@Composable
private fun V6PuppyPortrait(styleId: String, size: Dp, accessory: String = \"None\", unlocked: Boolean = true) {
    ProtectedPuppyPortrait(
        styleId = styleId,
        size = size,
        accessory = accessory,
        unlocked = unlocked,
        background = v6PuppyBackground(styleId),
        furFilter = v6PuppyFilter(styleId)
    )
}
"""

        val patched = source.substring(0, portraitStart) + replacement + source.substring(backgroundStart)
        val target = generatedProtectedSource.get().asFile
            .resolve("com/harleytg/puppyclicker/PuppyClickerV6ActivityProtected.kt")
        target.parentFile.mkdirs()
        target.writeText(patched)
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareProtectedPuppyAssets, generateProtectedPuppyActivity)
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.12.01"))

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
}
