plugins {
    id("com.android.application")
    id("kotlin-android")
    id("kotlin-kapt")
    id("kotlin-parcelize")
//    id("kotlin-android-extensions")
//    id("io.fabric")
}

android {
    val versionMajor = 6
    val versionMinor = 2
    val versionPatch = 5
    val schemaLocation = "$projectDir/schemas"

    buildFeatures {
        viewBinding = true
    }

    compileSdkVersion = rootProject.extra["compileSdkVersion"] as String
    lint {
//        checkReleaseBuilds(false)
        // Or, if you prefer, you can continue to check for errors in release builds,
        // but continue the build even when errors are found:
//        abortOnError false
    }

    defaultConfig {
        minSdk = 21
        targetSdk = 29

        versionCode = versionMajor * 100000 + versionMinor * 1000 + versionPatch
        versionName = "${versionMajor}.${versionMinor}.${versionPatch}"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments += mapOf(
                    "room.schemaLocation" to schemaLocation
                )
            }
        }

        // Specifies the fully-qualified class name of the test instrumentation runner.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        getByName("androidTest").assets.srcDir(schemaLocation)
    }

    signingConfigs {
        create("release") {
            storeFile = file(project.properties["RELEASE_STORE_FILE"] as String? ?: "dummy")
            storePassword = project.properties["RELEASE_STORE_PASSWORD"] as String? ?: "dummy"
            keyAlias = project.properties["RELEASE_KEY_ALIAS"] as String? ?: "dummy"
            keyPassword = project.properties["RELEASE_KEY_PASSWORD"] as String? ?: "dummy"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android.txt"),
                "proguard-rules.txt"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            // Disable fabric build ID generation for debug builds
//            ext.enableCrashlytics = false
            isDebuggable  = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

//    androidExtensions {
//        experimental = true
//        // For ViewHolder binding https://blog.jetbrains.com/kotlin/2017/08/kotlin-1-1-4-is-out/
//    }

    kotlinOptions {
        //https://stackoverflow.com/questions/59488983/why-i-still-get-cannot-inline-bytecode-built-with-jvm-target-1-8-into-bytecode
        jvmTarget = "1.8"
    }
    namespace = "com.anthonymandra.rawdroid"
}

dependencies {
    implementation(project(":inscription"))
    implementation(project(":metadata-extractor"))
    implementation(project(":rawprocessor"))
    implementation(project(":ssiv"))
    implementation("com.github.rcketscientist:DocumentActivity:1.0.0")
    implementation("com.github.rcketscientist:showcaseview:4.0.0")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:${rootProject.extra["kotlinVersion"]}")
    implementation("androidx.core:core-ktx:${rootProject.extra["coreVersion"]}")

    // UI
    implementation("com.afollestad:material-cab:2.0.1")
    implementation("me.zhanghai.android.materialprogressbar:library:1.6.1")
    implementation("com.github.deano2390:MaterialShowcaseView:1.2.0")
    implementation("com.eftimoff:android-viewpager-transformers:1.0.1@aar")
    implementation("com.github.bumptech.glide:glide:4.11.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    kapt("com.github.bumptech.glide:compiler:4.11.0")

    // Rx
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.10")
    implementation("io.reactivex.rxjava2:rxkotlin:2.3.0")
    implementation("com.jakewharton.rxbinding2:rxbinding-kotlin:2.2.0")

    // Material extension contains many of the necessary androidx classes
    implementation("com.google.android.material:material:${rootProject.extra["materialVersion"]}")
    implementation("androidx.annotation:annotation:${rootProject.extra["annotationVersion"]}")
    implementation("androidx.constraintlayout:constraintlayout:1.1.3")

    // Android architecture
    implementation("androidx.fragment:fragment-ktx:1.2.5")
    implementation("androidx.lifecycle:lifecycle-common-java8:${rootProject.extra["lifecycleVersion"]}")
    implementation("androidx.room:room-runtime:${rootProject.extra["roomVersion"]}")
    implementation("androidx.room:room-rxjava2:${rootProject.extra["roomVersion"]}")
    kapt("androidx.room:room-compiler:${rootProject.extra["roomVersion"]}")
    implementation("androidx.paging:paging-runtime-ktx:${rootProject.extra["pagingVersion"]}")
    implementation("androidx.work:work-runtime-ktx:${rootProject.extra["workVersion"]}")
    implementation("androidx.preference:preference-ktx:1.1.1")

    // Testing and Debug
    // Instrumented
    androidTestImplementation("androidx.arch.core:core-testing:${rootProject.extra["coreTestingVersion"]}")
    androidTestImplementation("androidx.annotation:annotation:${rootProject.extra["annotationVersion"]}")
    androidTestImplementation("androidx.test:runner:1.2.0")
    androidTestImplementation("androidx.test:rules:1.2.0")
    androidTestImplementation("org.hamcrest:hamcrest-library:2.1")
    androidTestImplementation("androidx.work:work-testing:${rootProject.extra["workVersion"]}")

    //    androidTestImplementation("androidx.test.espresso:espresso-core:3.1.0-alpha4")

    // Local
    testImplementation("junit:junit:4.12")
    testImplementation("androidx.room:room-testing:${rootProject.extra["roomVersion"]}")

    // Debug
    debugImplementation("com.github.amitshekhariitbhu.Android-Debug-Database:debug-db:1.0.7")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.0-alpha-2")
//
//    implementation("com.google.firebase:firebase-analytics:17.4.4")
//    implementation("com.crashlytics.sdk.android:crashlytics:2.10.1")
//    implementation("com.crashlytics.sdk.android:crashlytics-ndk:2.1.1")
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

//crashlytics {
//    enableNdk = true
//    androidNdkOut = "../rawprocessor/library/build/intermediates/ndkBuild/debug/obj"
//    androidNdkLibsOut = "../rawprocessor/library/build/intermediates/ndkBuild/release/obj"
//}
