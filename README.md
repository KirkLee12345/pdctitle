# PDCTitle（Fabric 称号模组）

纯服务端 Minecraft **Fabric 26.2** 称号模组，原版客户端即可加入、无需任何玩家安装 mod。所有指令以 `/pdctitle`（别名 `/pdc`）为根，避开原版已占用的 `/title`。

**称号池模型**：OP 维护全局称号列表（显示文案 + 描述，可增删改查）→ OP 把池中称号授权给玩家
（同一称号可发给很多人）→ 玩家自己选择佩戴/不佩戴。删除池中称号后，所有玩家的该称号随之消失。
佩戴的称号显示在**聊天栏 / Tab 列表 / 头顶名牌**的玩家名之前；聊天栏中把鼠标移到称号上会
**悬停显示称号描述**（仿成就提示样式）。

## 功能

- **① 称号池（OP）**：`add` / `edit` / `desc` / `remove` / `list` / `info` / `who`
  —— 删除定义级联清掉所有玩家该称号（佩戴中自动卸下），改文案/描述全服即时生效；
- **② 归属管理（OP）**：`grant` / `set`（授权+佩戴）/ `revoke` / `clear`，按 UUID 存储、支持离线玩家；
- **③ 玩家自助**：`my` / `wear` / `wear none` / `unwear` —— 仅限自己已被授权的称号；
- **悬停描述**：聊天栏称号挂 HoverEvent，显示 称号名 + 描述（原版客户端可见）；
- 数据存 `config/pdctitle/definitions.json`（称号池）+ `players.json`（归属/佩戴），原子写盘；
- 依赖极简：**仅 Fabric Loader**（零 fabric-api）。

## 当前状态

| 内容 | 状态 |
|---|---|
| 26.2 工具链骨架（Gradle/Loom/元数据/wrapper） | ✅ 就绪 |
| 数据层（TitleDefinition / PlayerData / TitleStore，v3 池模型） | ✅ 骨架（纯逻辑完整） |
| 三通道显示（名牌 Team / Tab / 聊天重发+悬停） | ⏳ 待实现（M0→M2） |
| 指令（池管理/归属/玩家自助） | ⏳ 待实现（M3） |
| 打包发布 | ⏳ 待实现（M4） |

> 当前为**骨架阶段**：含完整设计方案与纯逻辑数据层，但尚未编译验证、不可安装运行；
> 确认方案后按 `docs/04-开发里程碑.md` 从 M0 开始实现。

## 构建（需要 JDK 25）

```bash
./gradlew build     # 产物：build/libs/pdctitle-<version>.jar
```

首次构建会下载 Gradle 9.5.1 与 Minecraft 26.2 依赖，请保持网络通畅。

## 安装到服务器

1. Fabric 专用服务器（MC **26.2**，Fabric Loader **≥ 0.19.3**）；
2. 构建的 jar 放入服务器 `mods/`；
3. 启动服务器，自动生成 `config/pdctitle/definitions.json` 与 `players.json`；
4. 游戏内用 `/pdctitle` 操作（OP 可用池/归属管理指令）。

## 快速上手

```
/pdctitle add legend &b[至尊] 开服元老纪念称号   # OP：建池条目（空格后为可选描述，一条搞定）
/pdctitle desc legend 开服元老纪念称号          # OP：单独补/改描述（可选）
/pdctitle grant KirkLee123 legend              # OP：授权（可反复授权给不同玩家）
/pdctitle my                                   # 玩家：查看自己的称号
/pdctitle wear legend                          # 玩家：佩戴
/pdctitle wear none                            # 玩家：卸下
/pdctitle remove legend                        # OP：删除定义（所有玩家该称号随之消失）
```

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/01-需求与设计方案.md` | 需求、池/归属/佩戴模型、三通道 + 悬停描述技术方案、级联规则 |
| `docs/02-指令与配置参考.md` | 三级指令全集、配置格式、悬停行为说明、示例 |
| `docs/03-实现要点与M0验证清单.md` | 26.2 API 钩子、Mixin 目标、悬停实现要点、验证步骤 |
| `docs/04-开发里程碑.md` | M0~M4 计划与验收测试矩阵 |

## 技术栈速览

- Minecraft 26.2（unobfuscated / Mojang 官方映射）；Fabric Loader 0.19.5、Loom 1.17-SNAPSHOT、Java 25
- 三通道：Scoreboard Team（头顶）｜ Tab 显示名覆写+刷包 ｜ 聊天拦截重发 + HoverEvent 悬停描述

## License

MIT（见 `LICENSE`）
