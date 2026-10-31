plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

val releaseKeystore = System.getenv("RELEASE_KEYSTORE")
val releaseStorePassword = System.getenv("RELEASE_STORE_PASSWORD")
val releaseAlias = System.getenv("RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("RELEASE_KEY_PASSWORD")
if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    require(listOf(releaseKeystore, releaseStorePassword, releaseAlias, releaseKeyPassword).all { !it.isNullOrBlank() }) {
        "Release signing requires the original Still Launcher key. See ANDROID_RELEASES.md."
    }
    require(releaseAlias == "androiddebugkey") { "Preserve Still Launcher's existing signing identity." }
}

android {
    namespace = "app.still.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.still.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.6.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled = true }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { buildConfig = true }
    signingConfigs {
        create("permanentRelease") {
            storeFile = releaseKeystore?.let { file(it) }
            storePassword = releaseStorePassword
            keyAlias = releaseAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes { release {
        isMinifyEnabled = false
        signingConfig = signingConfigs.getByName("permanentRelease")
    } }
    lint { abortOnError = true }
}
dependencies {
    implementation(project(":keyboard"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.12")
}
