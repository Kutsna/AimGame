plugins {
    // Android Gradle Plugin
    id("com.android.application") version "8.8.0" apply false
    id("com.android.library")     version "8.8.0" apply false
    id("com.google.gms.google-services") version "4.4.3" apply false

    // Kotlin plugins (all modules will inherit 1.8.10)
    kotlin("jvm")      version "1.8.21" apply false
    kotlin("android")  version "1.8.21" apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
        classpath("com.google.gms:google-services:4.4.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}