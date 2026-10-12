package com.fuelingchat

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

class BootFailFastTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun missingRequiredKeyAbortsBootNamingTheKey() {
        val env =
            validEnv.lines().filterNot { it.startsWith("DB_PASSWORD=") }.joinToString("\n")

        val (exitCode, output) = runApp(env)

        assertNotEquals(0, exitCode)
        assertTrue(
            output.contains("DB_PASSWORD"),
            "expected output naming the key, got: $output",
        )
    }

    @Test
    fun unreachableDatabaseAbortsBootNamingTheDatabase() {
        val env =
            validEnv
                .replace("jdbc:postgresql://localhost:25432/fueling", "jdbc:postgresql://localhost:1/fueling")
                .replace("jdbc:postgresql://localhost:25432/gas_station", "jdbc:postgresql://localhost:1/gas_station")
                .replace("jdbc:postgresql://localhost:25432/payment", "jdbc:postgresql://localhost:1/payment")
                .replace("jdbc:postgresql://localhost:25432/vendors", "jdbc:postgresql://localhost:1/vendors")

        val (exitCode, output) = runApp(env)

        assertNotEquals(0, exitCode)
        assertTrue(
            output.contains("database unreachable: fueling"),
            "expected output naming the database, got: $output",
        )
    }

    @Test
    fun missingToolDescriptorAbortsBootNamingTheFile() {
        val (exitCode, output) = runApp(validEnv, stripMainResources = true)

        assertNotEquals(0, exitCode)
        assertTrue(output.contains("get_fueling_info.json"), "expected output naming the file, got: $output")
    }

    @Test
    fun serverStartsAndAnswersHttp() {
        val process = startApp(validEnv)
        try {
            val response = pollUntilResponds("http://localhost:8080/", Duration.ofSeconds(40))

            assertNotNull(response, "server did not start answering HTTP in time")
            assertEquals(404, response!!.statusCode())
        } finally {
            process.destroyForcibly()
            process.waitFor()
        }
    }

    private val validEnv =
        """
        DB_USER=postgres
        DB_PASSWORD=postgres
        FUELING_DB_URL=jdbc:postgresql://localhost:25432/fueling
        GAS_STATION_DB_URL=jdbc:postgresql://localhost:25432/gas_station
        PAYMENT_DB_URL=jdbc:postgresql://localhost:25432/payment
        VENDORS_DB_URL=jdbc:postgresql://localhost:25432/vendors
        DEEPSEEK_API_KEY=test-key
        DEEPSEEK_MODEL=test-model
        OLLAMA_MODEL=test-model
        """.trimIndent()

    private fun runApp(
        env: String,
        stripMainResources: Boolean = false,
    ): Pair<Int, String> {
        val process = startApp(env, stripMainResources)
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return exitCode to output
    }

    private fun startApp(
        env: String,
        stripMainResources: Boolean = false,
    ): Process {
        val workingDir = Files.createDirectories(tempDir.resolve("boot-${System.nanoTime()}"))
        Files.writeString(workingDir.resolve(".env"), env)
        return ProcessBuilder(appCommand(stripMainResources))
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start()
    }

    private fun appCommand(stripMainResources: Boolean): List<String> {
        val classpath =
            System
                .getProperty("java.class.path")
                .split(File.pathSeparator)
                .map { entry -> if (stripMainResources && entry.endsWith("resources/main")) emptyDir() else entry }
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        return listOf(java, "-cp", classpath.joinToString(File.pathSeparator), "com.fuelingchat.ApplicationKt")
    }

    private fun emptyDir(): String =
        Files.createDirectories(tempDir.resolve("empty-resources-${System.nanoTime()}")).toString()

    private fun pollUntilResponds(
        url: String,
        timeout: Duration,
    ): HttpResponse<String>? {
        val client = HttpClient.newHttpClient()
        val request = HttpRequest.newBuilder(URI.create(url)).GET().build()
        val deadline = System.nanoTime() + timeout.toNanos()
        while (System.nanoTime() < deadline) {
            try {
                return client.send(request, HttpResponse.BodyHandlers.ofString())
            } catch (_: Exception) {
                Thread.sleep(200)
            }
        }
        return null
    }
}
