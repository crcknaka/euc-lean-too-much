package com.eucleantoomuch.game.lwjgl3

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.lang.management.ManagementFactory

/**
 * Helper to start the JVM without the libGDX splash screen.
 * On macOS, this also handles the -XstartOnFirstThread requirement.
 */
object StartupHelper {
    private const val JVM_RESTARTED_ARG = "jvmIsRestarted"

    fun startNewJvmIfRequired(): Boolean {
        val osName = System.getProperty("os.name").lowercase()

        // Not needed on Windows/Linux without splash screen issues
        if (!osName.contains("mac")) {
            return false
        }

        // Check if already restarted
        if (System.getProperty(JVM_RESTARTED_ARG) == "true") {
            return false
        }

        // Get the current classpath and main class
        val javaPath = ProcessHandle.current().info().command().orElse("java")
        val classpath = System.getProperty("java.class.path")
        val mainClass = getMainClass()

        // Build new process with -XstartOnFirstThread
        val command = mutableListOf(
            javaPath,
            "-XstartOnFirstThread",
            "-D$JVM_RESTARTED_ARG=true",
            "-cp", classpath,
            mainClass
        )

        // Add original JVM args (except -XstartOnFirstThread if present)
        ManagementFactory.getRuntimeMXBean().inputArguments
            .filter { !it.contains("startOnFirstThread") }
            .forEach { command.add(2, it) }

        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.inheritIO()
            val process = processBuilder.start()
            Runtime.getRuntime().addShutdownHook(Thread { process.destroy() })
            process.waitFor()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return true
    }

    private fun getMainClass(): String {
        val trace = Thread.currentThread().stackTrace
        return trace.last().className
    }
}
