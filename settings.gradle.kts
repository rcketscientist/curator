pluginManagement {
    repositories {
        mavenCentral()
        google()
//        maven {
//            url = uri("https://maven.pkg.github.com/rcketscientist/*")
//        }
//        maven { url "https://maven.fabric.io/public" }
//        maven { url "https://jitpack.io" }
    }
}

include("app")
include("inscription")
include("metadata-extractor")
//include(":documentActivity")
//project(":documentActivity").projectDir = file("DocumentActivity/library")

include(":showcaseView")
project(":showcaseView").projectDir = file("showcaseView/library")

include(":rawprocessor")
project(":rawprocessor").projectDir = file("rawprocessor/library")

include(":ssiv")
project(":ssiv").projectDir = file("ssiv/library")
