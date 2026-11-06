import com.android.build.api.variant.FilterConfiguration
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose.compiler)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    // CE (#211): the open-source licenses screen, the same list as the phone app.
    alias(libs.plugins.aboutlibraries)
    alias(libs.plugins.aboutlibraries.android)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val envKeystoreFile: String? = System.getenv("KEYSTORE_FILE")
val keystoreProps: Properties? =
    if (envKeystoreFile == null && keystorePropertiesFile.exists()) {
        Properties().apply { load(keystorePropertiesFile.inputStream()) }
    } else null

// CE-owned: beta channel, the same as app/phone. .github/workflows/beta.yml sets BETA_BUILD to its
// run number, which marks the staging build as a beta and keeps its versionCode increasing.
val betaBuild: Int? = System.getenv("BETA_BUILD")?.toIntOrNull()

java { toolchain { languageVersion = Versions.JAVA } }

android {
    namespace = "dev.jdtech.jellyfin"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    if (envKeystoreFile != null || keystoreProps != null) {
        signingConfigs {
            create("release") {
                if (envKeystoreFile != null) {
                    storeFile = file(envKeystoreFile)
                    storePassword = System.getenv("KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("KEY_ALIAS")
                    keyPassword = System.getenv("KEY_PASSWORD")
                } else {
                    storeFile = rootProject.file(keystoreProps!!.getProperty("storeFile"))
                    storePassword = keystoreProps.getProperty("storePassword")
                    keyAlias = keystoreProps.getProperty("keyAlias")
                    keyPassword = keystoreProps.getProperty("keyPassword")
                }
            }
        }
    }

    defaultConfig {
        applicationId = "nl.midasvo.findroid.ce"
        minSdk = Versions.MIN_SDK
        targetSdk = Versions.TARGET_SDK

        // CE-owned: Ephyra's own version (EphyraVersion.kt), not upstream's Versions.APP_*.
        versionCode = EphyraVersion.CODE
        versionName = EphyraVersion.NAME
    }

    buildTypes {
        named("debug") { applicationIdSuffix = ".debug" }
        named("release") {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        register("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = betaBuild?.let { "-beta.$it" } ?: "-staging"
        }
    }

    flavorDimensions += "variant"
    productFlavors {
        register("libre") {
            dimension = "variant"
            isDefault = true
        }
    }

    splits {
        abi {
            // Detect app bundle and conditionally disable split abis
            // This is needed due to a "Multiple shrunk-resources files found in directory" error
            // present since AGP 8.9.0, for more info see:
            // https://issuetracker.google.com/issues/402800800
            val isBuildingBundle =
                gradle.startParameter.taskNames.any { it.lowercase().contains("bundle") }
            isEnable = !isBuildingBundle

            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    // CE-owned: Compose UI tests run on the JVM with Robolectric, so CI needs no emulator.
    testOptions { unitTests { isIncludeAndroidResources = true } }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }
}

androidComponents {
    onVariants { variant ->
        if (variant.buildType != "release" && variant.buildType != "staging") return@onVariants
        variant.outputs.forEach { output ->
            // Same versionCode as the phone beta of the same run, so switching a box between the
            // phone and TV APK (one application ID, ADR 0005) is never a downgrade.
            if (variant.buildType == "staging" && betaBuild != null) {
                output.versionCode.set(1_000_000 + betaBuild)
                output.versionName.set("${EphyraVersion.NEXT_PATCH}-beta.$betaBuild")
            }
            // CE-owned: phone and TV share one application ID (ADR 0005), but Play needs a unique
            // versionCode per upload and F-Droid offers the highest compatible one. So the TV
            // release sits just above the phone release of the same version.
            if (variant.buildType == "release") {
                output.versionCode.set(EphyraVersion.TV_CODE)
            }
            val abi =
                output.filters
                    .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
                    ?.identifier
            // CE-owned: the phone name plus "-tv", so the TV APK says what it is (ADR 0006).
            output.outputFileName.set(
                "ephyra-v${output.versionName.get()}-${variant.flavorName}-tv-$abi.apk"
            )
        }
    }
}

dependencies {
    implementation(projects.core)
    implementation(projects.data)
    implementation(projects.setup)
    implementation(projects.modes.film)
    implementation(projects.player.core)
    implementation(projects.player.local)
    implementation(projects.settings)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.savedstate.compose)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.tvprovider)
    implementation(libs.androidx.work)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.network.cache.control)
    implementation(libs.coil.svg)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.jellyfin.core)
    ksp(libs.kotlin.metadata.jvm)
    implementation(libs.media3.ffmpeg.decoder)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.timber)
    implementation(libs.aboutlibraries.core)
    // CE (#211): the "Report a problem" QR code on the About screen.
    implementation(libs.zxing.core)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
