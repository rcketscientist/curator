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
//include(":documentActivity")
//project(":documentActivity").projectDir = file("DocumentActivity/library")

//include(":showcaseView")
//project(":showcaseView").projectDir = file("showcaseView/library")

include(":rawprocessor")
project(":rawprocessor").projectDir = file("rawprocessor/library")

include(":ssiv")
project(":ssiv").projectDir = file("ssiv/library")
