buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
        classpath("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.0")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:11.6.1")
    }
}

plugins {
    id("io.gitlab.arturbosch.detekt") version "1.23.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

detekt {
    config = files("detekt-config.yml")
    buildUponDefaultConfig = true
}

ktlint {
    debug = false
    ignoreFailures = false
}
