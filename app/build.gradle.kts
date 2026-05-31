import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// ── Git-derived versioning ────────────────────────────────────────────────────
// versionCode: total commit count — monotonically increasing, never needs a manual bump.
// versionName: `git describe` — shows the nearest tag (e.g. "v1.1"), or
//              "v1.1-4-gabc1234" for 4 commits past that tag, or just the
//              short hash when no tags exist yet. "-dirty" appended for
//              uncommitted changes. Tag a commit to give a build a clean name.
fun git(vararg args: String): String = try {
    ProcessBuilder("git", *args)
        .directory(rootProject.projectDir)
        .start()
        .inputStream
        .bufferedReader()
        .readText()
        .trim()
} catch (_: Exception) { "" }

fun gitRevCount(): Int = git("rev-list", "--count", "HEAD").toIntOrNull() ?: 1
fun gitDescribe(): String = git("describe", "--tags", "--always", "--dirty").ifEmpty { "dev" }

android {
    namespace = "com.oddley.next"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.oddley.next"
        minSdk = 31
        targetSdk = 36
        versionCode = gitRevCount()
        versionName = gitDescribe()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
    }
    // Room schema export location (required when exportSchema = true)
    ksp {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

dependencies {
    // Compose BOM — manages all androidx.compose.* versions
    implementation(platform(libs.androidx.compose.bom))

    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose UI
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Material Icons Extended (Add, Check, DragHandle, etc.)
    implementation(libs.androidx.compose.material.icons.extended)

    // Drag-and-drop reorder for LazyColumn
    implementation(libs.reorderable)

    // RFC 5545 recurrence rule parsing and iteration (Task Emitters)
    implementation(libs.dmfs.lib.recur)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // JVM unit tests — JUnit 5 (Jupiter)
    testImplementation(libs.junit.jupiter)
    // Gradle 9.x needs the Platform Launcher explicitly on the runtime classpath
    testRuntimeOnly(libs.junit.platform.launcher)

    // Instrumented tests
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
