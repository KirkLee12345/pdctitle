package net.kirklee.titlemod;

/**
 * Brigadier 指令树定义（骨架，实现见 M3 里程碑）。
 *
 * 权限模型：
 *   - 授予 / 收回 / 清空 / 查看他人  -> 需要 OP（hasPermissionLevel(2)）；
 *   - 佩戴 / 卸下 / 查看自己        -> 任何玩家可用（操作范围仅限自己拥有的称号）。
 *
 * 指令一览（详细语法与示例见 docs/02-指令与配置参考.md）：
 *
 *   /title grant  <玩家> <称号...>       OP：授予一条称号（重复授予会提示“已拥有”）
 *   /title set    <玩家> <称号...>       OP：授予 + 立即佩戴（等价于旧版“设置称号”）
 *   /title revoke <玩家> <称号...>       OP：收回（若正佩戴则自动卸下）
 *   /title clear  <玩家>                 OP：清空该玩家全部称号并卸下
 *   /title list   [玩家]                 OP：查看某人（或全员）称号与佩戴情况
 *
 *   /title wear   <称号...>              玩家：佩戴自己拥有的称号
 *   /title wear   none                   玩家：卸下（不佩戴任何称号，显示原名）
 *   /title unwear                        玩家：卸下的另一种写法
 *   /title my                            玩家：查看自己拥有的称号与当前佩戴
 *
 * 实现要点：
 *   - 玩家参数：优先在线玩家自动补全；离线目标走服务端 UserCache 解析成 UUID（改名不掉称号）；
 *   - “称号”参数取剩余参数拼接的原始字符串（支持空格与 & 颜色码），经 TitleData 构造校验；
 *   - 命令注册入口在无 fabric-api 方案下 = Mixin 进 CommandDispatcher 构造器
 *     （M0 确认 26.2 目标类与方法，见 docs/03-实现要点与M0验证清单.md §4.4）。
 */
public final class TitleCommands {
	private TitleCommands() {
	}

	// TODO(M3): public static void register(CommandDispatcher<CommandSourceStack> dispatcher)
	//   build 根指令 "title"（及别名 "称号"/"t" 视测试情况而定）。
}
