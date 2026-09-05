package net.kirklee.pdctitle;

/**
 * Brigadier 指令树定义（骨架；实现见 M3 里程碑）。三级指令：
 *
 * ① 称号池管理（OP，level≥2）：
 *   /title add    <id> <显示文案...>        新增称号定义（description 留空，随后用 desc 设置）
 *   /title edit   <id> <显示文案...>        修改显示文案（对所有已授权玩家即时生效）
 *   /title desc   <id> <描述...>            设置描述；参数为 none 则清空
 *   /title remove <id>                     删除定义 → 所有玩家的该称号级联消失（佩戴中自动卸下）
 *   /title list   [page]                   分页列出全部称号定义（含描述）
 *   /title info   <id>                     查看单条定义详情
 *   /title who    <id>                     查看哪些玩家拥有该称号
 *
 * ② 归属管理（OP，level≥2）：
 *   /title grant  <玩家> <id>              授权池中称号给玩家（重复授权提示已拥有）
 *   /title set    <玩家> <id>              授权 + 立即佩戴（快捷）
 *   /title revoke <玩家> <id>              收回授权（若佩戴中自动卸下）
 *   /title clear  <玩家>                   清空该玩家全部授权并卸下
 *
 * ③ 玩家自助（任意玩家，仅限自己）：
 *   /title my                              查看自己拥有的称号/佩戴（每条显示文案可悬停看描述）
 *   /title wear  [id]                      佩戴自己拥有的称号；省略 id 时列出可选
 *   /title wear  none | /title unwear      卸下
 *
 * 实现要点：
 *   - <id> 建议用 ^[a-z0-9][a-z0-9_-]{0,31}$，并为在线/池中 id 提供 Tab 自动补全；
 *   - <玩家> 优先在线玩家补全，离线目标走 UserCache 解析为 UUID（改名不掉授权）；
 *   - 所有文案/描述参数拼接原文后经 TitleDefinition 构造统一校验清洗；
 *   - 命令注册：无 fabric-api 方案 = Mixin 进 CommandDispatcher 构造器（docs/03 §2 H5）。
 */
public final class TitleCommands {
	private TitleCommands() {
	}

	// TODO(M3): public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
}
