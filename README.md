# ExBingo

<details>
<summary>中文介绍</summary>

## 简介

ExBingo 是 [Yet Another Bingo](https://modrinth.com/mod/yet-another-minecraft-bingo) 的 NeoForge 1.21.1 移植版本，提供多人 Bingo 物品收集玩法。

本模组保留原模组的主要玩法、资源和数据结构，并为更多的模组添加兼容。

在移植过程中，ExBingo 对原模组进行了大量代码重写，因此部分功能和表现会与原模组存在差别。

## 内置功能

- 多人 Bingo 物品收集、队伍、棋盘与大厅流程。
- 队伍共享箱：每个 Bingo 队伍拥有持久化共享箱，可配置是否参与 Bingo 物品目标判定与消耗物品模式。
- 队伍传送：游戏中同队玩家可互相传送。
- 客户端 HUD/快捷键支持：客户端安装本模组后，可用默认按键 `B` 打开队伍箱。

**队伍共享箱与传送指令**

- `/teamchest` 或 `/tc` —— 打开所在队伍的共享箱。
- `/teamtp <玩家>` 或 `/ttp <玩家>` —— 传送到同队玩家。
- `/teamchest toggle`、`/teamchest count`、`/tptoggle` —— OP 在游戏开始前切换队伍箱、队伍箱物品判定、队伍传送。

Bingo 游戏重置时会清空所有队伍箱。

## 联动与兼容

**模组联动**

- JEI物品管理器：从宾果卡目标打开物品配方界面。
- 简单的语音聊天（Simple Voice Chat）：按队伍管理语音分组。
- Xaero 的小地图 / 世界地图：对局中同步同队远距离玩家位置；对局结束返回大厅时，自动清理本局已探索的地图缓存与路径点。

<details>
<summary>提供专属棋盘与详细难度分级</summary>

- 农夫乐事（Farmer's Delight）
- 暮色森林（Twilight Forest）
- 灾变（L_Ender's Cataclysm）
- 冰火传说（Ice and Fire）
- 永恒星光（Eternal Starlight）
- 逆卡巴拉：觉醒（Qliphoth Awakening）
- 蜜蜂领域（The Bumblezone）
- Mowzie 的生物（Mowzie's Mobs）
- 阿撒泻勒邪教（Cult of Azazel）
- Iron 的法术与魔法书（Iron's Spells 'n Spellbooks）
- 莱特兰扩充（L2Complements）
- 深暗之园（The Undergarden）

</details>



**棋盘内容兼容**

ExBingo 默认就能识别其他模组的物品与进度，并按以下规则处理棋盘内容：

- **已分类的模组内容**（在某张难度表中）会正常进入棋盘。
- **未分类的模组内容**默认不会进入棋盘，避免大量未经平衡的内容混入。
- **无法破坏的方块**（基岩、屏障、命令方块等）默认不会进入棋盘。
- 原版内容的表现保持不变。

当你拥有 OP 权限时，可用指令 `/bingo autotier generate` 按获取难度，一键为未分类的模组内容自动分级（不会改动原版内容、模组自带分级或已分配内容）。

**关闭某个棋盘**

每个棋盘对应一个过滤预设（如 `items`、`farmersdelight`、`twilightforest`、`cataclysm` 等）。OP 可关闭不想要的棋盘，关闭后它不会出现在选棋盘界面，也不会被“全选”选入：

- `/bingo carddisable <棋盘>` —— 关闭某个棋盘。
- `/bingo carddisable list` —— 列出当前已关闭的棋盘。
- `/bingo cardenable <棋盘>` —— 重新启用某个棋盘。

关闭列表保存在服务端 `config/exbingo/config.json` 的 `disabledFilterPresets` 字段中，立即生效、重启后保留，无需 `/reload`。也可直接编辑该字段（填入棋盘 id 列表）。

## 配置文件

服务端配置位于存档/服务器的 `config/exbingo/` 目录，可直接用文本编辑器修改：

- `config/exbingo/config.json` —— 主配置。例如：`excludeModUncategorizedFromCards` 控制未分类模组内容是否默认排除，`excludeUnbreakableBlocksFromCards` 控制无法破坏方块是否默认排除，`revealAllAdvancements` 控制原版进度树（按 L 的界面）是否一开始就显示全部进度而非需先解锁前置（默认开启），队伍功能见 `teamChestEnabled`、`teamChestCountsForObjectives`、`teamTeleportEnabled`，自动分级参数见 `autoTier` 段，已关闭的棋盘见 `disabledFilterPresets` 段。
- `config/exbingo/tierlists/*.tierlist.json` —— 各难度表，按 `S/A/B/C/D` 分档；`autotier.tierlist.json` 由自动分级生成。
- `config/exbingo/game-options.json` —— 棋盘与对局选项。

修改后用 `/reload` 或重启服务器生效。这些文件由 ExBingo 跟踪：模组更新会自动同步**你未手动改过**的文件；一旦你手改了某个文件，该文件就**不再被覆盖**，你的改动会被保留。

## 许可证与来源

ExBingo 是 Yet Another Bingo 的派生版本，使用 `LGPL-3.0-only` 许可证。队伍共享箱与队伍传送功能参考了 MIT 许可的 [YetAnotherBingo-TeamChest](https://github.com/ImCZFy/YetAnotherBingo-TeamChest)（Copyright (c) 2025 ChengZhiFy）。许可证文本见 [LICENSE](LICENSE) 和 [COPYING](COPYING)，来源与鸣谢见 [NOTICE](NOTICE)。

</details>


## Introduction

ExBingo is a NeoForge 1.21.1 port of [Yet Another Bingo](https://modrinth.com/mod/yet-another-minecraft-bingo), providing a multiplayer Bingo item-hunt game mode.

This mod keeps the upstream mod's main gameplay, resources, and data layout, and add compatibility for more mods.

During the port, ExBingo rewrote a large portion of the upstream mod's code, so some features and behavior differ from the original mod.

## Built-In Features

- Multiplayer Bingo item-hunt rounds with teams, cards, and lobby flow.
- Team chest: every Bingo team has a persistent shared chest, configurable for Bingo item scoring and consume-items mode.
- Team teleport: teammates can teleport to each other during a game.
- Client HUD/keybind support: with the client installed, the default `B` key opens the team chest.

**Team chest and teleport commands**

- `/teamchest` or `/tc` — open your team's shared chest.
- `/teamtp <player>` or `/ttp <player>` — teleport to a teammate.
- `/teamchest toggle`, `/teamchest count`, and `/tptoggle` — ops can toggle team chest, chest item scoring, and team teleport before the game starts.

All team chests are cleared when the Bingo game resets.

## Integrations

**Mod integrations**

- JEI: opens item recipe views from bingo card targets.
- Simple Voice Chat: manages voice groups by team.
- Xaero's Minimap / World Map: during a round, distant teammates are synced to the map; on returning to the lobby, the round's explored map cache and waypoints are cleared automatically.

<details>
<summary>provides a dedicated board and detailed difficulty tiers</summary>

- Farmer's Delight
- Twilight Forest
- L_Ender's Cataclysm
- Ice and Fire
- Eternal Starlight
- Qliphoth Awakening
- The Bumblezone
- Mowzie's Mobs
- Cult of Azazel
- Iron's Spells 'n Spellbooks
- L2Complements
- The Undergarden


</details>


**Card content compatibility**

ExBingo recognizes other mods' items and advancements out of the box, and handles card content with these rules:

- **Categorized modded content** (present in a tier list) appears on cards normally.
- **Uncategorized modded content** is kept off cards by default, to avoid flooding the game with unbalanced content.
- **Unbreakable blocks** (bedrock, barrier, command blocks, ...) are kept off cards by default.
- Vanilla behavior is unchanged.

When you have OP permission, you can run `/bingo autotier generate` to auto-classify uncategorized modded content by how hard it is to obtain (it never changes vanilla content, mod-provided tiers, or anything already assigned). 

**Disabling a board**

Each board is a filter preset (e.g. `items`, `farmersdelight`, `twilightforest`, `cataclysm`). Ops can disable boards they don't want; a disabled board never appears in the board-selection menu and is never included by any "select all":

- `/bingo carddisable <board>` — disable a board.
- `/bingo carddisable list` — list the currently disabled boards.
- `/bingo cardenable <board>` — re-enable a board.

The disabled set is stored server-side in the `disabledFilterPresets` field of `config/exbingo/config.json`. It applies immediately and persists across restarts, with no `/reload` needed. You can also edit that field directly (a list of board ids).

## Configuration Files

Server-side configuration lives in the world/server `config/exbingo/` directory and can be edited directly with a text editor:

- `config/exbingo/config.json` — main config. For example, `excludeModUncategorizedFromCards` controls whether uncategorized modded content is excluded by default, `excludeUnbreakableBlocksFromCards` controls unbreakable-block exclusion, `revealAllAdvancements` controls whether the vanilla advancement tree (the "L" screen) shows every advancement from the start instead of hiding entries until their prerequisites are unlocked (on by default), team features are controlled by `teamChestEnabled`, `teamChestCountsForObjectives`, and `teamTeleportEnabled`, auto-tier parameters live under the `autoTier` block, and disabled boards under `disabledFilterPresets`.
- `config/exbingo/tierlists/*.tierlist.json` — tier lists, split into `S/A/B/C/D`; `autotier.tierlist.json` is generated by auto-tiering.
- `config/exbingo/game-options.json` — board and match options.

Run `/reload` or restart the server to apply changes. These files are tracked by ExBingo: mod updates sync automatically to files **you haven't edited**, but once you edit a file, it is **no longer overwritten** and your changes are preserved.

## License And Attribution

ExBingo is a derivative version of Yet Another Bingo and is distributed under `LGPL-3.0-only`. The team chest and team teleport features reference the MIT-licensed [YetAnotherBingo-TeamChest](https://github.com/ImCZFy/YetAnotherBingo-TeamChest) project (Copyright (c) 2025 ChengZhiFy). See [LICENSE](LICENSE) and [COPYING](COPYING) for license texts, and [NOTICE](NOTICE) for source and credit information.
