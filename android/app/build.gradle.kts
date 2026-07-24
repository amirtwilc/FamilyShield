plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

fun configuredApiBase(): String? = (project.findProperty("API_BASE") as String?)?.trimEnd('/')

gradle.taskGraph.whenReady {
    if (allTasks.any { it.name.contains("Release") }) {
        val apiBase = configuredApiBase()
            ?: throw GradleException("Release builds require -PAPI_BASE=https://your-production-backend")
        if (!apiBase.startsWith("https://")) {
            throw GradleException("Release API_BASE must use HTTPS, got: $apiBase")
        }
    }
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.familyshield.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.familyshield.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "1.0"
        // Backend base URL.
        //  - Dev default: http://10.0.2.2:3000 (the emulator's alias for the dev PC).
        //  - Physical phone on the LAN:  -PAPI_HOST=192.168.x.x  (-> http://IP:3000)
        //  - Deployed HTTPS server:      -PAPI_BASE=https://your-app.up.railway.app
        val debugApiBase = configuredApiBase()
            ?: "http://${(project.findProperty("API_HOST") as String?) ?: "10.0.2.2"}:3000"
        buildConfigField("String", "API_BASE_URL", "\"$debugApiBase\"")

        // Google sign-in: the OAuth *Web* client id (server client id). Blank hides
        // the button.  -PGOOGLE_CLIENT_ID=xxxxx.apps.googleusercontent.com
        val googleClientId = (project.findProperty("GOOGLE_CLIENT_ID") as String?) ?: ""
        buildConfigField("String", "GOOGLE_CLIENT_ID", "\"$googleClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            val releaseApiBase = configuredApiBase() ?: "https://missing-release-api-base.invalid"
            buildConfigField("String", "API_BASE_URL", "\"$releaseApiBase\"")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    val composeBom = platform("androidx.compose:compose-bom:2024.09.02")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // Material 3 Adaptive (ListDetailPaneScaffold — canonical list-detail layout)
    implementation("androidx.compose.material3.adaptive:adaptive:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.0.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.0.0")

    // Google sign-in via Credential Manager (no Google Maps — this is identity only)
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.firebase:firebase-messaging-ktx:24.0.2")

    // OpenStreetMap (never Google Maps, per project constraint)
    implementation("org.osmdroid:osmdroid-android:6.1.20")

    debugImplementation("androidx.compose.ui:ui-tooling")

    // Unit tests (JVM) — ViewModel logic with hand-written fakes
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}
