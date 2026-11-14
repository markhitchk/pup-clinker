plugins { id("com.android.application") }
android {
    namespace = "com.harleytg.filewiper"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.harleytg.filewiper"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
