package me.jfenn.bingo.client.integrations.xaero

import me.jfenn.bingo.client.common.event.ClientGameEndEvent
import me.jfenn.bingo.common.scope.BingoComponent
import me.jfenn.bingo.platform.event.IEventBus
import org.slf4j.Logger

/**
 * Resets Xaero's in-memory map view when a bingo round ends, so the explored
 * tiles and waypoints from one game don't carry into the next.
 *
 * Deleting Xaero's disk cache ([XaeroCacheCleaner]) only affects the *next* load —
 * it can't clear tiles Xaero has already loaded and rendered for the live session,
 * which is why explored content lingered until a full client restart. The fix is to
 * make Xaero treat the new round as a different world:
 *
 *  - World map: Xaero caches explored tiles per `(dimension, multiworld id)`. On a
 *    persistent server the round dimension (e.g. `minecraft:overworld`) and its
 *    auto-detected multiworld repeat every game, so the same tile cache reloads.
 *    Assigning the current dimension a fresh custom (`cm$`) multiworld id and
 *    confirming it makes Xaero's background processor clear the dimension's regions
 *    and switch to a new, empty cache directory.
 *  - Minimap: it renders live tiles around the player (no persistent tile cache to
 *    reset), but waypoints are stored per world — so they are cleared and re-saved.
 *
 * A round can span several dimensions (overworld, nether, twilight forest, …), and
 * both surfaces must be wiped for *all* of them, not just where the player happens
 * to stand when the game ends:
 *  - World map tiles: Xaero's `confirmMultiworld` only acts on the *current*
 *    dimension, so only that one is switched to a fresh multiworld here. The other
 *    dimensions have already been unloaded from memory (Xaero clears a dimension's
 *    regions when you leave it), leaving only their disk cache — which
 *    [XaeroCacheCleaner] deletes on game end, so they load empty next round.
 *  - Waypoints: the minimap keeps a `MinimapWorld` in memory per visited dimension,
 *    so deleting disk files alone wouldn't clear them. Instead every world in every
 *    root container is walked and emptied, covering all dimensions in one pass.
 *
 * Timing is critical: `confirmMultiworld` requires the round dimension to still be
 * the *current* one. So this runs on [ClientGameEndEvent] — fired while the player
 * is still in the round's world, before the teleport back to the lobby changes the
 * current dimension.
 *
 * Everything is reflection so Xaero stays an optional, compile-free dependency;
 * each half is skipped cleanly if that Xaero module isn't installed. Runs on the
 * client thread (the event fires from the packet handler), as Xaero map state
 * requires.
 */
