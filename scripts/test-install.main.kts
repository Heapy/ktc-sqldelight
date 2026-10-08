#!/usr/bin/env kotlinr
// Install a pinned producer and verify shared catalog versions plus real SQLite queries.
// Usage: kotlinr scripts/test-install.main.kts INSTALLER [COMMIT] [OWNER/REPO]
@file:DependsOn("com.akuleshov7:ktoml-core-jvm:0.7.1")

import com.akuleshov7.ktoml.TomlInputConfig
import com.akuleshov7.ktoml.parsers.TomlParser
import com.akuleshov7.ktoml.tree.nodes.*
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.util.concurrent.TimeUnit

require(args.size in 1..3) { "Usage: test-install.main.kts INSTALLER [COMMIT] [OWNER/REPO]" }
val root = __FILE__.canonicalFile.parentFile.parentFile
val installer = File(args[0]).canonicalFile
val windows = System.getProperty("os.name").startsWith("Windows")
fun command(directory: File, arguments: List<String>): String {
    val output = Files.createTempFile("sqldelight-install-", ".log").toFile()
    try {
        val process = ProcessBuilder(arguments).directory(directory).redirectErrorStream(true).redirectOutput(output).start()
        try {
            check(process.waitFor(600, TimeUnit.SECONDS)) { "Command timed out: $arguments\n${output.readText()}" }
            val text = output.readText()
            check(process.exitValue() == 0) { "Command failed: $arguments\n$text" }
            print(text)
            return text
        } finally {
            if (process.isAlive) {
                process.descendants().use { children -> children.forEach { it.destroyForcibly() } }
                process.destroyForcibly().waitFor()
            }
        }
    } finally { output.delete() }
}
val commit = args.getOrNull(1) ?: command(root, listOf("git", "rev-parse", "HEAD")).trim()
val repository = args.getOrNull(2) ?: "Heapy/ktc-sqldelight"
require(Regex("[0-9a-f]{40}").matches(commit)) { "Expected a full producer commit SHA" }

fun table(node: TomlNode, key: String): TomlTable = node.children.filterIsInstance<TomlTable>().single { it.fullTableKey.last() == key }
fun string(node: TomlNode, key: String): String = (node.children.single { it.name == key } as TomlKeyValuePrimitive).value.content as String
fun catalog(file: File) = TomlParser(TomlInputConfig.compliant()).parseString(file.readText())
val expectedVersion = string(table(catalog(root.resolve("gradle/libs.versions.toml")), "versions"), "sqldelight")
val modules = mapOf(
    "runtime" to "app.cash.sqldelight:runtime",
    "native-driver" to "app.cash.sqldelight:native-driver",
    "sqlite-driver" to "app.cash.sqldelight:sqlite-driver",
    "coroutines-extensions" to "app.cash.sqldelight:coroutines-extensions",
)
val consumer = Files.createTempDirectory("ktc sqldelight consumer ").toFile()
try {
    for (name in listOf("kotlin", "kotlin.bat")) Files.copy(root.resolve(name).toPath(), consumer.resolve(name).toPath(), COPY_ATTRIBUTES)
    val app = consumer.resolve("app").apply { mkdir() }
    for (name in listOf("schema", "test")) check(root.resolve("examples/app/$name").copyRecursively(app.resolve(name)))
    consumer.resolve("project.yaml").writeText("modules:\n  - app\n")
    app.resolve("module.yaml").writeText($$"""
        product: jvm/lib

        dependencies:
          - $libs.ktc.sqldelight.runtime

        test-dependencies:
          - $libs.ktc.sqldelight.sqlite.driver

        plugins:
          sqldelight:
            enabled: true
            packageName: example.db
            className: ExampleDatabase
            sourceDirectory: schema
    """.trimIndent() + "\n")
    val consumerCatalog = consumer.resolve("gradle/libs.versions.toml").apply {
        parentFile.mkdirs()
        writeText("[versions]\nconsumer = \"1.0\"\n\n[libraries]\nconsumer = { module = \"example:unused\", version.ref = \"consumer\" }\n")
    }
    val invocation = if (windows && installer.extension == "bat") listOf("cmd.exe", "/c", installer.path) else listOf(installer.path)
    fun install(vararg arguments: String) = command(consumer, invocation + arguments + listOf("--project-dir", consumer.path))
    install("add", repository, "--commit", commit, "--enable-in", "app")
    install("verify")
    val parsed = catalog(consumerCatalog)
    val versions = table(parsed, "versions")
    val libraries = table(parsed, "libraries")
    check(string(versions, "ktc-sqldelight-sqldelight") == expectedVersion) { "Exported SQLDelight version differs from the producer" }
    check(versions.children.count { it.name.startsWith("ktc-sqldelight-") } == 1) { "Expected one shared SQLDelight version" }
    check(libraries.children.filterIsInstance<TomlTable>().map { it.fullTableKey.last() }.toSet() == modules.keys.map { "ktc-sqldelight-$it" }.toSet() + "consumer")
    for ((alias, module) in modules) {
        val library = table(libraries, "ktc-sqldelight-$alias")
        check(string(library, "module") == module) { "Wrong exported module for $alias" }
        check(string(table(library, "version"), "ref") == "ktc-sqldelight-sqldelight") { "Expected a shared version.ref for $alias" }
    }
    check(string(versions, "consumer") == "1.0")
    check(string(table(libraries, "consumer"), "module") == "example:unused")
    check(string(table(table(libraries, "consumer"), "version"), "ref") == "consumer")
    val originalCatalog = consumerCatalog.readBytes()
    install("sync", "--offline")
    install("verify")
    check(consumerCatalog.readBytes().contentEquals(originalCatalog)) { "Offline sync changed the catalog" }
    val wrapper = consumer.resolve(if (windows) "kotlin.bat" else "kotlin").path
    command(consumer, (if (windows) listOf("cmd.exe", "/c", wrapper) else listOf("sh", wrapper)) + "test")
    println("SQLDelight installation passed: four exports, one shared version.ref, preserved user entries, offline sync and SQLite queries")
} finally {
    check(consumer.deleteRecursively()) { "Could not remove fixture: $consumer" }
}
