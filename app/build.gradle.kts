import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.secrets.gradle)
    alias(libs.plugins.firebase.crashlytics) apply false
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
} else {
    logger.lifecycle("Firebase disabled: app/google-services.json not found.")
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
} else {
    logger.lifecycle("Release signing disabled: keystore.properties not found.")
}
val releaseKeystoreFile = keystoreProperties
    .getProperty("storeFile")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { configuredPath -> rootProject.file(configuredPath) }
    ?.takeIf { it.exists() }
val hasReleaseKeystore = releaseKeystoreFile != null
if (keystorePropertiesFile.exists() && !hasReleaseKeystore) {
    logger.lifecycle("Release signing disabled: keystore file from keystore.properties was not found.")
}

val localProperties = Properties().apply {
    val source = rootProject.file("local.properties")
    if (source.exists()) {
        source.inputStream().use { load(it) }
    }
}

fun readLocalProperty(name: String, defaultValue: String): String {
    return localProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() } ?: defaultValue
}

val trilooBackendUrl = readLocalProperty(
    name = "TRILOO_BACKEND_URL",
    // Backend Triloo переехал на HTTPS (Caddy + Lets Encrypt) — старый HTTP
    // default ломал auth, потому что network_security_config теперь HTTPS-only.
    defaultValue = "https://triloo.85.192.61.86.nip.io/"
).let { value ->
    if (value.endsWith("/")) value else "$value/"
}
val mapkitApiKey = readLocalProperty(
    name = "MAPKIT_API_KEY",
    defaultValue = ""
)
val mapkitMapEnabled = readLocalProperty(
    name = "MAPKIT_MAP_ENABLED",
    defaultValue = "false"
).equals("true", ignoreCase = true)
val geosuggestApiKey = readLocalProperty(
    name = "GEOSUGGEST_API_KEY",
    defaultValue = ""
)
val geoapifyApiKey = readLocalProperty(
    name = "GEOAPIFY_API_KEY",
    defaultValue = ""
)
val geminiApiKeys = readLocalProperty(
    name = "GEMINI_API_KEYS",
    defaultValue = ""
)
val openRouteServiceApiKey = readLocalProperty(
    name = "OPENROUTESERVICE_API_KEY",
    defaultValue = ""
)

android {
    namespace = "com.triloo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.triloo"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "1.0.5"
        buildConfigField("String", "APP_TRILOO_BACKEND_URL", "\"$trilooBackendUrl\"")
        buildConfigField("String", "APP_MAPKIT_API_KEY", "\"$mapkitApiKey\"")
        buildConfigField("boolean", "APP_MAPKIT_VIEW_ENABLED", mapkitMapEnabled.toString())
        buildConfigField("String", "APP_GEOSUGGEST_API_KEY", "\"$geosuggestApiKey\"")
        buildConfigField("String", "APP_GEOAPIFY_API_KEY", "\"$geoapifyApiKey\"")
        buildConfigField("String", "APP_GEMINI_API_KEYS", "\"$geminiApiKeys\"")
        buildConfigField("String", "APP_OPENROUTESERVICE_API_KEY", "\"$openRouteServiceApiKey\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

secrets {
    propertiesFileName = "secrets.properties"
    defaultPropertiesFileName = "local.defaults.properties"
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Retrofit + OkHttp
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // DataStore
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Coil (images)
    implementation(libs.coil.compose)

    // Splash Screen
    implementation(libs.androidx.splashscreen)

    // Haze — backdrop-blur для liquid-glass нав-бара.
    implementation(libs.haze)

    // Location / OCR
    implementation(libs.play.services.location)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.yandex.mapkit)

    // Feature modules
    implementation(project(":feature-map"))

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth.ktx)
    implementation(libs.firebase.firestore.ktx)
    implementation(libs.firebase.crashlytics.ktx)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
