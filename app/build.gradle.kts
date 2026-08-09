import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The Maps SDK reads its key from the manifest, not BuildConfig, so it has to
// be injected as a placeholder at build time.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
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

// Real Guava already contains ListenableFuture, so keep the placeholder
// artifact out entirely rather than letting the two race to define the class.
configurations.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
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

        manifestPlaceholders["mapsApiKey"] =
            (localProps.getProperty("MAPS_API_KEY") ?: "").trim()
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
    implementation(libs.androidx.concurrent.futures.ktx)

    // CameraX hands back a Guava ListenableFuture. Play Services pulls in the
    // deliberately EMPTY "9999.0-empty-to-avoid-conflict-with-guava" stub of
    // that artifact, which wins on version and contains no classes at all —
    // hence "cannot access class ListenableFuture". Real Guava supplies it.
    implementation(libs.guava)

    // Data Layer link to the Wear OS companion (push today's takings to the watch)
    implementation(libs.play.services.wearable)

    // GPS + map picker for the receipt's location field
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)
    implementation(libs.maps.compose)

    // QR block printed on every memo
    implementation(libs.zxing.core)

    // Android Auto — glanceable takings while driving between stalls
    implementation(libs.androidx.car.app)
    implementation(libs.androidx.car.app.projected)

    // App Lock: fingerprint / face, with the device credential as fallback
    implementation(libs.androidx.biometric)
}
