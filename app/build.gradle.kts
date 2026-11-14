plugins { id("com.android.application") }
android {
    namespace = "com.harleytg.filewiper"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.harleytg.filewiper.fixed"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "0.1.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
