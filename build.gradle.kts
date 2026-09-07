// Top-level build file for BECKY BRIDGE
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
    // KSP: strictly required for Room's annotation processor (Fase 3.2, Bloque 2 -
    // Memory persistence). Version pinned to match Kotlin 1.9.24 exactly, per KSP's
    // own compatibility table (KSP version = <kotlin-version>-<ksp-release>).
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
