# ExBingo Changelog / ExBingo 更新日志

This file records official release notes for ExBingo. Future releases should be added above older releases and should keep the same bilingual structure.

本文件用于记录 ExBingo 的正式发布更新日志。后续版本应追加在旧版本上方，并沿用同一套中英文双语结构。

## Format For Future Releases / 后续版本记录格式

Each release should include:

- Version, release date, Minecraft version, mod loader, license, and release type.
- A Chinese changelog first.
- An English changelog with the same categories and matching meaning.
- Categories in this order when applicable: `Added`, `Changed`, `Fixed`, `Compatibility`.
- If a category has no meaningful entry for a release, omit that category instead of leaving an empty section.
- Write release entries in objective, player-facing language. Prefer result-oriented phrasing and avoid internal implementation names unless they are needed for external compatibility.
- Cover every user-facing change, fix, and compatibility update included since the previous explicit version change, up to the release's version bump.
- Separate release entries with a line containing exactly `——————`.

每个版本应包含：

- 版本号、发布日期、Minecraft 版本、加载器、License 和 Release Type。
- 先写中文更新日志。
- 再写英文更新日志，分类和含义应与中文对应。
- 分类建议按以下顺序使用：`新增内容`、`调整内容`、`修复内容`、`兼容性`。
- 如果某个版本没有对应内容，应省略该分类，不要保留空标题。
- 更新描述应保持客观、面向玩家和结果导向；除非涉及外部兼容，否则避免写入内部实现细节。
- 每次写版本更新日志时，应覆盖上一次明确版本更换之后、直到本次版本号更换为止的所有面向玩家的改动、修复与兼容性调整。
- 每个版本更新日志之间用单独一行 `——————` 分割。

——————

## 1.0.6 - 2026-07-16

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.234
- License: LGPL-3.0-only
- Release Type: Feature update

### 中文更新日志

#### 新增内容

- 新增长对局性能维护。对局进行中实体数量超过阈值后，服务端会按 `server.performanceCleanup` 配置定期清理远离玩家的非持久实体、掉落物、投射物和普通生物，同时保留玩家、坐骑 / 乘客、自定义命名、持久、无敌、拴绳实体、ExBingo 实体和受保护命名空间。
- 新增 `allowNonOpGameConfiguration` 配置。启用后，非 OP 玩家也可以使用常规游戏配置指令和大厅菜单中的游戏设置控件。
- 新增对局中旁观者申请加入队伍流程。无队伍旁观者可在对局进行时使用 `/teamchest` / `/tc`，或在安装客户端时按队伍箱按键打开队伍选择；有在线成员的队伍需要队员点击同意，空队伍可直接加入。
- 新增玩家向自定义棋盘教程，说明如何编写 `*.tierlist.json`、添加 `itemFilterPresets`、使用 `item!` / `advancement!` 类型前缀，以及通过资源包设置棋盘显示名。

#### 调整内容

- 优化长局服务器性能。物品目标计分、地图维护、介绍书维护、大厅遗物清理和鞘翅补发等周期性工作现在会用更低频率或事件驱动方式运行，减少多人模组对局中的 TPS 抖动，同时保持计分反馈接近即时。
- 对局进行中所有玩家离线时，`PLAYING` 对局会暂停计时、物品检查、胜利判定和僵局判定，避免服务器无人在线时自动结束比赛；`POSTGAME` 的自动下一局 / 重置行为不受影响。

#### 修复内容

- 修复活跃对局中关服或重启后，服务器可能恢复成 `PREGAME`、但世界仍停留在对局地形中的问题。ExBingo 现在会在状态变化和关服流程中写入对局状态，并用临时文件、校验和备份降低保存文件损坏风险。
- 修复在 `STARTING`、`PRELOADING`、`LOADING` 或 `COUNTDOWN` 阶段关闭专用服务器时，已开始的对局可能未被当作活跃对局保护，进而走到错误的大厅 / 重置清理路径的问题。
- 修复 ExBingo 更新或迁移配置时可能删除、覆盖或清理 `config/exbingo/tierlists/` 下玩家自制棋盘的问题。自定义 tierlist 现在会在旧配置迁移、同名文件移动和资源缺失同步时被保留。
- 修复 NeoForge 配置桥在启动阶段可能用旧 TOML 覆盖 `config/exbingo/config.json` 的问题，并保留 JSON 中手动添加的 `itemFilterPresets`；游戏完全加载后，NeoForge 配置界面的运行时修改仍会正常同步回 JSON。
- 修复自定义棋盘中 `item!namespace:path` 与 `advancement!namespace:path` 类型限制可能丢失、同 ID 物品和进度被合并、或生成 / 计分时退回错误目标类型的问题。