internal class XaeroMapResetter(
    private val log: Logger,
    eventBus: IEventBus,
) : BingoComponent() {

    init {
        eventBus.register(ClientGameEndEvent) { reset() }
    }

    private fun reset() {
        resetWorldMap()
        clearMinimapWaypoints()
    }

    /**
     * Switch the current dimension's World Map to a fresh `cm$` multiworld, so the
     * background processor clears its regions and loads an empty tile cache. Mirrors
     * Xaero's own GUI path: `setMultiworld` (select) then `confirmMultiworld` (apply
     * without the in-game "press M to confirm" prompt).
     */
    private fun resetWorldMap() {
        try {
            val session = Class.forName("xaero.map.WorldMapSession")
                .getMethod("getCurrentSession").invoke(null) ?: return
            val processor = session.javaClass.getMethod("getMapProcessor").invoke(session) ?: return
            // mapWorldUsable is false while a world/dimension is still loading; calling
            // through then would be silently dropped.
            if (processor.javaClass.getMethod("isMapWorldUsable").invoke(processor) != true) return
            val mapWorld = processor.javaClass.getMethod("getMapWorld").invoke(processor) ?: return
            val dimension = mapWorld.javaClass.getMethod("getCurrentDimension").invoke(mapWorld) ?: return

            val dimensionClass = Class.forName("xaero.map.world.MapDimension")
            val uiSync = processor.javaClass.getField("uiSync").get(processor)
            synchronized(uiSync) {
                // A unique id per round forces a brand-new (empty) tile cache; the
                // checked-add loop guarantees we don't collide with an existing one.
                val addMultiworldChecked = dimension.javaClass.getMethod("addMultiworldChecked", String::class.java)
                var id = "cm\$bingo${System.currentTimeMillis()}"
                var attempt = 0
                while (addMultiworldChecked.invoke(dimension, id) != true) {
                    id = "cm\$bingo${System.currentTimeMillis()}_${++attempt}"
                }

                processor.javaClass.getMethod("setMultiworld", dimensionClass, String::class.java)
                    .invoke(processor, dimension, id)
                val confirmed = processor.javaClass.getMethod("confirmMultiworld", dimensionClass)
                    .invoke(processor, dimension) as? Boolean ?: false
                dimension.javaClass.getMethod("saveConfigUnsynced").invoke(dimension)

                if (confirmed) {
                    log.info("[XaeroMapResetter] Switched world map to fresh multiworld {}", id)
                } else {
                    log.warn("[XaeroMapResetter] World map multiworld confirm was rejected; map not reset this round")
                }
            }
        } catch (e: ClassNotFoundException) {
            // Xaero's World Map is not installed — nothing to reset.
        } catch (e: Throwable) {
            log.warn("[XaeroMapResetter] Failed to reset Xaero world map", e)
        }
    }

    /**
     * Clear every waypoint in every world of every root container — covering all
     * dimensions the round touched, not just the current one — and persist each
     * emptied world so the waypoints don't reload from disk next game. Mirrors
     * Xaero's own `GuiClearSet`: `WaypointSet.clear()` then
     * `MinimapWorldManagerIO.saveWorld(...)`, applied across the whole world tree.
     */
    private fun clearMinimapWaypoints() {
        try {
            val minimapModule = Class.forName("xaero.hud.minimap.BuiltInHudModules")
                .getField("MINIMAP").get(null) ?: return
            val session = minimapModule.javaClass.getMethod("getCurrentSession").invoke(minimapModule) ?: return
            val worldManager = session.javaClass.getMethod("getWorldManager").invoke(session) ?: return
            val worldManagerIO = session.javaClass.getMethod("getWorldManagerIO").invoke(session)
            val minimapWorldClass = Class.forName("xaero.hud.minimap.world.MinimapWorld")
            val saveWorld = worldManagerIO.javaClass.getMethod("saveWorld", minimapWorldClass)

            val rootContainers = worldManager.javaClass.getMethod("getRootContainers").invoke(worldManager)
                    as? Iterable<*> ?: return
            var clearedWorlds = 0
            for (rootContainer in rootContainers) {
                if (rootContainer == null) continue
                val worlds = rootContainer.javaClass.getMethod("getAllWorldsIterable").invoke(rootContainer)
                        as? Iterable<*> ?: continue
                for (world in worlds) {
                    if (world == null) continue
                    val sets = world.javaClass.getMethod("getIterableWaypointSets").invoke(world)
                            as? Iterable<*> ?: continue
                    var changed = false
                    for (set in sets) {
                        if (set == null) continue
                        set.javaClass.getMethod("clear").invoke(set)
                        changed = true
                    }
                    if (changed) {
                        // Persist the now-empty world, or its waypoints reload from disk.
                        saveWorld.invoke(worldManagerIO, world)
                        clearedWorlds++
                    }
                }
            }
            if (clearedWorlds > 0) {
                log.info("[XaeroMapResetter] Cleared minimap waypoints across {} world(s)", clearedWorlds)
            }
        } catch (e: ClassNotFoundException) {
            // Xaero's Minimap is not installed — nothing to clear.
        } catch (e: Throwable) {
            log.warn("[XaeroMapResetter] Failed to clear Xaero minimap waypoints", e)
        }
    }
}
