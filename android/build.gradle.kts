import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project
val joltVersion: String by project

// Configuration for native libraries
val natives: Configuration by configurations.creating

// Release signing credentials (android/keystore.properties, not committed to git)
val keystoreProps = Properties().apply {
    val f = file("keystore.properties")
    if (f.exists()) f.inputStream().use { stream -> load(stream) }
}

android {
    namespace = "com.eucleantoomuch.game"
    // 36 required by androidx.core 1.17, pulled in by the libGDX 1.14.2 Android backend
    compileSdk = 36

    defaultConfig {
        applicationId = "com.eucleantoomuch.game"
        minSdk = 24
        targetSdk = 36
        versionCode = 8
        versionName = "1.3.4"
        ndk {
            // Real phones only - x86/x86_64 would add ~13 MB of natives for Intel emulators
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        named("main") {
            manifest.srcFile("src/main/AndroidManifest.xml")
            java.srcDirs("src/main/kotlin")
            assets.srcDirs("../assets")
            res.srcDirs("src/main/res")
            jniLibs.srcDirs("libs")
        }
    }

    androidResources {
        // Keep archived wheel models in the repo but out of the APK
        ignoreAssetsPatterns.add("!old_Wheels")
        // The sound effects live in assets/ so the web build can reach them; Android plays the
        // same files out of res/raw through SoundPool, so shipping both would just be dead weight.
        ignoreAssetsPatterns.add("!sounds")
    }

    packaging {
        resources {
            excludes += listOf(
                "META-INF/robovm/ios/robovm.xml",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt"
            )
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx-backend-android:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")
    implementation("androidx.core:core-ktx:1.12.0")

    // Jolt physics natives for Android (arm64-v8a, armeabi-v7a, x86, x86_64)
    implementation("com.github.xpenatan.xJolt:jolt-android:$joltVersion")
    // libidl.so - the jParser loader opens it before libjolt.so, but the jolt-android
    // AAR does not declare it, so it must be added explicitly or loading fails at runtime.
    implementation("com.github.xpenatan.jParser:idl-helper-android:1.0.0")

    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")

    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-arm64-v8a")
}

// Task to copy native libraries to jniLibs folder
tasks.register("copyAndroidNatives") {
    doFirst {
        val libsDir = file("libs")
        libsDir.mkdirs()

        natives.files.forEach { jar ->
            val outputDir = when {
                jar.name.contains("natives-arm64-v8a") -> file("libs/arm64-v8a")
                jar.name.contains("natives-armeabi-v7a") -> file("libs/armeabi-v7a")
                jar.name.contains("natives-x86_64") -> file("libs/x86_64")
                jar.name.contains("natives-x86") -> file("libs/x86")
                else -> null
            }

            outputDir?.let { dir ->
                dir.mkdirs()
                copy {
                    from(zipTree(jar))
                    into(dir)
                    include("*.so")
                }
            }
        }
    }
}

tasks.matching { it.name.contains("merge") && it.name.contains("JniLibFolders") }.configureEach {
    dependsOn("copyAndroidNatives")
}

tasks.named("preBuild") {
    dependsOn("copyAndroidNatives")
}
