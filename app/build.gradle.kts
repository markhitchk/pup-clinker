import java.net.URI

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val puppySourceCommit = "ab844c9f5fd09f5f17b9bf577e75524f0b6edcd1"
val generatedSourceRes = layout.buildDirectory.dir("generated/source-assets/res")
val generatedSourceDrawables = layout.buildDirectory.dir("generated/source-assets/res/drawable-nodpi")

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

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

val downloadPuppySourceAssets by tasks.registering {
    description = "Downloads original Puppy Clicker artwork pinned to the source repository commit."
    group = "puppy clicker"
    outputs.dir(generatedSourceDrawables)

    doLast {
        val outputDir = generatedSourceDrawables.get().asFile
        outputDir.mkdirs()

        val baseUrl = "https://raw.githubusercontent.com/HarleyTG-O/Puppy-Clicker/$puppySourceCommit/Images"
        val assets = mapOf(
            "source_pup.png" to "$baseUrl/pup.png",
            "source_logo.png" to "$baseUrl/logo.png",
            "source_htg.png" to "$baseUrl/htg.png"
        )

        assets.forEach { (fileName, sourceUrl) ->
            val target = outputDir.resolve(fileName)
            if (!target.exists() || target.length() == 0L) {
                URI(sourceUrl).toURL().openStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(downloadPuppySourceAssets)
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
