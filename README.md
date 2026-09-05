# TitleMod（Fabric 称号模组）

纯服务端 Minecraft **Fabric 26.2** 称号模组：OP 授予/收回称号，玩家自由佩戴自己拥有的称号
（可拥有 **0..N** 个、**至多佩戴 1 个**，也可不佩戴），佩戴的称号显示在**聊天栏 / Tab 列表 /
头顶名牌**的玩家名字之前。原版客户端即可加入，**无需任何玩家安装 mod**。

## 功能

- 称号管理：`grant`（授予）、`set`（授予+佩戴）、`revoke`（收回，佩戴中自动卸下）、
  `clear`（清空）、`list`（查看）；
- 玩家自助：`wear` / `wear none` / `unwear` / `my`（仅限自己拥有的称号）；
- 按 **UUID** 存储（改名不掉称号）；离线玩家也可被授予，上线自动生效；
- 三个显示通道独立总开关（chat / tab / nametag，配置规划中）；
- 依赖极简：**仅 Fabric Loader**（零 fabric-api），数据存 `config/titlemod/titles.json`。

## 当前状态

| 内容 | 状态 |
|---|---|
| Gradle / 资源 / 元数据骨架（26.2 工具链） | ✅ 已就绪 |
| 数据层类骨架（TitleData / PlayerData / TitleStore） | ✅ 已就绪 |
| 三通道显示实现（名牌 Team / Tab / 聊天重发） | ⏳ 待实现（M0→M2） |
| 指令实现 | ⏳ 待实现（M3） |
| 打包发布 | ⏳ 待实现（M4） |

> 说明：当前工程为**骨架阶段**，含完整设计方案文档但逻辑尚未填充，**暂不可安装运行**；
> 请在确认方案后按 `docs/04-开发里程碑.md` 从 M0 开始实现。

## 构建（需要 JDK 25）

```bash
./gradlew build
# 产物：build/libs/titlemod-<version>.jar
```

首次构建会下载 Gradle 9.5.1 与 Minecraft 26.2 相关依赖，请保持网络通畅。

## 安装到服务器

1. 准备 Fabric 专用服务器（MC **26.2**，Fabric Loader **≥ 0.19.3**）；
2. 把构建出的 jar 放入服务器 `mods/` 目录；
3. 启动服务器；首次运行自动生成 `config/titlemod/titles.json`；
4. 控制台/游戏内用 `/title` 系列指令操作（OP 默认可用管理指令）。

## 快速上手

```
/title set KirkLee123 &b[至尊]     # OP：授予并佩戴
/title grant KirkLee123 &a[创世元老] # OP：再加一条（不改变佩戴）
/title wear &a[创世元老]           # 玩家：切换佩戴
/title wear none                    # 玩家：卸下
/title my                           # 查看自己的称号
/title revoke KirkLee123 &b[至尊]  # OP：收回
```

## 文档索引

| 文档 | 内容 |
|---|---|
| `docs/01-需求与设计方案.md` | 需求、数据模型、三通道技术方案、权限模型 |
| `docs/02-指令与配置参考.md` | 全部指令、配置格式、示例 |
| `docs/03-实现要点与M0验证清单.md` | 26.2 API 钩子、Mixin 目标、验证步骤、风险 |
| `docs/04-开发里程碑.md` | M0~M4 计划与验收测试矩阵 |

## 技术栈速览

- Minecraft 26.2（unobfuscated / Mojang 官方映射，无 Yarn、无 remap）
- Fabric Loader 0.19.5、Loom 1.17-SNAPSHOT、Gradle 9.5.1、Java 25
- 三通道机制：Scoreboard Team（头顶）｜ Tab 列表显示名覆写+刷包｜ 聊天签名拦截重发（详见 docs/01 §5）

## License

MIT（见 `LICENSE`）
