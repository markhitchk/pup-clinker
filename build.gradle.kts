plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}

// Release version override. Android Components finalizes the effective DSL
// after the app module has configured its baseline defaultConfig values.
// Keep the application ID and existing signing configuration unchanged.
subprojects {
    plugins.withId("com.android.application") {
        extensions.configure<com.android.build.api.variant.ApplicationAndroidComponentsExtension> {
            finalizeDsl { android ->
                android.defaultConfig.versionCode = 20
                android.defaultConfig.versionName = "1.7.7"
            }
        }
    }
}