#### 兼容性

- 修复对局中断线重连可能错误清理当前轮 Xaero 小地图 / 世界地图本地缓存的问题。现在只有客户端已回到大厅后断开连接，才会清理上一轮地图缓存与路径点。
- 清理永恒星光（Eternal Starlight）内置棋盘中的异常条目，移除无法正常作为棋盘目标的 `use_blossom_of_stars`、`gravity_pickaxe` 和 `crystallized_sand`。

### English Changelog

#### Added

- Added long-round performance maintenance. While a round is running, the server can periodically clean up far-away non-persistent entities, item drops, projectiles, and regular mobs once the entity count passes the `server.performanceCleanup` thresholds, while preserving players, mounts / passengers, custom-named, persistent, invulnerable, leashed entities, ExBingo entities, and protected namespaces.
- Added the `allowNonOpGameConfiguration` config option. When enabled, non-OP players can use regular game configuration commands and the game setting controls in the lobby menu.
- Added a spectator team-join flow during active rounds. Teamless spectators can use `/teamchest` / `/tc`, or the team chest keybind when the client mod is installed, to open team selection; teams with online members require teammate approval, while empty teams can be joined directly.
- Added a player-facing custom board tutorial covering `*.tierlist.json` files, `itemFilterPresets`, `item!` / `advancement!` type prefixes, and resource-pack display names for board presets.

#### Changed

- Improved long-round server performance. Item-objective scoring, map maintenance, intro book upkeep, lobby relic cleanup, elytra refreshes, and similar periodic work now run at lower frequency or from player-visible events, reducing TPS spikes in multiplayer modded rounds while keeping scoring feedback near instant.
- Active `PLAYING` rounds now pause timers, item checks, winner checks, and stalemate checks when every player is offline, preventing matches from ending while the server is empty. `POSTGAME` automatic next-round / reset behavior is unchanged.

#### Fixed

- Fixed shutting down or restarting during an active round sometimes restoring the server as `PREGAME` while the world was still the active game world. ExBingo now writes game state on state changes and during shutdown, using temp-file validation and backups to reduce save corruption risk.
- Fixed dedicated-server shutdown during `STARTING`, `PRELOADING`, `LOADING`, or `COUNTDOWN` not always being protected as an already-started round, which could route shutdown through the wrong lobby / reset cleanup path.
- Fixed ExBingo updates or config migrations potentially deleting, overwriting, or cleaning up player-authored boards under `config/exbingo/tierlists/`. Custom tier lists are now preserved through legacy config migration, same-name moves, and missing-resource sync.
- Fixed the NeoForge config bridge sometimes letting stale TOML overwrite `config/exbingo/config.json` during startup, and preserved JSON-added `itemFilterPresets`; after the game is fully loaded, runtime edits from the NeoForge config screen still sync back to JSON.
- Fixed custom board `item!namespace:path` and `advancement!namespace:path` type restrictions being lost, same-ID item and advancement targets being collapsed together, or generation / scoring falling back to the wrong objective type.

#### Compatibility

- Fixed disconnecting and reconnecting during an active round potentially clearing the current Xaero Minimap / World Map local cache. Previous-round map caches and waypoints are now cleaned only after the client has returned to the lobby and disconnects.
- Cleaned up the built-in Eternal Starlight board by removing invalid targets that could not be used correctly on cards: `use_blossom_of_stars`, `gravity_pickaxe`, and `crystallized_sand`.

——————

## 1.0.5 - 2026-07-12

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.234
- License: LGPL-3.0-only
- Release Type: Feature update

### 中文更新日志

#### 新增内容

- 新增附魔之瓶经验掉落覆盖功能。默认开启后每个附魔之瓶会掉落 100-500 点经验，并可通过 `experienceBottleXp.enabled`、`experienceBottleXp.min`、`experienceBottleXp.max` 或 NeoForge 配置界面的“游戏玩法”部分调整。

#### 修复内容

- 修复重置世界或第二局开局时，跨维度传送如果在服务端玩家追踪过程中部分失败，可能导致玩家卡在 `LOADING`、出生区块就绪检查无法完成的问题。现在会修复“实体已被目标世界追踪，但未加入该世界玩家列表”的异常状态，并重新同步维度切换后的客户端状态。

#### 兼容性

