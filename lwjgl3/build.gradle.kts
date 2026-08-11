plugins {
    kotlin("jvm")
    application
}

val gdxVersion: String by project
val joltVersion: String by project

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    // LibGDX LWJGL3 backend
    implementation("com.badlogicgames.gdx:gdx-backend-lwjgl3:$gdxVersion")
    implementation("com.badlogicgames.gdx:gdx-platform:$gdxVersion:natives-desktop")


    // Jolt physics natives for desktop (Windows/Linux/macOS Intel+ARM)
    runtimeOnly("com.github.xpenatan.xJolt:jolt-desktop:$joltVersion")
}

sourceSets {
    main {
        java.srcDirs("src/main/kotlin")
    }
}

application {
    mainClass.set("com.eucleantoomuch.game.lwjgl3.DesktopLauncherKt")
}

tasks.named<JavaExec>("run") {
    workingDir = file("../assets")
    isIgnoreExitValue = true

    if (System.getProperty("os.name").lowercase().contains("mac")) {
        jvmArgs("-XstartOnFirstThread")
    }
}

tasks.jar {
    archiveBaseName.set("euc-lean-too-much-desktop")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })

    manifest {
        attributes["Main-Class"] = "com.eucleantoomuch.game.lwjgl3.DesktopLauncherKt"
    }
}
