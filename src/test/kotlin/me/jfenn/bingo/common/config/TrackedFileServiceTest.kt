package me.jfenn.bingo.common.config

import me.jfenn.bingo.common.MOD_ID_BINGO
import me.jfenn.bingo.platform.IModEnvironment
import org.junit.jupiter.api.io.TempDir
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TrackedFileServiceTest {
    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `missing tierlist resource can keep existing tracked file on disk`() {
        val service = createService()
        val file = tempDir.resolve("$MOD_ID_BINGO/tierlists/items.tierlist.json")
        val tierListJson = """{"S":["minecraft:diamond"]}"""

        service.readFileOrResource(
            path = file,
            resource = tierListJson,
            serialize = { it },
            deserialize = ::readText,
        )

        val result = service.readFileOrResource(
            path = file,
            resource = null,
            serialize = { it },
            deserialize = ::readText,
            deleteMissingResource = false,
        )

        assertTrue(Files.exists(file))
        assertEquals(tierListJson, Files.readString(file))
        assertEquals(tierListJson, result.config)
    }

    private fun createService(): TrackedFileService {
        val environment = TestEnvironment(tempDir)
        return TrackedFileService(
            configService = ConfigService(ConfigManager(environment)),
            environment = environment,
            log = LoggerFactory.getLogger("TrackedFileServiceTest"),
        )
    }

    private fun readText(stream: InputStream): String {
        return stream.bufferedReader().use { it.readText() }
    }

    private class TestEnvironment(
        override val configDir: Path,
    ) : IModEnvironment {
        override val gameDir: Path = configDir.resolve("game")
        override val envType: IModEnvironment.EnvType = IModEnvironment.EnvType.SERVER
        override fun isModLoaded(modId: String): Boolean = false
    }
}
