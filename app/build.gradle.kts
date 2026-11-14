import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// release 서명 정보는 저장소 밖에 둔다: local.properties(깃 제외)의 release.storeFile·storePassword·keyAlias·keyPassword
val signing = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val hasReleaseKey = signing.getProperty("release.storeFile")?.let { file(it).exists() } == true

android {
    namespace = "com.hour.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.hour.app"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    flavorDimensions += "env"
    productFlavors {
        create("pleos") {
            dimension = "env"
        }
        create("sim") {
            dimension = "env"
            applicationIdSuffix = ".sim"
            versionNameSuffix = "-sim"
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(signing.getProperty("release.storeFile"))
                storePassword = signing.getProperty("release.storePassword")
                keyAlias = signing.getProperty("release.keyAlias")
                keyPassword = signing.getProperty("release.keyPassword")
                // Pleos App Market은 서명 키 교체(rotation·lineage)를 위해 API 28+를 요구한다. v3 서명을 함께 넣어 둔다.
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // 서명 키가 없으면 디버그 키로 서명한다(로컬 확인용). 제출용 APK는 scripts/release_apk.sh가 실제 키를 확인한다.
            signingConfig = if (hasReleaseKey) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
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
}

dependencies {
    implementation(project(":car"))
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
