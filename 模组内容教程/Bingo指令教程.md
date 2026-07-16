# Bingo 指令教程

本文面向玩家、服主和管理员，说明 ExBingo 常用指令的用途。部分指令需要 OP 或服务器配置权限；没有权限时，游戏内不会执行对应操作。

## 基础指令

`/bingo`：查看当前 Bingo 版本和本局主要设置。

`/bingo book`：获得教程书。服务器已经自动发放教程书时，此指令可能不可用。

`/ready`：切换自己的准备状态。达到服务器设置的准备条件后，会触发开局或下一轮倒计时。

`/ready cancel`：取消准备倒计时。需要配置权限。

`/join <队伍>`：加入指定队伍。队伍名以游戏内补全为准。

`/join spectators`：加入旁观者。

`/join <队伍> <玩家>`：将指定玩家加入队伍。需要管理玩家队伍的权限。

`/bingo shuffleteams <队伍数>`：赛前随机分队。需要配置权限。

`/spectator`：在允许的状态下切换旁观模式。通常用于观战或赛后查看。

## 队伍协作指令

`/teamchest` 或 `/tc`：打开队伍箱。旁观者可在对应流程中通过该入口申请加入队伍。

`/teamchest toggle`：开启或关闭队伍箱功能。需要配置权限，且通常只应在赛前调整。

`/teamchest count`：切换队伍箱内物品是否计入 Bingo 目标。需要配置权限，且通常只应在赛前调整。

`/teamtp <玩家>` 或 `/ttp <玩家>`：传送到同队玩家位置。只在游戏进行中生效，目标必须是同队玩家。

`/tptoggle`：开启或关闭同队传送功能。需要配置权限，且通常只应在赛前调整。

`/coords`：向队友发送自己的当前坐标。

`/coords <说明>`：向队友发送当前坐标，并附带说明文字。

`/coords list`：查看本队已记录的坐标信息。

聊天别名由服务器配置决定。默认配置若启用全局聊天或队伍聊天别名，玩家可以用对应别名发送全局消息或队内消息，具体命令以服务器补全为准。

## 开始、结束与重置

`/bingo start`：开始游戏。需要配置权限。

`/bingo start ignore_warnings`：忽略开局警告并开始游戏。常用于确认人数、队伍或数据包警告后强制开局。

`/bingo end`：结束当前游戏。需要配置权限。

`/bingo resume`：在存在可恢复对局时恢复游戏。

`/bingo reset`：重置游戏并返回下一轮准备状态。需要对应权限，只在游戏中或赛后可用。

`/bingo lobby`：在非大厅模式服务器中请求切换到大厅模式。

`/bingo lobby confirm`：确认执行大厅模式切换。

## 棋盘与格子管理

`/bingo card`：打开当前玩家所属队伍的 Bingo 棋盘。

`/bingo reroll`：重随整张当前棋盘。需要配置权限。

`/bingo card set <格子> <目标>`：把指定格子设置为指定目标。格子使用 `b1`、`i3`、`o5` 这类坐标，也可以用 `random` 随机选择一个格子。

目标可以直接写资源 ID：

```text
/bingo card set b1 minecraft:diamond
```

物品和成就 ID 相同时，可以使用类型限定：

```text
/bingo card set b1 item!create:sturdy_sheet
/bingo card set i1 advancement!create:sturdy_sheet
```

`item!` 表示只按物品目标设置，`advancement!` 表示只按成就目标设置。该指令的补全会同时提供普通 ID、`item!` ID 和 `advancement!` ID。

`/bingo card set <格子> random`：把指定格子随机替换为当前筛选和难度分布下的新目标。

`/bingo card set <格子> free_space`：把指定格子设置为免费格。

`/bingo card seed`：查看当前棋盘种子。

`/bingo card seed <种子>`：用指定种子重新生成当前棋盘。

`/bingo card stack`：查看当前服务器上的多棋盘列表。

`/bingo card stack push`：新增一张棋盘并放到棋盘列表前端。需要赛前配置权限。

`/bingo card stack pop`：移除当前最前端棋盘。需要赛前配置权限。

`/bingo card assign <队伍> <棋盘>`：把队伍分配到指定棋盘。棋盘参数通常使用 `/bingo card stack` 中显示的 `#1`、`#2` 等编号。

## 棋盘筛选与难度

`/bingo filter <筛选>`：设置当前棋盘筛选。可以输入预设名称，也可以输入过滤表达式。

常见过滤写法：

```text
/bingo filter everything
/bingo filter +create -unobtainable
/bingo filter +type=advancement -uncategorized
```

`+标签` 表示包含，`-标签` 表示排除，`标签#数量` 表示要求抽取指定数量。

