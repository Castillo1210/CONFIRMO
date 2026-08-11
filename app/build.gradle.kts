import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// Configuración de entorno leída desde local.properties (no versionado).
// Ver local.properties.example para la plantilla y los valores esperados.
// Los defaults de abajo solo existen como red de seguridad si alguien
// compila sin haber configurado local.properties; el valor real de cada
// desarrollador/entorno debe vivir únicamente en ese archivo.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        load(FileInputStream(localPropertiesFile))
    }
}

fun envConfig(key: String, default: String): String =
    (localProperties.getProperty(key) ?: System.getenv(key) ?: default)

android {
    namespace = "com.example.tconfirmo"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.tconfirmo"
        minSdk = 24
        targetSdk = 36
        versionCode = 20
        versionName = "1.19"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "UPDATE_METADATA_URL",
            "\"https://raw.githubusercontent.com/Castillo1210/CONFIRMO/main/update/version.json\""
        )
        // Ya no hay URLs hardcodeadas aquí: ambos valores se leen de
        // local.properties (no versionado). Ver local.properties.example.
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${envConfig("API_BASE_URL", "https://n5vqr8.tyresperu.com/")}\""
        )
        buildConfigField(
            "String",
            "SIGNALR_HUB_URL",
            "\"${envConfig("SIGNALR_HUB_URL", "https://n5vqr8.tyresperu.com/hubs/deposits")}\""
        )
    }

    signingConfigs {
        // Firma de release leída desde local.properties (no versionado).
        // Ver local.properties.example para las claves esperadas:
        //   RELEASE_STORE_FILE, RELEASE_STORE_PASSWORD,
        //   RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
        // Solo se registra la config si RELEASE_STORE_FILE existe, para que
        // el build siga funcionando (sin firmar) en entornos sin keystore.
        val storeFilePath = envConfig("RELEASE_STORE_FILE", "")
        if (storeFilePath.isNotBlank() && rootProject.file(storeFilePath).exists()) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = envConfig("RELEASE_STORE_PASSWORD", "")
                keyAlias = envConfig("RELEASE_KEY_ALIAS", "")
                keyPassword = envConfig("RELEASE_KEY_PASSWORD", "")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            optimization {
                enable = false
            }
        }
    }
    lint {
        baseline = file("lint-baseline.xml")
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)

    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.signalr)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation("com.google.firebase:firebase-analytics")
}