- 新增莱特兰扩充（L2Complements）内置专属棋盘与起步难度表。检测到该模组时，棋盘选择菜单会显示“莱特兰扩充”棋盘，聚焦高阶核心物品目标。
- 新增 Enigmatic Legacy+ / Enigmatic Legacy 大厅兼容清理。开局前大厅会移除神秘遗物开局赠送的护身符、七咒之戒等遗物（包含 Curios 饰品栏），并标记赠送 / 诅咒状态已处理，避免玩家在大厅反复获得或保留这些开局效果。

### English Changelog

#### Added

- Added a configurable Bottle o' Enchanting XP override. When enabled, each bottle drops 100-500 XP by default, configurable through `experienceBottleXp.enabled`, `experienceBottleXp.min`, `experienceBottleXp.max`, or the Gameplay section of the NeoForge config screen.

#### Fixed

- Fixed reset or second-round starts where a cross-dimension teleport could partially fail during server-side player tracking, leaving the player stuck in `LOADING` because spawn readiness checks could not complete. ExBingo now repairs the state where the entity is tracked by the target world but missing from that world's player list, then resyncs the post-dimension client state.

#### Compatibility

- Added a built-in L2Complements board and starter tier list. When L2Complements is installed, the card selection menu shows an L2Complements board focused on high-tier core item objectives.
- Added Enigmatic Legacy+ / Enigmatic Legacy lobby cleanup. In the pre-game lobby, starter relics such as the Enigmatic Amulet and Ring of Seven Curses are removed from inventories and Curios slots, and the gift / cursed state is marked as handled so those effects are not repeatedly granted or kept active in the lobby.

——————

## 1.0.4 - 2026-07-07

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.234
- License: LGPL-3.0-only
- Release Type: Feature update

### 中文更新日志

#### 新增内容

- 新增内置队伍共享箱。对局中同队玩家可使用 `/teamchest` 或 `/tc` 打开 27 格队伍箱；客户端安装 ExBingo 时也可用默认按键 `B` 快速打开。
- 新增队伍箱物品参与 Bingo 物品目标判定的选项。启用后，队伍箱中的物品可帮助完成物品格，并会在消耗物品模式中按需被扣除。
- 新增同队传送功能。对局中可使用 `/teamtp <玩家>` 或 `/ttp <玩家>` 传送到同队玩家。
- 新增队伍功能配置项与开局前切换指令：`teamChestEnabled`、`teamChestCountsForObjectives`、`teamTeleportEnabled`，以及 `/teamchest toggle`、`/teamchest count`、`/tptoggle`。
- 新增 `boardSourceWeights` 配置，可按棋盘来源调整抽取权重；将某个来源权重设为 `0` 可阻止该来源参与随机抽取。

#### 调整内容

- NeoForge 配置界面现在在连接多人服务器时可读取服务器端 Common 配置；拥有 OP 默认权限的玩家可直接提交修改，非 OP 玩家会看到该部分被禁用。
- 大厅介绍书新增队伍箱与队伍传送说明，并将多处命令文字改为可点击的命令建议。
- 调整 Ice and Fire 棋盘分级：下调龙冰刺、火焰炖菜、冰霜炖菜、眼罩和耳塞等目标的档位，使难度更贴近实际获取门槛。
- 将 Ice and Fire 的龙蛋、龙鳞块、海蟒鳞 / 鳞块、死亡蠕虫甲壳 / 护手 / 盔甲等颜色变体合并为“任意一种”目标，棋盘不会再要求必须获取某个具体颜色。
- 继续校准 Cataclysm、Twilight Forest、Eternal Starlight 和 The Bumblezone 的棋盘内容，调整部分末地 / 黑暗森林阶段目标档位，并移除战利品袋、花粉堆等不适合作为普通棋盘目标的条目。

#### 修复内容

- 修复高内存占用或区块加载较慢时，开局后出生区域可能只显示地形但实体不渲染、方块破坏后回弹或露出虚空，必须退出重进才能恢复的问题。现在会预加载正确的出生区块，并等待客户端地形接收与服务端区块 / 实体跟踪都就绪后再继续开局。
- 修复默认难度预设被配置界面写回后可能按字母排序，导致大厅难度菜单显示 `easy / extreme / hard / impossible`、缺少 `medium` 的问题。
- 修复专用服务器上原版收纳袋功能包启用后，收纳袋配方仍可能被旧兔皮配方或数据包顺序影响的问题；现在内置皮革加线配方，并确保 ExBingo 配方覆盖生效。

#### 兼容性

- 修复与 Simple Voice Chat 的客户端兼容问题：语音连接重置时渲染大厅交互实体不再导致客户端崩溃。

