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

// APK size controls (see docs): -PapkAbi=arm64-v8a builds a single-ABI APK; -PcompressNativeLibs compresses
// native libraries inside downloadable APKs. Play bundles (.aab) use neither: Play optimises those itself.
val apkAbi: String? = providers.gradleProperty("apkAbi").orNull
val compressNativeLibs: Boolean = providers.gradleProperty("compressNativeLibs").isPresent

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
        manifestPlaceholders["appLabel"] = "@string/app_name"
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
            // Separate package + name, so a test build installs next to the real app instead of conflicting with it.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "Kharcha Check Test"
        }
        release {
            // Real Android phones are ARM. x86/x86_64 are only for PC emulators and cost ~28 MB, so they're
            // left out of release builds (debug/test builds keep them for emulators).
            ndk {
                abiFilters.clear()
                abiFilters += apkAbi?.split(",")?.map { it.trim() } ?: listOf("arm64-v8a", "armeabi-v7a")
            }
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
        // PdfBox ships ~3.4 MB of Chinese/Japanese/Korean font maps that Indian statements never use.
        // Identity-H/V (needed by almost every PDF) are kept. A CJK-font PDF simply falls back to OCR.
        ignoreAssetsPatterns += listOf(
            "<file>83pv-*", "<file>90ms*", "<file>90pv*", "<file>Add-*", "<file>Adobe-*", "<file>B5pc-*",
            "<file>CNS-*", "<file>ETen*", "<file>EUC-*", "<file>Ext-*", "<file>GB*", "<file>HKscs-*",
            "<file>KSC*", "<file>Uni*", "<file>H", "<file>V",
        )
    }
    packaging {
        jniLibs {
            useLegacyPackaging = compressNativeLibs
        }
        resources.excludes += listOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/*.version",
            "DebugProbesKt.bin",
            "kotlin-tooling-metadata.json",
            // Bouncy Castle (pulled in by PdfBox for PDF decryption) bundles ~4 MB of post-quantum
            // crypto tables and certificate-review message files that are never used.
            "org/bouncycastle/pqc/**",
            "org/bouncycastle/x509/*.properties",
        )
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
