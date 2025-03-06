pluginManagement {
    repositories {
        mavenCentral()
        google()
//        maven { url "https://maven.fabric.io/public" }
//        maven { url "https://jitpack.io" }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include("app")
include("inscription")
include("metadata-extractor")

include(":rawprocessor")
project(":rawprocessor").projectDir = file("rawprocessor/library")

include(":ssiv")
project(":ssiv").projectDir = file("ssiv/library")
