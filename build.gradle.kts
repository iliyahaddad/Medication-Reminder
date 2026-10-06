// Top-level build file. Plugin versions are declared in the version catalog
// (gradle/libs.versions.toml) and applied with `apply false` so that all
// modules share a single resolved classpath.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

// Detekt configuration shared by every module.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
    parallel = true
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
