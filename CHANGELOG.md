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

每个版本应包含：

- 版本号、发布日期、Minecraft 版本、加载器、License 和 Release Type。
- 先写中文更新日志。
- 再写英文更新日志，分类和含义应与中文对应。
- 分类建议按以下顺序使用：`新增内容`、`调整内容`、`修复内容`、`兼容性`。
- 如果某个版本没有对应内容，应省略该分类，不要保留空标题。
- 更新描述应保持客观、面向玩家和结果导向；除非涉及外部兼容，否则避免写入内部实现细节。

---

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
