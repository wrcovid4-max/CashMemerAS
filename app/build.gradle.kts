plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Firebase is optional. The google-services plugin hard-fails when its config
 * file is absent, so only apply it once the file is actually there — that way
 * a fresh clone still builds and runs, just with cloud sync switched off.
 */
val firebaseConfig = project.file("google-services.json")
if (firebaseConfig.exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.cashmemer"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cashmemer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en", "ur")

        // Lets the app tell "Firebase not set up" apart from "sync failed".
        buildConfigField("boolean", "FIREBASE_CONFIGURED", firebaseConfig.exists().toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
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
    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)

    // Scheduled offline backups written into a folder the user picks
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.documentfile)

    // Google sign-in through Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    // Cloud sync. These compile with or without a google-services.json; the
    // app checks at runtime whether Firebase actually initialised.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Camera + barcode for Scan Receipt / Barcode Scan
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // GPS for the receipt's location field
    implementation(libs.play.services.location)
}
