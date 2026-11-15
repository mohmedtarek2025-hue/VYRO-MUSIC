import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// توليد ملفات الويدجت تلقائياً أثناء البناء لضمان عمل ويدجت الشاشة الرئيسية بدون خطوات يدوية
val generatedResDir = File(projectDir, "src/main/res")
File(generatedResDir, "drawable").apply { mkdirs() }.resolve("vyro_widget_bg.xml").writeText(
    """<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
    <corners android:radius="22dp" />
    <solid android:color="#12131F" />
</shape>
"""
)

File(generatedResDir, "layout").apply { mkdirs() }.resolve("vyro_widget.xml").writeText(
    """<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@drawable/vyro_widget_bg"
    android:padding="0dp">

    <ImageView
        android:id="@+id/widget_album_art"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="fitXY"
        android:contentDescription="غلاف الأغنية" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:orientation="vertical"
        android:gravity="bottom"
        android:padding="12dp">

        <TextView
            android:id="@+id/widget_title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="VYRO MUSIC PRO"
            android:textColor="#FFFFFF"
            android:textSize="14sp"
            android:textStyle="bold"
            android:singleLine="true"
            android:ellipsize="end"
            android:shadowColor="#DD000000"
            android:shadowDx="0"
            android:shadowDy="2"
            android:shadowRadius="6" />

        <TextView
            android:id="@+id/widget_artist"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="Crafted by Mohamed Tarek"
            android:textColor="#E0D4FF"
            android:textSize="11sp"
            android:singleLine="true"
            android:ellipsize="end"
            android:shadowColor="#DD000000"
            android:shadowDx="0"
            android:shadowDy="1"
            android:shadowRadius="4" />

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center"
            android:layout_marginTop="8dp">

            <ImageButton
                android:id="@+id/widget_prev"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="@android:color/transparent"
                android:src="@android:drawable/ic_media_previous"
                android:tint="#FFFFFF"
                android:contentDescription="الأغنية السابقة" />

            <ImageButton
                android:id="@+id/widget_play_pause"
                android:layout_width="48dp"
                android:layout_height="48dp"
                android:layout_marginLeft="14dp"
                android:layout_marginRight="14dp"
                android:background="@android:color/transparent"
                android:src="@android:drawable/ic_media_play"
                android:tint="#FFFFFF"
                android:contentDescription="تشغيل أو إيقاف مؤقت" />

            <ImageButton
                android:id="@+id/widget_next"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="@android:color/transparent"
                android:src="@android:drawable/ic_media_next"
                android:tint="#FFFFFF"
                android:contentDescription="الأغنية التالية" />
        </LinearLayout>
    </LinearLayout>
</FrameLayout>
"""
)

File(generatedResDir, "xml").apply { mkdirs() }.resolve("vyro_widget_info.xml").writeText(
    """<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="130dp"
    android:minHeight="130dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/vyro_widget"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen|keyguard" />
"""
)

android {
    namespace = "com.mohamedtarek.vyromusic"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mohamedtarek.vyromusic"
        minSdk = 26
        targetSdk = 35
        versionCode = 20
        versionName = "2.0.0-PRO"
    }

    buildTypes {
        release {
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-common:1.4.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
