plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "helium314.keyboard.latin"
    compileSdk = 35
    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("String", "APPLICATION_ID", "\"app.still.launcher\"")
        buildConfigField("int", "VERSION_CODE", "3501")
        buildConfigField("String", "VERSION_NAME", "\"0.2.0 (HeliBoard 3.5)\"")
        ndk { abiFilters.addAll(listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) }
    }
    buildFeatures { viewBinding = true; buildConfig = true; compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    externalNativeBuild { ndkBuild { path = file("src/main/jni/Android.mk") } }
    ndkVersion = "27.0.12077973"
    lint { abortOnError = true; disable += "MissingTranslation" } // Upstream translations are intentionally incomplete.
}
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.9.3")
    implementation("sh.calvin.reorderable:reorderable:2.4.3")
    implementation("com.github.skydoves:colorpicker-compose:1.1.2")
    testImplementation("junit:junit:4.12")
}