### English Changelog

#### Added

- Added built-in team shared chests. During a round, teammates can open a 27-slot team chest with `/teamchest` or `/tc`; clients with ExBingo installed can also use the default `B` keybind.
- Added an option for team chest contents to count toward Bingo item objectives. When enabled, chest items can complete item tiles and are consumed as needed in consume-items mode.
- Added same-team teleporting. During a round, players can use `/teamtp <player>` or `/ttp <player>` to teleport to a teammate.
- Added team feature config options and pre-game toggle commands: `teamChestEnabled`, `teamChestCountsForObjectives`, `teamTeleportEnabled`, plus `/teamchest toggle`, `/teamchest count`, and `/tptoggle`.
- Added `boardSourceWeights`, allowing card generation weights to be tuned by board source; setting a source weight to `0` prevents that source from being randomly picked.

#### Changed

- The NeoForge configuration screen can now read the server-side Common config while connected to a multiplayer server. Players with the OP-default permission can submit edits directly, while non-OP players see that section disabled.
- Added team chest and team teleport information to the lobby intro book, and changed multiple command texts into clickable command suggestions.
- Adjusted Ice and Fire board tiers by lowering dragon ice spikes, fire stew, frost stew, blindfold, earplugs, and related targets so their tiers better match their real acquisition difficulty.
- Combined Ice and Fire colored variants such as dragon eggs, dragon scale blocks, sea serpent scales / scale blocks, and death worm chitin / gauntlets / armor into "any of" objectives, so cards no longer require one specific color.
- Continued tuning the Cataclysm, Twilight Forest, Eternal Starlight, and The Bumblezone boards by adjusting several End / Dark Forest stage tiers and removing loot bags, pollen piles, and similar entries that were not suitable as regular card targets.

#### Fixed

- Fixed game starts under high memory pressure or slow chunk loading where spawn terrain could appear but entities failed to render, broken blocks rubber-banded back or exposed void, and players had to reconnect to recover. Spawn preloading now targets the correct chunks, and the game waits for both client terrain delivery and server-side chunk / entity tracking readiness before continuing.
- Fixed default difficulty presets being written back alphabetically by the config screen, which could make the lobby difficulty menu show `easy / extreme / hard / impossible` and omit `medium`.
- Fixed dedicated servers with the vanilla bundle feature pack enabled still being affected by the old rabbit-hide recipe or data-pack ordering. ExBingo now includes the leather-plus-string bundle recipe and ensures that override wins.

#### Compatibility

- Fixed a Simple Voice Chat client compatibility issue where rendering lobby interaction entities during a voice connection reset could crash the client.

——————

## 1.0.3 - 2026-07-07

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.234
- License: LGPL-3.0-only
- Release Type: Feature update

### 中文更新日志

#### 新增内容

- 新增 NeoForge 模组配置界面入口，可在游戏内调整 ExBingo 的常用服务端与客户端选项，并继续同步到原有 `config/exbingo/config.json` 配置文件。
- 新增可选的物品与进度难度标记。开启后，带有 ExBingo 难度分级的物品、宾果卡目标、得分提示和进度图标会显示 `S/A/B/C/D` 字母。
- 新增 Xaero 的小地图 / 世界地图联动增强：对局中同步同队远距离玩家位置；队伍玩家分享 Xaero 路径点时会自动转为队内消息。

#### 调整内容

- 宾果卡与得分提示中的难度标记现在使用目标本身的分级，进度目标不会再因为共用展示物品而显示成该物品的分级。
- 优化宾果卡 HUD 与地图同步流程，减少无闪烁格子时的空转刷新，并在图片、洗牌、换卡、队伍变化和资源重载后主动同步必要状态。
- 调整 Cataclysm 与 Ice and Fire 棋盘分级，重新平衡末地结构方块、碎肉机、龙骨、精灵、巢穴、龙控制道具、元素百合、特殊食物等目标的档位。

#### 修复内容

