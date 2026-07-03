import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
    alias(libs.plugins.firebase.performance)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

fun localProp(key: String) = localProps.getProperty(key) ?: System.getenv(key) ?: ""
fun escapedProp(key: String) = localProp(key).replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.dhanuk.debtbro"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dhanuk.debtbro"
        // minSdk was bumped to 29 on 2026-07-03 to drop the legacy
        // CsvExporter path, but that locked out users on Android 9 / API
        // 28 and below — they see a "There was a problem parsing the
        // package" error in the install dialog because Android silently
        // refuses APKs whose minSdk > device API level.
        //
        // Backed out to 26 on 2026-07-03. To support the legacy API 26-28
        // path we:
        //   - re-added WRITE_EXTERNAL_STORAGE with maxSdkVersion=28 in
        //     AndroidManifest.xml
        //   - re-added the getExternalStoragePublicDirectory code path in
        //     CsvExporter.kt, gated by Build.VERSION.SDK_INT < Q
        // The MediaStore path remains the default on API 29+.
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: localProp("VERSION_CODE").toIntOrNull() ?: 1)
        versionName = localProp("VERSION_NAME").ifEmpty { "1.0.0" }
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AI uses Gemini Flash 2.5 (2026-07-03)
        buildConfigField("String", "GEMINI_API_KEY", "\"${escapedProp("GEMINI_API_KEY")}\"")
        buildConfigField("String", "GEMINI_API_KEY_2_5_FLASH_LITE", "\"${escapedProp("GEMINI_API_KEY_2_5_FLASH_LITE")}\"")
        buildConfigField("String", "ADMOB_APP_ID", "\"${escapedProp("ADMOB_APP_ID")}\"")
        buildConfigField("String", "ADMOB_BANNER_ID", "\"${escapedProp("ADMOB_BANNER_ID")}\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"${escapedProp("ADMOB_INTERSTITIAL_ID")}\"")
        buildConfigField("String", "ADMOB_REWARDED_ID", "\"${escapedProp("ADMOB_REWARDED_ID")}\"")
        buildConfigField("String", "ONESIGNAL_APP_ID", "\"${escapedProp("ONESIGNAL_APP_ID")}\"")
        // ONESIGNAL_API_KEY is a REST API server secret - ideally should be in Cloud Functions only
        // Included here for backward compatibility with legacy OneSignal dashboard push workflows
        buildConfigField("String", "ONESIGNAL_API_KEY", "\"${escapedProp("ONESIGNAL_API_KEY")}\"")

        buildConfigField("String", "PACKAGE_NAME", "\"${escapedProp("PACKAGE_NAME").ifEmpty { "com.dhanuk.debtbro" }}\"")
        buildConfigField("Boolean", "ENABLE_CRASHLYTICS", "true")
        buildConfigField("Boolean", "ENABLE_PERFORMANCE_MONITORING", "true")
        buildConfigField("String", "PRIVACY_POLICY_URL", "\"${escapedProp("PRIVACY_POLICY_URL").ifEmpty { "https://dhanuk.page.gd/DebtBro/Privacy-Policy.html" }}\"")
        buildConfigField("String", "TERMS_OF_SERVICE_URL", "\"https://dhanuk.page.gd/DebtBro/Terms-and-Conditions.html\"")
        buildConfigField("String", "HELP_URL", "\"https://dhanuk.page.gd/DebtBro/Help-and-Support.html\"")
        buildConfigField("String", "ACCOUNT_DELETION_URL", "\"${localProp("ACCOUNT_DELETION_URL").ifEmpty { "https://us-central1-${localProp("FIREBASE_PROJECT_ID").ifEmpty { "debtbro-4e3c9" }}.cloudfunctions.net/requestAccountDeletion" }}\"")
        // App-level ADMOB_APP_ID is a manifest placeholder only. Real ad unit IDs live in BuildConfig above.
        // The fallback is only used when local.properties / CI is missing config — production builds must ship real IDs.
        manifestPlaceholders["ADMOB_APP_ID"] = localProp("ADMOB_APP_ID").ifEmpty {
            if (project.hasProperty("debuggable") && !rootProject.findProperty("debug")?.toString().equals("true", ignoreCase = true).or(false))
                error("ADMOB_APP_ID must be set in local.properties or CI secrets for release builds")
            else "ca-app-pub-3940256099942544~3347511713"
        }
    }

    signingConfigs {
        create("release") {
            storeFile = file("keystore.jks")
            storePassword = localProp("KEYSTORE_PASSWORD")
            keyAlias = "mykey"
            keyPassword = localProp("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (file("keystore.jks").exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        dex { useLegacyPackaging = false }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.compose.bom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.performance)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.auth)
    implementation(libs.googleid)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.admob)
    implementation(libs.onesignal)
    implementation(libs.vico.compose)
    implementation(libs.konfetti)
    implementation(libs.coil)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.androidx.material)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