`/bingo difficulty <S> <A> <B> <C> <D>`：设置五个难度档位在棋盘上的数量，总数通常应为 25。

示例：

```text
/bingo difficulty 1 4 8 8 4
```

`/bingo difficulty <预设名>`：使用配置中已有的难度预设，例如 `easy`、`medium`、`hard`、`extreme`，具体可用名称以游戏内补全为准。

`/bingo carddisable <棋盘>`：隐藏指定棋盘预设。需要管理棋盘预设的权限。

`/bingo carddisable list`：查看当前被隐藏的棋盘预设。

`/bingo cardenable <棋盘>`：重新启用被隐藏的棋盘预设。

## 对局规则设置

`/bingo goal <数量> items`：设置胜利目标为完成指定数量的格子。

`/bingo goal <数量> lines`：设置胜利目标为完成指定数量的连线。

`/bingo goal full_card items`：设置目标为完成整张棋盘。

`/bingo goal full_card lines`：设置目标为完成所有连线。

`/bingo mode lockout [true|false]`：切换锁定模式。启用后，一个目标通常只能由先完成的队伍获得。

`/bingo mode inventory [true|false]`：切换背包模式。

`/bingo mode hidden_items [true|false]`：切换隐藏目标模式。

`/bingo mode consume_items [true|false]`：切换物品完成后是否消耗。该模式需要大厅环境支持。

`/bingo options play_to cards <数量>`：设置本场打到指定棋盘数。

`/bingo options play_to infinite_cards`：设置为无限棋盘。

`/bingo options play_to replace_goals`：设置为完成目标后替换目标的玩法。

`/bingo options stalemate end_game`：僵局时结束游戏。

`/bingo options stalemate reroll_card`：僵局时重随棋盘。

`/bingo options stalemate do_nothing`：僵局时不自动处理。

`/bingo options end_when never`：不自动结束。

`/bingo options end_when first_win`：第一个队伍获胜后结束。

`/bingo options end_when teams_win <队伍数>`：指定数量的队伍获胜后结束。

`/bingo options end_when all_win`：所有队伍获胜后结束。

`/bingo options pvp [true|false]`：切换 PvP。

`/bingo options elytra [true|false]`：切换鞘翅开局。

`/bingo options night_vision [true|false]`：切换夜视。

`/bingo options preview_card [true|false]`：切换赛前预览棋盘。

`/bingo options spawn_distance <区块数>`：设置队伍出生点间距。

`/bingo options spawn_dimension <维度ID>`：设置出生维度。可选维度以游戏内补全为准。

`/bingo timelimit <分钟>`：设置时间限制。

`/bingo timelimit off`：关闭时间限制。

## 玩家偏好

`/bingoprefs bossbar [true|false]`：切换 Boss 栏显示。

`/bingoprefs scoreboard [true|false]`：切换计分板显示。

`/bingoprefs scoreboard_auto_hide [true|false]`：切换计分板自动隐藏。

`/bingoprefs leading_messages [true|false]`：切换领先提示。

`/bingoprefs score_messages [true|false]`：切换得分提示。

`/bingoprefs item_messages [true|false]`：切换物品完成提示。

`/bingoprefs night_vision [true|false]`：切换个人夜视偏好。

不写 `true` 或 `false` 时，部分偏好指令会直接反转当前状态。

## 统计与数据维护

`/bingostats`：查看自己的统计。

`/bingostats <玩家>`：查看指定玩家统计。需要查看他人统计的权限。

`/bingostats reset player`：重置自己的统计，具体可用性取决于服务器权限配置。

`/bingostats reset player <玩家>`：重置指定玩家统计。需要全局统计管理权限。

`/bingostats reset game`：重置当前规则组合对应的统计。需要全局统计管理权限。

`/bingostats reset all`：重置全部统计。需要全局统计管理权限。

`/bingo autotier generate`：自动分级未分类的非原版目标，并写入自动分级文件。需要自动分级权限。

`/bingo autotier clear`：清空自动分级结果。需要自动分级权限。

`/bingodata tag add <标签> <目标或通配符>`：向标签中加入目标。需要数据维护权限。

`/bingodata tag remove <标签> <目标或通配符>`：从标签中移除目标。需要数据维护权限。

`/bingodata tierlist add <列表> <档位> <目标或通配符>`：向难度表中加入目标。档位可为 `s`、`a`、`b`、`c`、`d` 或 `uncategorized`。

`/bingodata tierlist remove <列表> <目标或通配符>`：从难度表中移除目标。

数据维护指令支持 `*` 通配符。使用前建议先确认目标 ID，避免一次改动过多内容。

## 调试指令

`/bingo debug`：向服务器日志输出调试信息。需要调试权限。

该指令主要用于排查服务器状态，不属于常规玩家流程。
