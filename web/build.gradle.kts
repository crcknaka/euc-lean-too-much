import org.teavm.gradle.api.OptimizationLevel

// Browser build via TeaVM (the only web backend that can compile Kotlin - GWT needs Java source).
// Build with `./gradlew :web:gdx_teavm_web_js_build`; the finished site lands in
// build/dist/js/webapp and is a plain static folder - any web server can host it.
plugins {
    kotlin("jvm")
    id("com.github.xpenatan.gdx-teavm") version "1.6.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// javax.script.ScriptException is declared in src/main/java to fill a hole in jParser's wasm
// loader (see that file). javac rejects declaring a package that a readable system module also
// exports, so this compilation reads java.base only - everything here needs nothing else.
tasks.named<JavaCompile>("compileJava") {
    options.compilerArgs.addAll(listOf("--limit-modules", "java.base"))
}

sourceSets {
    main {
        java.srcDirs("src/main/kotlin", "src/main/java")
    }
}

val gdxVersion: String by project
val joltVersion: String by project
val gdxTeaVMVersion = "1.6.1"

// jParser's loader-core carries the JNI JParserLibraryLoader, whose System.loadLibrary calls
// TeaVM cannot compile. It stays on the classpath all the same: loader-teavm replaces that one
// class with a wasm loader through the `emu.com=com` substitution rule, and a substitution
// needs the original class to be present to replace. Removing loader-core instead leaves the
// class simply missing, which fails the build the moment anything touches Jolt.

dependencies {
    // Declared FIRST on purpose: these carry the `emu/...` replacement classes and the
    // teavm.properties rules that swap jParser's JNI loader for the wasm one.
    implementation("com.github.xpenatan.jParser:loader-teavm:1.0.0")
    implementation("com.github.xpenatan.jParser:idl-teavm:1.0.0")
    implementation("com.github.xpenatan.jParser:idl-helper-teavm:1.0.0")

    // Needed to compile the substitution policy in src/main/java that tells TeaVM to use the
    // wasm loader above; the compiler already has it at build time, hence compileOnly.
    compileOnly("org.teavm:teavm-extension-spi:0.15.0")
    // loader-teavm's wasm loader needs this but never declares it, so without it the
    // replacement class fails to resolve and TeaVM silently falls back to the JNI original.
    implementation("com.github.xpenatan:jMultiplatform:0.1.3")

    implementation(project(":core"))
    implementation("com.badlogicgames.gdx:gdx:$gdxVersion")

    // Real FreeType compiled to wasm - keeps the Cyrillic glyph set working in the browser
    implementation("com.github.xpenatan.gdx-teavm:gdx-freetype-web:$gdxTeaVMVersion")

    // Jolt physics compiled to wasm - this is what makes ragdolls work on the web
    implementation("com.github.xpenatan.xJolt:jolt-teavm:$joltVersion")
}

// The browser backend preloads every file in the asset manifest before the game starts -
// there is no lazy path - so anything shipped is paid for in download time on first visit.
// Stage a filtered copy rather than pointing at the raw asset tree: the retired wheel models
// are kept in the repo for reference but nothing loads them, and .DS_Store is Finder noise.
val stageWebAssets = tasks.register<Sync>("stageWebAssets") {
    from("../assets") {
        exclude("old_Wheels/**", "**/.DS_Store")
    }
    into(layout.buildDirectory.dir("webAssets"))
}

gdxTeaVM {
    assets(layout.buildDirectory.dir("webAssets"))

    // TeaVM strips unreachable code and has no full reflection: Ashley instantiates
    // components reflectively, so every component class must be registered explicitly.
    reflection("com.eucleantoomuch.game.ecs.components**")

    js {
        mainClass.set("com.eucleantoomuch.game.web.WebLauncher")
        htmlTitle.set("EUC Rider - Lean too much")
        htmlWidth.set(1280)
        htmlHeight.set(720)
        optimization.set(OptimizationLevel.BALANCED)
        obfuscated.set(true)
        serverPort.set(8080)
        devServer {
            enabled.set(true)
            autoBuild.set(true)
            autoReload.set(true)
        }
    }
}

// The plugin renders webapp/index.html off its own classpath and backend-web always wins that
// lookup, so a template in this module's resources is silently ignored. Swap in ours after the
// fact instead, reusing the two bits the plugin computes (the entry-point call and the script
// tag for the generated bundle) so the page stays correct if the output name ever changes.
// (matching/configureEach, not named(): the plugin registers its tasks in afterEvaluate,
// so the task does not exist yet while this script is being evaluated.)
// A plain file collection carries no task dependency, so the staged assets have to be wired
// to whatever consumes them by hand.
tasks.matching { it.name == "generateJavaScript" }.configureEach {
    dependsOn(stageWebAssets)
}

tasks.matching { it.name == "gdx_teavm_web_js_build" }.configureEach {
    dependsOn(stageWebAssets)
    doLast {
        val generated = layout.buildDirectory.file("dist/js/webapp/index.html").get().asFile
        val template = file("src/main/resources/webapp/index.html")
        if (!generated.exists() || !template.exists()) return@doLast

        val html = generated.readText()
        val entryCall = Regex("""async function start\(\)\s*\{\s*([\s\S]*?)\s*\}""")
            .find(html)?.groupValues?.get(1)
        val bundleTag = Regex("""<script[^>]*src="[^"]*\.js"></script>""")
            .findAll(html).lastOrNull()?.value
        if (entryCall == null || bundleTag == null) {
            logger.warn("index.html: could not read the generated entry point, keeping the plugin's page")
            return@doLast
        }

        generated.writeText(
            template.readText()
                .replace("%TITLE%", "EUC Rider - Lean too much")
                .replace("%MODE%", entryCall)
                .replace("%JS_SCRIPT%", bundleTag)
        )
        logger.lifecycle("index.html: replaced with the project template (FreeType + responsive canvas)")
    }
}
