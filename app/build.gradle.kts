plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

val releaseKeystore: String? = System.getenv("KC_KEYSTORE_PATH")
val buildNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.kharchacheck.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kharchacheck.app"
        minSdk = 26
        targetSdk = 36
        // v2 starts at 1000 so it is always newer than any v1 build already installed or uploaded.
        versionCode = 1000 + buildNumber
        versionName = "2.0.$buildNumber"
        buildConfigField("String", "SUPPORT_EMAIL", "\"tosourav6@gmail.com\"")
    }

    // Two editions from one codebase:
    //  standard: no SMS permissions at all. Ship this first; Play approves it with no declaration.
    //  smart:    adds optional, user-enabled SMS reading. Needs the Play "SMS-based money management"
    //            permission declaration to be approved before you can publish it.
    flavorDimensions += "capture"
    productFlavors {
        create("standard") {
            dimension = "capture"
            isDefault = true
            buildConfigField("boolean", "SMS_CAPTURE", "false")
        }
        create("smart") {
            dimension = "capture"
            buildConfigField("boolean", "SMS_CAPTURE", "true")
        }
    }

    signingConfigs {
        create("release") {
            if (releaseKeystore != null) {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("KC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KC_KEY_ALIAS")
                keyPassword = System.getenv("KC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = if (releaseKeystore != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    androidResources {
        noCompress += "traineddata"
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    lint {
        htmlReport = true
        checkReleaseBuilds = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.core.ktx)
    implementation(libs.splashscreen)
    implementation(libs.activity.compose)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)
    implementation(libs.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.sqlcipher)
    implementation(libs.sqlite)

    implementation(libs.datastore.preferences)
    implementation(libs.biometric)
    implementation(libs.work.runtime.ktx)
    implementation(libs.documentfile) // auto-backup into a user-chosen folder (e.g. Google Drive)
    implementation(libs.coroutines.android)
    implementation(libs.tesseract) // offline OCR for screenshots and statements (Apache-2.0)
    implementation(libs.pdfbox) // exact text from digital PDF statements, incl. password-protected (Apache-2.0)

    testImplementation(libs.junit)
}
