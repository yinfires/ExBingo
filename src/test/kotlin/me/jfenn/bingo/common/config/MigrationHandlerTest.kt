package me.jfenn.bingo.common.config

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IModEnvironment
import me.jfenn.bingo.platform.config.IConfigManager
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.reflect.KType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MigrationHandlerTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `legacy migration leaves current exbingo tierlists untouched when old config is absent`() {
        val customTierList = tempDir.resolve("$MOD_ID_BINGO/tierlists/custom.tierlist.json")
        val customJson = """{"S":["example:custom"]}"""
        Files.createDirectories(customTierList.parent)
        Files.writeString(customTierList, customJson)

        createHandler().migrateLegacyConfigDir()

        assertTrue(Files.exists(customTierList))
        assertEquals(customJson, Files.readString(customTierList))
    }

    @Test
    fun `legacy migration merges old bingo config without replacing current custom tierlists`() {
        val currentCustom = tempDir.resolve("$MOD_ID_BINGO/tierlists/custom.tierlist.json")
        val oldCustom = tempDir.resolve("bingo/tierlists/custom.tierlist.json")
        val oldOnly = tempDir.resolve("bingo/tierlists/legacy_only.tierlist.json")
        val currentJson = """{"S":["example:current"]}"""
        val oldJson = """{"S":["example:old"]}"""

        Files.createDirectories(currentCustom.parent)
        Files.writeString(currentCustom, currentJson)
        Files.createDirectories(oldCustom.parent)
        Files.writeString(tempDir.resolve("bingo/game-options.json"), "{}")
        Files.writeString(oldCustom, oldJson)
        Files.writeString(oldOnly, """{"S":["example:legacy"]}""")

        createHandler().migrateLegacyConfigDir()

        assertTrue(Files.exists(currentCustom))
        assertEquals(currentJson, Files.readString(currentCustom))
        assertTrue(Files.exists(tempDir.resolve("$MOD_ID_BINGO/tierlists/legacy_only.tierlist.json")))
    }

    @Test
    fun `tierlist directory migration keeps existing target files`() {
        val currentCustom = tempDir.resolve("$MOD_ID_BINGO/tierlists/custom.tierlist.json")
        val oldCustom = tempDir.resolve("$MOD_ID_BINGO/custom.tierlist.json")
        val currentJson = """{"S":["example:current"]}"""
        val oldJson = """{"S":["example:old"]}"""

        Files.createDirectories(currentCustom.parent)
        Files.writeString(currentCustom, currentJson)
        Files.writeString(oldCustom, oldJson)

        createHandler(config = BingoConfig(version = 31)).runMigrations()

        assertTrue(Files.exists(currentCustom))
        assertEquals(currentJson, Files.readString(currentCustom))
        assertTrue(Files.exists(oldCustom))
    }

    private fun createHandler(config: BingoConfig = BingoConfig()): MigrationHandler {
        val environment = TestEnvironment(tempDir)
        return MigrationHandler(
            log = LoggerFactory.getLogger("MigrationHandlerTest"),
            config = config,
            environment = environment,
            configManager = UnusedConfigManager(tempDir),
        )
    }

    private class TestEnvironment(
        override val configDir: Path,
    ) : IModEnvironment {
        override val gameDir: Path = configDir.resolve("game")
        override val envType: IModEnvironment.EnvType = IModEnvironment.EnvType.SERVER
        override fun isModLoaded(modId: String): Boolean = false
    }

    private class UnusedConfigManager(
        override val configDir: Path,
    ) : IConfigManager {
        override fun inputStream(path: String): InputStream = error("Not used in this test")
        override fun outputStream(path: String): OutputStream = error("Not used in this test")
        override fun readLastModified(fileName: String): Instant = Instant.MIN
        override fun <T : Any> read(type: KType, file: String): T {
            @Suppress("UNCHECKED_CAST")
            return when (file) {
                "$MOD_ID_BINGO/config.json" -> BingoConfig() as T
                "$MOD_ID_BINGO/game-options.json" -> me.jfenn.bingo.common.options.BingoOptions() as T
                "$MOD_ID_BINGO/game-options-default.json" -> me.jfenn.bingo.common.options.BingoOptions() as T
                "$MOD_ID_BINGO/files.json" -> TrackedFiles() as T
                else -> error("Unexpected read in this test: $file")
            }
        }

        override fun <T : Any> write(type: KType, file: String, config: T) = Unit
    }
}
