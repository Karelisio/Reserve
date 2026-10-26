plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

// Signing material is only provided by CI (GitHub secrets) or the developer's
// environment: nothing is ever read from the repository.
val releaseKeystore = System.getenv("RESERVE_KEYSTORE_PATH")?.let(::file)?.takeIf { it.exists() }

android {
    namespace = "com.karelisio.reserve"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.karelisio.reserve"
        minSdk = 26
        targetSdk = 36
        versionCode = (System.getenv("RESERVE_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("RESERVE_VERSION_NAME") ?: "1.0.0"
        // In-app updates come from the GitHub releases of this repository.
        buildConfigField("String", "GITHUB_REPO", "\"Karelisio/Reserve\"")
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RESERVE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RESERVE_KEY_ALIAS")
                keyPassword = System.getenv("RESERVE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            buildConfigField("boolean", "UPDATES_ENABLED", "true")
        }
        debug {
            applicationIdSuffix = ".debug"
            // A debug build cannot be replaced by a release APK (other id, other key).
            buildConfigField("boolean", "UPDATES_ENABLED", "false")
        }
    }

    // Per-ABI APKs (much smaller, MapLibre ships native code) plus a universal one.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = true
        // Version suggestions are handled manually (beta Material 3 Expressive, target SDK).
        disable += setOf("NewerVersionAvailable", "GradleDependency", "OldTargetApi", "AndroidGradlePluginVersion")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        optIn.addAll(
            "androidx.compose.material3.ExperimentalMaterial3Api",
            "androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    // Applies the baseline profiles at install time, also for APKs installed outside the Play Store.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.navigation.suite)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.maplibre.android)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
