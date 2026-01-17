plugins {
    id("com.android.application")
    kotlin("android")
}

val gdxVersion: String by project

// Configuration for native libraries
val natives: Configuration by configurations.creating

android {
    namespace = "com.eucleantoomuch.game"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.eucleantoomuch.game"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-x86_64")

    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-armeabi-v7a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-arm64-v8a")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86")
    natives("com.badlogicgames.gdx:gdx-freetype-platform:$gdxVersion:natives-x86_64")
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
