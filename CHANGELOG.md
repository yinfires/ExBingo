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
