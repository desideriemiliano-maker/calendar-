import java.io.ByteArrayOutputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/** Esegue git nella root del progetto; stringa vuota se git non è disponibile o non è un repository. */
fun runGit(vararg args: String): String = try {
    val out = ByteArrayOutputStream()
    project.exec {
        workingDir = rootProject.projectDir
        commandLine(listOf("git") + args.toList())
        standardOutput = out
        isIgnoreExitValue = true
    }
    out.toString(Charsets.UTF_8.name()).trim()
} catch (e: Exception) {
    ""
}

val gitCommitCount: Int = runGit("rev-list", "--count", "HEAD").toIntOrNull()?.coerceAtLeast(1) ?: 1

fun escapeKotlin(testo: String): String = testo.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$")

/**
 * Genera il sorgente Kotlin `Changelog.kt` con una voce per ogni commit git (versionCode = posizione
 * nella storia, coerente con `gitCommitCount`/`versionCode` dell'app), da mostrare nel menu
 * "Versioni" di Impostazioni. Se non c'è storia git (repo appena creato, nessun commit), la lista
 * risulta vuota: la UI lo gestisce senza errori.
 */
fun generaSorgenteChangelog(): String {
    val log = runGit("log", "--reverse", "--date=short", "--pretty=format:%ad@@@%s")
    val voci = StringBuilder()
    if (log.isNotBlank()) {
        log.lines().forEachIndexed { indice, riga ->
            val parti = riga.split("@@@", limit = 2)
            if (parti.size == 2) {
                voci.append("    VoceChangelog(versionCode = ${indice + 1}, data = \"${parti[0]}\", messaggio = \"${escapeKotlin(parti[1])}\"),\n")
            }
        }
    }
    return """
        |package com.desideri.viaggiotemplate.changelog
        |
        |data class VoceChangelog(val versionCode: Int, val data: String, val messaggio: String)
        |
        |val CHANGELOG: List<VoceChangelog> = listOf(
        |$voci)
        |
    """.trimMargin()
}

val changelogGeneratoDir = layout.buildDirectory.dir("generated/changelog")

val generaChangelog = tasks.register("generaChangelog") {
    val outputDir = changelogGeneratoDir
    outputs.dir(outputDir)
    doLast {
        val pacchettoDir = File(outputDir.get().asFile, "com/desideri/viaggiotemplate/changelog")
        pacchettoDir.mkdirs()
        File(pacchettoDir, "Changelog.kt").writeText(generaSorgenteChangelog())
    }
}

android {
    namespace = "com.desideri.viaggiotemplate"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.desideri.viaggiotemplate"
        minSdk = 26
        targetSdk = 35
        versionCode = gitCommitCount
        versionName = "1.0.$gitCommitCount"
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

    sourceSets {
        getByName("main") {
            java.srcDir(changelogGeneratoDir)
        }
    }
}

tasks.matching { it.name.contains("Kotlin") }.configureEach {
    dependsOn(generaChangelog)
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
