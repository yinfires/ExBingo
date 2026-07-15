package me.jfenn.bingo.common.state

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import kotlinx.serialization.serializer
import me.jfenn.bingo.common.event.model.StateChangedEvent
import me.jfenn.bingo.common.utils.decodeFromUtf8Stream
import me.jfenn.bingo.platform.IJsonSerializers
import me.jfenn.bingo.platform.IPersistentStateManager
import me.jfenn.bingo.platform.IPersistentStateType
import me.jfenn.bingo.platform.IServerWorldFactory
import me.jfenn.bingo.platform.event.IEventBus
import me.jfenn.bingo.platform.event.game.ScopeStopped
import me.jfenn.bingo.platform.event.model.ApplicationCloseEvent
import me.jfenn.bingo.platform.event.model.ServerEvent
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.OpenOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.reflect.KType

@OptIn(ExperimentalSerializationApi::class)
class PersistentStateManager(
    val logger: Logger,
    serializers: IJsonSerializers,
    val serverWorldFactory: IServerWorldFactory,
    eventBus: IEventBus,
) : IPersistentStateManager {

    private val json = Json(serializers.json) {
        prettyPrint = true
    }
    private val registeredTypes = mutableMapOf<String, Type<*>>()
    private val writeOptions = arrayOf<OpenOption>(
        StandardOpenOption.SYNC,
        StandardOpenOption.WRITE,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING
    );

    override fun <T : Any> register(
        id: String,
        kType: KType,
        default: () -> T
    ): IPersistentStateType<T> {
        val path = serverWorldFactory.overworld.directory.resolve("data")
            .resolve("$id.json.gz")

        val type = Type(
            id,
            path,
            @Suppress("UNCHECKED_CAST") (serializer(kType) as KSerializer<T>),
            default()
        )
        registeredTypes[type.id] = type
        return type
    }

    override fun <T : Any> getFromWorld(
        type: IPersistentStateType<T>
    ): T {
        require(type is Type)
        val path = type.path
        val backupPath = backupPath(path)
        if (Files.exists(path)) {
            try {
                logger.debug("Reading persistent state ${type.id}...")
                type.value = readFromPath(path, type.serializer)
            } catch (e: Throwable) {
                logger.error("Error decoding persistent state ${type.id}", e)
                if (Files.exists(backupPath)) {
                    try {
                        logger.warn("Reading persistent state ${type.id} from backup...")
                        type.value = readFromPath(backupPath, type.serializer)
                    } catch (backupError: Throwable) {
                        logger.error("Error decoding backup persistent state ${type.id}", backupError)
                    }
                }
            }
        }

        return type.value
    }

    override fun <T : Any> put(
        type: IPersistentStateType<T>,
        value: T
    ) {
        require(type is Type)
        val path = type.path
        val backupPath = backupPath(path)
        var didWrite = false

        try {
            logger.debug("Writing persistent state ${type.id}...")
            Files.createDirectories(path.parent)
            val tempPath = Files.createTempFile(path.parent, "${path.fileName}.", ".tmp")
            try {
                writeToPath(tempPath, type.serializer, value)
                readFromPath(tempPath, type.serializer)

                copyCurrentToBackup(path, backupPath, type.serializer)

                moveReplacing(tempPath, path)
                didWrite = true
            } finally {
                Files.deleteIfExists(tempPath)
            }
        } catch (e: Throwable) {
            logger.error("Error encoding persistent state ${type.id}", e)
        }

        if (didWrite) {
            type.value = value
            logger.debug("Writing persistent state ${type.id}... Done!")
        }
    }

    private class Type<T: Any>(
        val id: String,
        val path: Path,
        val serializer: KSerializer<T>,
        var value: T,
    ) : IPersistentStateType<T>

    private fun <T : Any> readFromPath(path: Path, serializer: KSerializer<T>): T {
        return Files.newInputStream(path)
            .buffered()
            .let { GZIPInputStream(it) }
            .use {
                json.decodeFromUtf8Stream(serializer, it)
            }
    }

    private fun <T : Any> writeToPath(path: Path, serializer: KSerializer<T>, value: T) {
        Files.newOutputStream(path, *writeOptions)
            .buffered()
            .let { GZIPOutputStream(it) }
            .use {
                json.encodeToStream(serializer, value, it)
            }
    }

    private fun backupPath(path: Path): Path {
        return path.resolveSibling("${path.fileName}.bak")
    }

    private fun <T : Any> copyCurrentToBackup(path: Path, backupPath: Path, serializer: KSerializer<T>) {
        if (!Files.exists(path)) return

        val backupTempPath = Files.createTempFile(path.parent, "${backupPath.fileName}.", ".tmp")
        try {
            try {
                readFromPath(path, serializer)
            } catch (e: Throwable) {
                logger.warn("Skipping backup update because current persistent state is unreadable: {}", path, e)
                return
            }

            Files.copy(path, backupTempPath, StandardCopyOption.REPLACE_EXISTING)
            readFromPath(backupTempPath, serializer)
            moveReplacing(backupTempPath, backupPath)
        } finally {
            Files.deleteIfExists(backupTempPath)
        }
    }

    private fun moveReplacing(from: Path, to: Path) {
        try {
            Files.move(
                from,
                to,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: UnsupportedOperationException) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun writeAll() {
        registeredTypes.values.forEach {
            this.put<Any>(@Suppress("UNCHECKED_CAST") (it as Type<Any>), it.value)
        }
    }

    init {
        eventBus.register(ServerEvent.Saved) {
            writeAll()
        }

        eventBus.register(StateChangedEvent) {
            writeAll()
        }

        eventBus.register(ScopeStopped) {
            writeAll()
        }

        eventBus.register(ApplicationCloseEvent) {
            writeAll()
        }
    }
}
