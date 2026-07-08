package me.jfenn.bingo.common.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.jfenn.bingo.common.card.autotier.AutoTierConfig
import me.jfenn.bingo.common.card.tierlist.TierLabel
import java.nio.file.Files
import java.nio.file.Path
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
        ClientConfig::class,
        PlayerSettings::class,
        ServerConfig::class,
    )
}
