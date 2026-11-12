plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.hour.car"
    compileSdk = 35
    defaultConfig { minSdk = 28 }

    flavorDimensions += "env"
    productFlavors {
        create("pleos") { dimension = "env" }
        create("sim") { dimension = "env" }
    }

    // AAOS Car API (CarPropertyManager, CarUxRestrictionsManager). pleos 소스셋에서만 호출한다.
    useLibrary("android.car")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    api(project(":domain"))
    api(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.ktx)

    "pleosImplementation"(libs.pleos.vehicle)
    "pleosImplementation"(libs.pleos.navihelper)
    "pleosImplementation"(libs.pleos.fusedlocation)
    "pleosImplementation"(libs.pleos.tts)
    "pleosImplementation"(libs.pleos.stt)
}