- 修复服务器在 PREGAME 状态重启时，手动编辑的 `game-options.json` 可能被存档中的旧对局选项覆盖，导致棋盘仍按旧设置生成的问题。
- 修复玩家初次进入服务器或重置后，客户端可能只显示空宾果卡框、缺失 25 个格子内容，直到后续完成格子或切换菜单才刷新的问题。
- 修复开局等待地形加载时可能误用大厅或上一局残留的加载信号，导致玩家过早进入世界、卡在虚空或无法破坏方块的问题。
- 修复重置世界后，新世界可能没有被服务器 tick 循环接管、实体加载队列不处理，进而导致回到大厅或下一局出现实体冻结、方块交互异常的问题。
- 修复大厅左键破坏方块时可能触发未使用事件桥并导致崩溃的问题。
- 修复重掷棋盘、手动改格、切换卡组、修改出生装备、僵局换卡或完成后换卡时，HUD / 地图棋盘可能仍显示旧内容的问题。
- 修复从宾果卡目标使用 JEI 收藏快捷键时，带自定义显示数据的物品可能收藏异常，并避免该快捷键把已收藏项目反向移除的问题。
- 修复专用服务器默认未启用原版收纳袋功能包时，收纳袋相关内容可能不可用的问题。

#### 兼容性

- 物品与进度难度标记支持原版进度界面与 Better Advancements 进度界面；联机时以服务器同步的难度表为准，资源重载后会自动刷新。

### English Changelog

#### Added

- Added a NeoForge mod configuration screen entry for ExBingo's common server-side and client-side options, while continuing to sync back to the existing `config/exbingo/config.json` configuration file.
- Added an optional item and advancement difficulty overlay. When enabled, items, Bingo card targets, score messages, and advancement icons with an ExBingo tier show `S/A/B/C/D` letters.
- Added enhanced Xaero Minimap / World Map integration: distant teammates are synced during rounds, and shared Xaero waypoints from players on an ExBingo team are routed only to teammates.

#### Changed

- Difficulty labels on Bingo cards and score messages now use the objective's own tier, so advancement objectives no longer inherit the tier of their display item.
- Optimized Bingo card HUD and map syncing by reducing idle refresh work when no tiles are flashing, while actively syncing required state after image, shuffle, card assignment, team, and resource reload changes.
- Adjusted the Cataclysm and Ice and Fire board tiers, rebalancing End structure blocks, the Meat Shredder, dragonbone, pixie, nests, dragon control items, elemental lilies, special foods, and related targets.

#### Fixed

- Fixed manual edits to `game-options.json` being overwritten by stale saved PREGAME options after a server restart, causing cards to regenerate with old settings.
- Fixed clients sometimes showing an empty Bingo card frame after joining or after a reset, with the 25 tile contents missing until a later score or menu update.
- Fixed game start terrain loading checks reusing stale lobby or previous-round loading signals, which could send players into the world too early and leave them in the void or unable to break blocks.
- Fixed live world resets where the newly recreated worlds were not picked up by the server tick loop and entity loading queue, causing frozen entities or broken block interactions in the lobby or next round.
- Fixed a crash that could occur when left-clicking lobby blocks through an unused block-attack event bridge.
- Fixed rerolls, manual tile edits, card stack changes, spawn kit edits, stalemate card skips, and post-completion card changes leaving stale HUD or map card contents on clients.
- Fixed JEI bookmarking from Bingo card targets when the displayed stack contained ExBingo custom data, and prevented the bookmark hotkey from removing an already-bookmarked item.
- Fixed dedicated servers not enabling the vanilla bundle feature pack by default, which could leave bundle-related content unavailable.

#### Compatibility

- Item and advancement difficulty overlays support both the vanilla advancements screen and the Better Advancements screen. In multiplayer, server-synced tier data is authoritative and refreshes after resource reloads.

——————

## 1.0.2 - 2026-07-04

- Minecraft: 1.21.1
- Mod Loader: NeoForge 21.1.234
- License: LGPL-3.0-only
- Release Type: Feature update

### 中文更新日志

#### 新增内容

- 新增在宾果卡界面直接收藏物品的功能，可在棋盘上快速标记关注的目标。

#### 调整内容

- 调整暮色森林棋盘的物品分级，并移除部分不适合作为收集目标的条目。

#### 修复内容

- 修复多人联机开局时，网络延迟较高的玩家可能在地形加载完成前被送入世界，出现卡在虚空、无法破坏方块、在其他玩家视角中原地不动的问题；现在会等待每位玩家的出生地形真正加载完成后再开始游戏。

### English Changelog

#### Added

- Added the ability to favorite items directly from the Bingo card screen, so target items can be quickly marked on the board.

#### Changed

- Adjusted item tiers on the Twilight Forest board and removed some entries that were not suitable as collection targets.

#### Fixed

- Fixed multiplayer game starts where higher-latency players could be dropped into the world before their terrain finished loading, leaving them stuck in the void, unable to break blocks, and appearing to stand still to other players. The game now waits until each player's spawn terrain has actually loaded before starting.
