plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.smartskan"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.smartskan.aab"
        minSdk = 24
        targetSdk = 37
        versionCode = 9
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Live AdMob IDs from the AdMob console.
        manifestPlaceholders["admobAppId"] = "ca-app-pub-9788753435817251~9995541876"
        buildConfigField("String", "BANNER_AD_UNIT_ID", "\"ca-app-pub-9788753435817251/6695250250\"")
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("com.smartskan.**")
            }
            proguardFiles("proguard-rules.pro")
            // Puts native debug symbols in the bundle (from library .so files such as ML Kit's),
            // so Play Console can show readable native crash and ANR stack traces.
            // Needs the NDK installed (SDK Manager → SDK Tools → NDK (Side by side)).
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    androidResources {
        // Lists the app's languages for Android 13+ system settings, from the res/values-* folders.
        generateLocaleConfig = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Per-app language picker, including on Android versions before 13
    implementation(libs.androidx.appcompat)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // On-device ML Kit scanners (camera UI provided by Google Play services — no CAMERA permission needed)
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.code.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)

    // PDF merge and text extraction (Merge PDFs, Create booklet and PDF to Word tools)
    implementation(libs.pdfbox.android)

    // Banner ads, UK/EEA consent form, and the one-off "Remove ads" purchase
    implementation(libs.play.services.ads)
    implementation(libs.ump)
    implementation(libs.billing.ktx)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
