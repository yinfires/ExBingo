package me.jfenn.bingo.common.datapack

import me.jfenn.bingo.common.config.readStream
import me.jfenn.bingo.platform.config.IConfigManager
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class LobbyWorldService(
    private val log: Logger,
    private val configManager: IConfigManager,
) {

    companion object {
        private const val LOBBY_WORLD_ZIP = "lobby_world.zip"
    }

    fun readLastModified() = configManager.readLastModified(LOBBY_WORLD_ZIP)

    fun openLobbyZip(): ZipInputStream {
        return ZipInputStream(configManager.readStream(LOBBY_WORLD_ZIP, shouldWriteDefault = false).buffered())
    }

    fun copyDataPack(toPath: Path) {
        Files.createDirectories(toPath.parent)
        val tempPath = Files.createTempFile(toPath.parent, "${toPath.fileName}.", ".tmp")

        try {
            ZipOutputStream(tempPath.toFile().outputStream().buffered()).use { zipOutput ->
                openLobbyZip().use { zipStream ->
                    while (true) {
                        val entry = zipStream.nextEntry ?: break
                        if (entry.isDirectory) continue

                        val entryName = entry.name.substringAfter('/')
                        val entryPrefix = "datapacks/bingo/"

                        if (entryName.startsWith(entryPrefix)) {
                            val outputName = entryName.removePrefix(entryPrefix)
                            log.debug("[LobbyWorldService] Copying {} -> {}", outputName, toPath.fileName)
                            zipOutput.putNextEntry(ZipEntry(outputName))
                            IOUtils.copy(zipStream, zipOutput)
                            zipOutput.closeEntry()
                        }

                        zipStream.closeEntry()
                    }
                }
            }

            try {
                Files.move(tempPath, toPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: IOException) {
                Files.move(tempPath, toPath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            Files.deleteIfExists(tempPath)
            if (Files.exists(toPath)) {
                log.warn("[LobbyWorldService] Unable to replace locked datapack at {}. Keeping the existing file.", toPath, e)
            } else {
                throw e
            }
        }
    }

}
