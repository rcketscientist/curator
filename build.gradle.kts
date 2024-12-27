// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "7.4.0" apply false
//    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    id("org.jetbrains.kotlin.kapt") version "2.1.0" apply false
//    id("io.fabric.tools") version "1.31.2" apply false
}

buildscript {

    extra.apply {
        set("kotlinVersion", "2.1.0")
        set("compileSdkVersion", "android-28")
        set("annotationVersion", "1.1.0")
        set("exifVersion", "1.0.0")
        set("materialVersion", "1.1.0")
        set("coreVersion", "1.3.1")
        set("lifecycleVersion", "2.2.0")
        set("roomVersion", "2.2.5")
        set("roomVersion", "2.2.5")
        set("pagingVersion", "2.1.2")
        set("coreTestingVersion", "2.1.0")
        set("workVersion", "2.4.0")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:7.3.1")
//        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.google.gms:google-services:4.3.3")
//        classpath("io.fabric.tools:gradle:1.31.2")
    }
    repositories {
        google()
    }
}
repositories {
    google()
}

//allprojects {
//    repositories {
//        jcenter()
//        google()
//        maven {
//            url "https://maven.google.com/"
//        }
//    }
//}

tasks.create<Delete>("clean") {
    delete = setOf ( rootProject.buildDir )
}
