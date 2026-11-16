import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// توليد أيقونة VYRO MUSIC الاحترافية (Adaptive Icon + Monochrome لأندرويد 13+) تلقائياً أثناء البناء
val resDrawableDir = File(projectDir, "src/main/res/drawable").apply { mkdirs() }

File(resDrawableDir, "ic_vyro_bg.xml").writeText(
    """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="108"
        android:viewportHeight="108">
        <path
            android:fillColor="#FF05050A"
            android:pathData="M0,0h108v108h-108z" />
        <path
            android:fillColor="#FF140D2A"
            android:pathData="M54,54m-48,0a48,48 0,1 1,96 0a48,48 0,1 1,-96 0" />
        <path
            android:fillColor="#FF1F123E"
            android:pathData="M54,54m-38,0a38,38 0,1 1,76 0a38,38 0,1 1,-76 0" />
    </vector>
    """.trimIndent()
)

File(resDrawableDir, "ic_vyro_fg.xml").writeText(
    """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="108"
        android:viewportHeight="108">
        <!-- حلقة النيون الذهبية/البنفسجية -->
        <path
            android:strokeColor="#FFA87BFF"
            android:strokeWidth="2.8"
            android:fillColor="#00000000"
            android:pathData="M54,54m-31,0a31,31 0,1 1,62 0a31,31 0,1 1,-62 0" />
        <!-- حرف V الهندسي الفاخر -->
        <path
            android:fillColor="#FFA87BFF"
            android:pathData="M35,36 L44,36 L54,60 L64,36 L73,36 L57,72 L51,72 Z" />
        <!-- نبضات الصوت الذهبية داخل الشعار -->
        <path
            android:fillColor="#FFFFC857"
            android:pathData="M52,32h4v16h-4zM45,38h3.5v10h-3.5zM59.5,38h3.5v10h-3.5z" />
    </vector>
    """.trimIndent()
)

File(resDrawableDir, "ic_vyro_launcher.xml").writeText(
    """
    <?xml version="1.0" encoding="utf-8"?>
    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
        <background android:drawable="@drawable/ic_vyro_bg" />
        <foreground android:drawable="@drawable/ic_vyro_fg" />
        <monochrome android:drawable="@drawable/ic_vyro_fg" />
    </adaptive-icon>
    """.trimIndent()
)

android {
    namespace = "com.mohamedtarek.vyromusic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mohamedtarek.vyromusic"
        // يعمل حصرياً على أندرويد 13 (API 33) فما فوق لضمان أعلى أداء وحداثة
        minSdk = 33
        targetSdk = 34
        versionCode = 4
        versionName = "3.0.0-ULTRA"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.2")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-session:1.3.1")
    implementation("io.coil-kt:coil-compose:2.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
