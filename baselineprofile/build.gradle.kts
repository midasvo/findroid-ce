import com.android.build.api.dsl.ManagedVirtualDevice

plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

java { toolchain { languageVersion = Versions.JAVA } }

android {
    namespace = "dev.jdtech.jellyfin.baselineprofile"
    compileSdk = Versions.COMPILE_SDK
    buildToolsVersion = Versions.BUILD_TOOLS

    defaultConfig {
        minSdk = 28
        targetSdk = Versions.TARGET_SDK
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app:phone"

    // Must match app/phone's only flavor.
    flavorDimensions += "variant"
    productFlavors { register("libre") { dimension = "variant" } }

    testOptions.managedDevices.allDevices {
        create<ManagedVirtualDevice>("pixel6Api34") {
            device = "Pixel 6"
            apiLevel = 34
            // AOSP images are rootable, which profile collection needs. No Google APIs.
            systemImageSource = "aosp"
        }
    }
}

baselineProfile {
    // -Pbaselineprofile.useConnectedDevices=true runs on the attached phone instead, which is how
    // a profile for a logged-in server is recorded (see README.md). The plugin runs managed
    // devices as well when both are set, so the managed device is only added without the flag.
    val useConnected = providers.gradleProperty("baselineprofile.useConnectedDevices").isPresent
    if (!useConnected) managedDevices += "pixel6Api34"
    useConnectedDevices = useConnected
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.junit)
}
