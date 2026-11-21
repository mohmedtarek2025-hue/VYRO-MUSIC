import java.security.KeyStore

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

base {
    archivesName.set("VYRO_MUSIC_5.0.1_TITAN")
}

val vyroKeystoreFile = file("vyro.jks")
val vyroKeyPass = (System.getenv("VYRO_KEY_PASS") ?: "vyro2026").trim()
val vyroKeyAlias = if (vyroKeystoreFile.exists()) {
    runCatching {
        val ks = KeyStore.getInstance(vyroKeystoreFile, vyroKeyPass.toCharArray())
        val aliases = ks.aliases()
        var found: String? = null
        while (aliases.hasMoreElements()) {
            val candidate = aliases.nextElement()
            if (ks.isKeyEntry(candidate)) {
                found = candidate
                break
            }
        }
        found
    }.getOrNull() ?: "vyro"
} else {
    "vyro"
}

android {
    namespace = "com.mohamedtarek.vyromusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mohamedtarek.vyromusic"
        minSdk = 24
        targetSdk = 35
        versionCode = 12
        versionName = "5.0.1-TITAN"
    }

    signingConfigs {
        if (vyroKeystoreFile.exists()) {
            create("vyro") {
                storeFile = vyroKeystoreFile
                storePassword = vyroKeyPass
                keyAlias = vyroKeyAlias
                keyPassword = vyroKeyPass
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (vyroKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("vyro")
            }
        }
        getByName("release") {
            isMinifyEnabled = false
            if (vyroKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("vyro")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
