// Top-level build file — plugins declared here (not applied) so each
// module applies only what it needs. Versions centralized here to avoid
// drift between modules if this ever grows past a single :app module.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.20" apply false
}
