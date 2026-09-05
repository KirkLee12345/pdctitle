# PDCTitle（Fabric 称号模组）

纯服务端 Minecraft **Fabric 26.2** 称号模组。原版客户端即可加入，**无需任何玩家安装 mod**。
所有指令以 `/pdctitle`（简写 `/plt`）为根（原版已占用 `/title`，故不使用）。

**称号池模型**：OP 维护全局“称号池”（显示文案 + 描述，可增删改查）→ 把池中称号授权给玩家
（同一称号可发给很多人）→ 玩家自己点击佩戴/切换或不佩戴。删除池中称号后，所有玩家的该称号级联消失。
佩戴后称号显示在**玩家名字之前**：聊天栏、头顶名牌；聊天栏中鼠标悬停称号可看描述（含颜色/格式码）。

## 功能

- **① 称号池（OP）**：`add / edit / desc / remove / list / info / who`
  - `add` 可直接带描述：`/pdctitle add <id> <显示文案> [描述...]`
  - 显示文案与描述均支持 `&` 颜色/格式码与中文；
  - 显示文案含 `&`、`[]`、空格等特殊字符时，用**英文双引号**包起来（如 `"&b[至尊]"`）；
  - 删除定义 → 所有玩家该称号级联清除（佩戴中自动卸下）；改文案/描述全服即时生效。
- **② 归属管理（OP）**：`grant / set`（授权+佩戴）`/ revoke / clear`；按 UUID 存储，支持离线玩家，改名不掉授权。
- **③ 玩家自助**：
  - `my`：**聊天菜单**——提示语 + 称号列表；**点击称号立即佩戴/切换**；灰色 `[不佩戴称号]` 按钮点击卸下；
  - `wear <id>` / `wear none` / `unwear` 命令行方式（仅限自己已获授权的称号）。
- **显示**：
  - 聊天格式仿原版：`[至尊] <KirkLee123> 消息内容`（称号在尖括号外，悬停看描述）；
  - 头顶名牌：专属 Scoreboard Team 前缀（原生同步，所有原版客户端可见）；
  - Tab：默认关闭（`config.json` 中 `"tab": false`），避免与其它管理 Tab 的模组冲突；需要时可开启。
- **审计日志**：只记录称号增删改查与授权/佩戴取下的变更，查看类（`my/list/info` 等）不刷日志。
- 数据存 `config/pdctitle/definitions.json`（称号池）+ `players.json`（归属/佩戴）+ `config.json`（通道开关），原子写盘；
- 依赖极简：**仅 Fabric Loader**（零 fabric-api）。

## 当前状态

| 内容 | 状态 |
|---|---|
| 26.2 工具链（Loom 1.17 / Loader 0.19.5 / Java 25） | ✅ 可构建 |
| 数据层（称号池 / 归属 / 佩戴 / 审计日志） | ✅ 完成 |
| 三通道显示（名牌 Team / Tab 可选 / 聊天重发 + 悬停描述） | ✅ 完成 |
| 指令与聊天菜单 | ✅ 完成 |
| 本机验证 | ✅ 编译 + 无头服务器 + RCON 实测（命令/聊天格式/日志精简） |
| 多人真机联调 | ✅ 已由服主在 26.2 服务器实测通过 |

## 构建（需要 JDK 25）

```bash
./gradlew build     # 产物：build/libs/pdctitle-0.1.0-SNAPSHOT.jar
```

首次构建会下载 Gradle 9.5.1 与 Minecraft 26.2 依赖，请保持网络通畅。

## 安装到服务器

1. 服务器须为 **Fabric 专用服务器**，Minecraft **26.2**、Fabric Loader **≥ 0.19.3**；
2. 把构建出的 jar（或在 Release 页下载）放入服务器 `mods/` 目录；
3. 启动服务器。首次运行自动生成 `config/pdctitle/config.json`（`definitions.json`/`players.json` 在第一次变更时生成）；
4. 如需调整通道开关，编辑 `config/pdctitle/config.json`：
   ```json
   { "chat": true, "tab": false, "nametag": true }
   ```
   改完控制台执行 `/pdctitle reload`（或重启）生效；
