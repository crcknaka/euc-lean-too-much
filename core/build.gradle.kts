plugins {
    kotlin("jvm")
}

val gdxVersion: String by project
val ktxVersion: String by project
val ashleyVersion: String by project

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // LibGDX core
    api("com.badlogicgames.gdx:gdx:$gdxVersion")

    // FreeType for smooth fonts
    api("com.badlogicgames.gdx:gdx-freetype:$gdxVersion")

    // Ashley ECS
    api("com.badlogicgames.ashley:ashley:$ashleyVersion")

    // KTX extensions
    api("io.github.libktx:ktx-app:$ktxVersion")
    api("io.github.libktx:ktx-ashley:$ktxVersion")
    api("io.github.libktx:ktx-graphics:$ktxVersion")
    api("io.github.libktx:ktx-math:$ktxVersion")
    api("io.github.libktx:ktx-collections:$ktxVersion")
    api("io.github.libktx:ktx-log:$ktxVersion")
    api("io.github.libktx:ktx-freetype:$ktxVersion")

    // Kotlin stdlib
    implementation(kotlin("stdlib"))
}

sourceSets {
    main {
        java.srcDirs("src/main/kotlin")
    }
}
