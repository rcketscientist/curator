// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.10" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.1.10" apply false
//    id("org.jetbrains.kotlin.kapt") version "2.1.10" apply false
    id("com.google.devtools.ksp") version "2.1.10-1.0.31" apply false
//    id("io.fabric.tools") version "1.31.2" apply false
}

buildscript {

    extra.apply {
        set("kotlinVersion", "2.1.10")
        set("compileSdkVersion", "android-35")
        set("annotationVersion", "1.9.1")
        set("exifVersion", "1.4.0")
        set("materialVersion", "1.1.0") // TODO: Compose
        set("coreVersion", "1.15.0")
        set("lifecycleVersion", "2.8.7")
        set("roomVersion", "2.6.1")
        set("preferenceVersion", "1.2.1")
        set("pagingVersion", "3.3.6")
        set("archCoreVersion", "2.2.0")
        set("workVersion", "2.10.0")
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.9.0")
//        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        classpath("com.google.gms:google-services:4.3.10")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.6.21")
//        classpath("io.fabric.tools:gradle:1.31.2")
    }
    repositories {
        google()
    }
}
repositories {
    google()
    jcenter()
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/rcketscientist/*")
    }
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
    delete = setOf ( getLayout().buildDirectory)
}
