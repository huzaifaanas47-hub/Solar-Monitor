plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Server address. Change it here, or set a GitHub repo variable named BASE_URL.
// (It can also be changed inside the app: Settings -> Server address.)
val siteUrl: String = (System.getenv("BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
    ?: "https://v2.medicglobe.fun/").let { if (it.endsWith("/")) it else "$it/" }

// Each GitHub build gets a higher number, so a new APK installs over the old one.
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

// Optional signing from the environment. With no KEYSTORE_PATH the release APK is
// left UNSIGNED so you can sign it yourself (see README).
val ksPath: String? = System.getenv("KEYSTORE_PATH")?.takeIf { it.isNotEmpty() }

android {
    namespace = "com.medicglobe.knoxsolar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.medicglobe.knoxsolar"
        minSdk = 29
        targetSdk = 34
        versionCode = buildNumber
        versionName = "2.0.$buildNumber"
        buildConfigField("String", "BASE_URL", "\"$siteUrl\"")
    }

    if (ksPath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
