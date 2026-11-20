import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val resDir = File(projectDir, "src/main/res")
val drawableV26Dir = File(resDir, "drawable-v26").apply { mkdirs() }
val drawableDir = File(resDir, "drawable").apply { mkdirs() }
val xmlDir = File(resDir, "xml").apply { mkdirs() }
val layoutDir = File(resDir, "layout").apply { mkdirs() }

File(drawableDir, "ic_vyro_bg.xml").writeText(
    """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="108"
        android:viewportHeight="108">
        <path android:fillColor="#FF05060A" android:pathData="M0,0h108v108h-108z"/>
        <path android:fillColor="#FF0D1426" android:pathData="M54,54m-46,0a46,46 0,1 1,92 0a46,46 0,1 1,-92 0"/>
        <path android:fillColor="#FF12203E" android:pathData="M54,54m-35,0a35,35 0,1 1,70 0a35,35 0,1 1,-70 0"/>
    </vector>
    """.trimIndent()
)

File(drawableDir, "ic_vyro_fg.xml").writeText(
    """
    <?xml version="1.0" encoding="utf-8"?>
    <vector xmlns:android="http://schemas.android.com/apk/res/android"
        android:width="108dp"
        android:height="108dp"
        android:viewportWidth="108"
        android:viewportHeight="108">
        <path
            android:strokeColor="#FF2196F3"
            android:strokeWidth="3.0"
            android:fillColor="#00000000"
            android:pathData="M54,54m-31,0a31,31 0,1 1,62 0a31,31 0,1 1,-62 0"/>
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M34,36 L43,36 L54,62 L65,36 L74,36 L57,74 L51,74 Z"/>
        <path
            android:fillColor="#FF2196F3"
            android:pathData="M52,30h4v16h-4zM45,36h3.5v10h-3.5zM59.5,36h3.5v10h-3.5z"/>
    </vector>
    """.trimIndent()
)

File(drawableV26Dir, "ic_vyro_launcher.xml").writeText(
    """
    <?xml version="1.0" encoding="utf-8"?>
    <adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
        <background android:drawable="@drawable/ic_vyro_bg"/>
        <foreground android:drawable="@drawable/ic_vyro_fg"/>
        <monochrome android:drawable="@drawable/ic_vyro_fg"/>
    </adaptive-icon>
    """.trimIndent()
)

File(layoutDir, "vyro_widget.xml").writeText(
    """
    <?xml version="1.0" encoding="utf-8"?>
    <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
        android:id="@+id/widget_root"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:padding="2dp">

        <ImageView
            android:id="@+id/widget_bg"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scaleType="fitXY" />

        <LinearLayout
            android:id="@+id/widget_wide_box"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:paddingStart="12dp"
            android:paddingEnd="10dp"
            android:paddingTop="8dp"
            android:paddingBottom="8dp">

            <ImageView
                android:id="@+id/widget_art_wide"
                android:layout_width="56dp"
                android:layout_height="56dp"
                android:scaleType="centerCrop"
                android:src="@drawable/ic_vyro_fg" />

            <LinearLayout
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:orientation="vertical"
                android:paddingStart="12dp"
                android:paddingEnd="6dp">

                <TextView
                    android:id="@+id/widget_title_wide"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="VYRO MUSIC"
                    android:textColor="#FFFFFFFF"
                    android:textStyle="bold"
                    android:textSize="14sp"
                    android:shadowColor="#CC000000"
                    android:shadowRadius="4"
                    android:singleLine="true"
                    android:ellipsize="end" />

                <TextView
                    android:id="@+id/widget_artist_wide"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Mohamed Tarek"
                    android:textColor="#FFDDE2F5"
                    android:textSize="11sp"
                    android:shadowColor="#CC000000"
                    android:shadowRadius="4"
                    android:singleLine="true"
                    android:ellipsize="end" />
            </LinearLayout>

            <ImageButton
                android:id="@+id/widget_prev_wide"
                android:layout_width="34dp"
                android:layout_height="34dp"
                android:background="@android:color/transparent"
                android:src="@android:drawable/ic_media_previous" />

            <ImageButton
                android:id="@+id/widget_play_wide"
                android:layout_width="40dp"
                android:layout_height="40dp"
                android:background="@android:color/transparent"
                android:src="@android:drawable/ic_media_play" />

            <ImageButton
                android:id="@+id/widget_next_wide"
                android:layout_width="34dp"
                android:layout_height="34dp"
                android:background="@android:color/transparent"
                android:src="@android:drawable/ic_media_next" />
        </LinearLayout>

        <LinearLayout
            android:id="@+id/widget_square_box"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:orientation="vertical"
            android:gravity="bottom|center_horizontal"
            android:padding="12dp"
            android:visibility="gone">

            <ImageView
                android:id="@+id/widget_art_square"
                android:layout_width="68dp"
                android:layout_height="68dp"
                android:layout_marginBottom="6dp"
                android:scaleType="centerCrop"
                android:src="@drawable/ic_vyro_fg" />

            <TextView
                android:id="@+id/widget_title_square"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:text="VYRO MUSIC"
                android:textColor="#FFFFFFFF"
                android:textStyle="bold"
                android:textSize="14sp"
                android:shadowColor="#EE000000"
                android:shadowRadius="6"
                android:singleLine="true"
                android:ellipsize="end" />

            <TextView
                android:id="@+id/widget_artist_square"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:text="Mohamed Tarek"
                android:textColor="#FFE4E8F7"
                android:textSize="11sp"
                android:shadowColor="#EE000000"
                android:shadowRadius="6"
                android:singleLine="true"
                android:ellipsize="end" />

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="6dp"
                android:orientation="horizontal"
                android:gravity="center">

                <ImageButton
                    android:id="@+id/widget_prev_square"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:background="@android:color/transparent"
                    android:src="@android:drawable/ic_media_previous" />

                <ImageButton
                    android:id="@+id/widget_play_square"
                    android:layout_width="42dp"
                    android:layout_height="42dp"
                    android:layout_marginStart="6dp"
                    android:layout_marginEnd="6dp"
                    android:background="@android:color/transparent"
                    android:src="@android:drawable/ic_media_play" />

                <ImageButton
                    android:id="@+id/widget_next_square"
                    android:layout_width="36dp"
                    android:layout_height="36dp"
                    android:background="@android:color/transparent"
                    android:src="@android:drawable/ic_media_next" />
            </LinearLayout>
        </LinearLayout>
    </FrameLayout>
    """.trimIndent()
)

File(xmlDir, "vyro_widget_info.xml").writeText(
    """
    <?xml version="1.0" encoding="utf-8"?>
    <appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
        android:minWidth="140dp"
        android:minHeight="70dp"
        android:minResizeWidth="110dp"
        android:minResizeHeight="60dp"
        android:targetCellWidth="2"
        android:targetCellHeight="2"
        android:updatePeriodMillis="1800000"
        android:initialLayout="@layout/vyro_widget"
        android:resizeMode="horizontal|vertical"
        android:widgetCategory="home_screen" />
    """.trimIndent()
)

android {
    namespace = "com.mohamedtarek.vyromusic"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mohamedtarek.vyromusic"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "4.0.0-APEX"
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
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.02.02")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.media3:media3-exoplayer:1.2.1")
    implementation("androidx.media3:media3-session:1.2.1")
    implementation("io.coil-kt:coil-compose:2.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}
