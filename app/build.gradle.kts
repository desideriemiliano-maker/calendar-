plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.desideri.viaggiotemplate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.desideri.viaggiotemplate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
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
    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.0")
    implementation("androidx.core:core-ktx:1.13.1")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Test (unit, JVM puro - non richiedono emulatore)
    testImplementation("junit:junit:4.13.2")
}

// Aggira il bug "Cannot obtain the package" della run configuration "Android App" di IntelliJ:
// builda, installa e avvia l'app sul dispositivo/emulatore collegato, usabile dal pannello
// Gradle di IntelliJ (Tasks > app > run > runOnDevice) o da `./gradlew runOnDevice`.
tasks.register<Exec>("runOnDevice") {
    group = "run"
    description = "Installa il debug APK e avvia MainActivity sul dispositivo collegato via adb"
    dependsOn("installDebug")
    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val adb = android.sdkDirectory.resolve("platform-tools/adb" + if (isWindows) ".exe" else "")
    commandLine(adb.absolutePath, "shell", "am", "start", "-n", "${android.namespace}/.MainActivity")
}