5. 用 `/pdctitle add` 建称号 → `/pdctitle grant` 授权 → 玩家 `/plt my` 点击佩戴。

## 权限说明

- **OP（op 名单 / ops.json 中）**：可执行 `add/edit/desc/remove/list/info/who`（称号池管理）与
  `grant/set/revoke/clear`（归属管理）；
- **任何玩家**：可执行 `my/wear/wear none/unwear`，但只能佩戴/卸下**自己被授权**的称号（服务端校验）；
- **服务器控制台**：默认视为 OP，可执行全部指令；
- 注意：第三方验证/离线服（authlib-injector 等）的消息在服务端属“未签名”，本模组已按未签名消息路径处理，
  重发后的聊天行不会出现原版 `[Not Secure]` 标记。

## 指令速查

| 分类 | 指令 | 说明 |
|---|---|---|
| ① 池（OP） | `/pdctitle add <id> <显示文案> [描述...]` | 新增称号；文案/描述支持 & 码，特殊字符用引号 |
| | `/pdctitle desc <id> <描述>` | 单独设置/修改描述（`none` 清空） |
| | `/pdctitle edit <id> <显示文案>` | 修改显示文案（全服即时生效） |
| | `/pdctitle remove <id>` | 删除称号（级联清理所有玩家） |
| | `/pdctitle list / info <id> / who <id>` | 查看池 / 单条详情 / 拥有者 |
| ② 归属（OP） | `/pdctitle grant <玩家> <id>` | 授权称号 |
| | `/pdctitle set <玩家> <id>` | 授权并立即佩戴 |
| | `/pdctitle revoke <玩家> <id>` / `clear <玩家>` | 收回 / 清空 |
| ③ 玩家 | `/plt my` | 聊天菜单：点击称号佩戴/切换，点 `[不佩戴称号]` 卸下 |
| | `/plt wear <id>` / `wear none` / `unwear` | 命令行佩戴/卸下 |
| 通用 | `/pdctitle reload` | 重载配置与数据 |

示例：

```
/pdctitle add legend "&b[至尊]" 开服最早一批元老玩家的纪念称号
/pdctitle grant KirkLee123 legend
/pdctitle grant Alice legend          # 同一称号发给多人
# 玩家侧：/plt my → 点击 [至尊] 即佩戴；点击 [不佩戴称号] 卸下
/pdctitle remove legend               # 删除后所有玩家该称号消失
```

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/01-需求与设计方案.md` | 需求、池/归属/佩戴模型、三通道 + 悬停描述技术方案、级联规则 |
| `docs/02-指令与配置参考.md` | 三级指令全集、参数规则（引号/&码）、配置格式、示例 |
| `docs/03-实现要点与M0验证清单.md` | 26.2 API 钩子、Mixin 清单、聊天拦截与参数解析要点 |
| `docs/04-开发里程碑.md` | M0~M4 计划与验收测试矩阵 |

## 技术栈速览

- Minecraft 26.2（unobfuscated / Mojang 官方映射，无 Yarn、无 remap）
- Fabric Loader 0.19.5、Loom 1.17-SNAPSHOT、Gradle 9.5.1、Java 25
- 三通道：Scoreboard Team（头顶）｜ Tab 显示名覆写（默认关）｜ 聊天漏斗拦截重发 + HoverEvent 悬停描述
- 纯 Mixin + 纯 Java 实现，零 fabric-api

## Credits

本项目由 **DeepSeek Harness** 辅助完成开发（需求梳理、26.2 生态调研、代码实现、编译与运行时验证、文档）。

## License

MIT（见 `LICENSE`）：可自由使用/修改/再分发（含商用），只需保留原作者版权声明。
