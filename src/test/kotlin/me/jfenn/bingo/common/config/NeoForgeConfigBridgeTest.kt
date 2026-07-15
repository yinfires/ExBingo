package me.jfenn.bingo.common.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.jfenn.bingo.common.card.autotier.AutoTierConfig
import me.jfenn.bingo.common.card.filter.ObjectiveFilterList
import me.jfenn.bingo.common.card.tierlist.TierLabel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeoForgeConfigBridgeTest {
    @Test
    fun `all config leaves are mirrored or explicitly json-only`() {
        val configLeaves = collectConfigLeaves(BingoConfig::class).toSet()
        val covered = NeoForgeConfigBridge.mirroredConfigPaths +
                NeoForgeConfigBridge.intentionallyJsonOnlyConfigPaths.keys

        assertEquals(
            emptySet(),
            configLeaves - covered,
            "New BingoConfig fields must be added to NeoForgeConfigBridge or explicitly documented as JSON-only.",
        )
        assertEquals(
            emptySet(),
            NeoForgeConfigBridge.mirroredConfigPaths - configLeaves,
            "NeoForgeConfigBridge contains stale config paths.",
        )
    }

    @Test
    fun `neoforge config entries have english and chinese labels and tooltips`() {
        val enUs = readLang("en_us")
        val zhCn = readLang("zh_cn")

        val missingEnglish = NeoForgeConfigBridge.requiredTranslationKeys
            .filter { enUs[it].isNullOrBlank() }
        val missingChinese = NeoForgeConfigBridge.requiredTranslationKeys
            .filter { zhCn[it].isNullOrBlank() }

        assertTrue(missingEnglish.isEmpty(), "Missing en_us config translations: $missingEnglish")
        assertTrue(missingChinese.isEmpty(), "Missing zh_cn config translations: $missingChinese")
    }

    @Test
    fun `default board source weights are all one`() {
        assertTrue(
            BingoConfig().boardSourceWeights.values.all { it == 1.0 },
            "Default board source weights should all be 1.0.",
        )
    }

    @Test
    fun `default visible difficulty presets use intended order`() {
        assertEquals(
            listOf("easy", "medium", "hard", "extreme"),
            BingoConfig().difficultyPresets.keys.take(4).toList(),
            "The lobby difficulty menu shows the first four presets.",
        )
    }

    @Test
    fun `legacy alphabetically written difficulty presets normalize back to intended order`() {
        val legacyAlphabeticalDefaults = TierLabel.DIFFICULTY_PRESETS.entries
            .sortedBy { it.key }
            .associate { it.key to it.value }

        assertEquals(
            TierLabel.DIFFICULTY_PRESETS.keys.toList(),
            BingoConfig(difficultyPresets = legacyAlphabeticalDefaults).difficultyPresets.keys.toList(),
        )
    }

    @Test
    fun `experience bottle xp override defaults to 100 through 500`() {
        assertEquals(
            ExperienceBottleXpConfig(enabled = true, min = 100, max = 500),
            BingoConfig().experienceBottleXp,
        )
    }

    @Test
    fun `toml bridge preserves custom json item filter presets`() {
        val everything = ObjectiveFilterList.fromString("-tedious -unobtainable")
        val custom = ObjectiveFilterList.fromString("+enigmaticlegacyplus")
        val configPresets = linkedMapOf(
            "everything" to everything,
            "enigmaticlegacyplus" to custom,
        )
        val tomlValues = listOf("everything=-unobtainable -tedious")

        val merged = NeoForgeConfigBridge.mergeItemFilterPresets(configPresets, tomlValues)

        assertEquals(custom, merged["enigmaticlegacyplus"])
        assertEquals(ObjectiveFilterList.fromString("-unobtainable -tedious"), merged["everything"])
    }

    @Test
    fun `startup config source keeps newer or equal legacy json authoritative`() {
        val time = FileTime.fromMillis(1_000)

        assertEquals(
            NeoForgeConfigBridge.StartupConfigSource.LEGACY_JSON,
            NeoForgeConfigBridge.chooseStartupConfigSource(time, time),
        )
        assertEquals(
            NeoForgeConfigBridge.StartupConfigSource.LEGACY_JSON,
            NeoForgeConfigBridge.chooseStartupConfigSource(FileTime.fromMillis(2_000), time),
        )
    }

    @Test
    fun `startup config source only lets newer toml override legacy json`() {
        val legacyTime = FileTime.fromMillis(1_000)
        val tomlTime = FileTime.fromMillis(2_000)

        assertEquals(
            NeoForgeConfigBridge.StartupConfigSource.NEOFORGE_TOML,
            NeoForgeConfigBridge.chooseStartupConfigSource(legacyTime, tomlTime),
        )
        assertEquals(
            NeoForgeConfigBridge.StartupConfigSource.NEOFORGE_TOML,
            NeoForgeConfigBridge.chooseStartupConfigSource(null, tomlTime),
        )
        assertEquals(
            NeoForgeConfigBridge.StartupConfigSource.LEGACY_JSON,
            NeoForgeConfigBridge.chooseStartupConfigSource(legacyTime, null),
        )
    }

    private fun collectConfigLeaves(type: KClass<*>, prefix: String = ""): List<String> {
        val constructor = type.primaryConstructor
            ?: error("Config type ${type.simpleName} must have a primary constructor")

        return constructor.parameters.flatMap { parameter ->
            val name = parameter.name ?: error("Unnamed config parameter in ${type.simpleName}")
            val path = if (prefix.isBlank()) name else "$prefix.$name"
            val erasure = parameter.type.jvmErasure
            if (erasure in nestedConfigTypes) {
                collectConfigLeaves(erasure, path)
            } else {
                listOf(path)
            }
        }
    }

    private fun readLang(locale: String): Map<String, String> {
        val path = Path.of("src/main/resources/assets/exbingo/lang/$locale.json")
        val text = Files.readString(path).removePrefix("\uFEFF")
        return Json.parseToJsonElement(text).jsonObject
            .mapValues { (_, value) -> value.jsonPrimitive.content }
    }

    private val nestedConfigTypes = setOf<KClass<*>>(
        AutoTierConfig::class,
        ChatConfig::class,
        ExperienceBottleXpConfig::class,
        ClientConfig::class,
        PlayerSettings::class,
        PerformanceCleanupConfig::class,
        ServerConfig::class,
    )
}
