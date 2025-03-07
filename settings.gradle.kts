pluginManagement {
    repositories {
        mavenCentral()
        google()
//        maven { url "https://maven.fabric.io/public" }
//        maven { url "https://jitpack.io" }
    }
}
plugins {
//    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include("app")
include("inscription")
include("metadata-extractor")

include(":rawprocessor")
project(":rawprocessor").projectDir = file("rawprocessor/library")

include(":ssiv")
project(":ssiv").projectDir = file("ssiv/library")
