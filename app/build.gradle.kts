plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Website address. Change it here, or set a GitHub repo variable named BASE_URL.
val siteUrl: String = (System.getenv("BASE_URL")?.trim()?.takeIf { it.isNotEmpty() }
    ?: "https://v2.medicglobe.fun/").let { if (it.endsWith("/")) it else "$it/" }

// Each GitHub build gets a higher number, so a new APK installs over the old one.
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.medicglobe.knoxsolar"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.medicglobe.knoxsolar"
        minSdk = 29
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        buildConfigField("String", "BASE_URL", "\"$siteUrl\"")
    }

    signingConfigs {
        create("release") {
            storeFile = file("knox-release.jks")
            storePassword = "knoxsolar"
            keyAlias = "knox"
            keyPassword = "knoxsolar"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { buildConfig = true }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
