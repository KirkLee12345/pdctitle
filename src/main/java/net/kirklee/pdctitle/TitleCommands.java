package net.kirklee.pdctitle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/**
 * PDCTitle 指令树：根 /pdctitle（简写 /plt）。原版已占用 /title，故不使用。
 *
 * ① 池管理（OP）add/edit/desc/remove/list/info/who
 * ② 归属（OP）  grant/set(=授权+佩戴)/revoke/clear/unequip(=只卸下佩戴，保留授权)
 * ③ 玩家自助    my（/plt 单独输入等同 my） / wear [id] / wear none / unwear
 * 通用          on/off(全局显示开关) / reload
 *
 * 所有带“玩家”参数的指令统一走 argTarget()：支持 @a/@p/@r/@s/@e[type=player,...]
 * 与玩家名（在线、离线均可，见 PlayerLookup）。
 */
public final class TitleCommands {
	private static final String TARGET = "target";
	private static final String ID = "id";
	private static final String DISPLAY = "display";
	private static final String DESC = "desc";
	private static final String TEXT = "text";
	private static final String DISPLAY_OFF_HINT =
		"（注意：管理员已用 /plt off 关闭称号显示，你的称号暂时不会显示，佩戴状态不受影响）";

	private TitleCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		registerRoot(dispatcher, "pdctitle");
		registerRoot(dispatcher, "plt");
	}

	private static void registerRoot(CommandDispatcher<CommandSourceStack> d, String name) {
		LiteralArgumentBuilder<CommandSourceStack> root = LiteralArgumentBuilder.<CommandSourceStack>literal(name);
		// 只输入 /plt（或 /pdctitle）= /plt my
		root.executes(TitleCommands::runRoot);

		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("add")
			.requires(TitleCommands::isOp)
			.then(argId().then(argDisplay()
				.executes(TitleCommands::runAdd)
				.then(argDesc().executes(TitleCommands::runAddWithDesc)))));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("edit")
			.requires(TitleCommands::isOp)
			.then(argId().then(argText().executes(TitleCommands::runEdit))));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("desc")
			.requires(TitleCommands::isOp)
			.then(argId().then(argText().executes(TitleCommands::runDesc))));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("remove")
			.requires(TitleCommands::isOp)
			.then(argId().executes(TitleCommands::runRemove)));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("list")
			.requires(TitleCommands::isOp)
			.executes(TitleCommands::runList));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("info")
			.requires(TitleCommands::isOp)
			.then(argId().executes(TitleCommands::runInfo)));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("who")
			.requires(TitleCommands::isOp)
			.then(argId().executes(TitleCommands::runWho)));

		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("grant")
			.requires(TitleCommands::isOp)
			.then(argTarget().then(argId().executes(ctx -> runGrant(ctx, false)))));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("set")
			.requires(TitleCommands::isOp)
			.then(argTarget().then(argId().executes(ctx -> runGrant(ctx, true)))));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("revoke")
			.requires(TitleCommands::isOp)
			.then(argTarget().then(argId().executes(TitleCommands::runRevoke))));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("clear")
			.requires(TitleCommands::isOp)
			.then(argTarget().executes(TitleCommands::runClear)));
		// 只卸下“佩戴”，保留授权（与 revoke/clear 区分）
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("unequip")
			.requires(TitleCommands::isOp)
			.then(argTarget().executes(TitleCommands::runUnequip)));

		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("my")
			.requires(CommandSourceStack::isPlayer)
			.executes(TitleCommands::runMy));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("wear")
			.requires(CommandSourceStack::isPlayer)
			.then(LiteralArgumentBuilder.<CommandSourceStack>literal("none").executes(TitleCommands::runWearNone))
			.then(argId().suggests(TitleCommands.suggestOwned()).executes(TitleCommands::runWear)));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("unwear")
			.requires(CommandSourceStack::isPlayer)
			.executes(TitleCommands::runWearNone));

		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("on")
			.requires(TitleCommands::isOp)
			.executes(ctx -> runDisplay(ctx, true)));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("off")
			.requires(TitleCommands::isOp)
			.executes(ctx -> runDisplay(ctx, false)));
		root.then(LiteralArgumentBuilder.<CommandSourceStack>literal("reload")
			.requires(TitleCommands::isOp)
			.executes(TitleCommands::runReload));
		d.register(root);
	}

	// ---------------- 参数构建 ----------------

	private static RequiredArgumentBuilder<CommandSourceStack, String> argId() {
		return RequiredArgumentBuilder.<CommandSourceStack, String>argument(ID, StringArgumentType.word())
			.suggests(suggestIds());
	}

	/** 目标参数：原版多目标选择器参数，支持 @a/@p/@r/@s/@e[type=player] 与玩家名（在线/离线）。 */
	private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> argTarget() {
		return RequiredArgumentBuilder.<CommandSourceStack, EntitySelector>argument(TARGET, EntityArgument.players());
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> argText() {
		return RequiredArgumentBuilder.<CommandSourceStack, String>argument(TEXT, StringArgumentType.greedyString());
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> argDisplay() {
		// string()：特殊字符（&/[]/中文）需用英文双引号包裹即可绕过字符白名单；空格也在引号内
		return RequiredArgumentBuilder.<CommandSourceStack, String>argument(DISPLAY, StringArgumentType.string());
	}

	private static RequiredArgumentBuilder<CommandSourceStack, String> argDesc() {
		return RequiredArgumentBuilder.<CommandSourceStack, String>argument(DESC, StringArgumentType.greedyString());
	}

	private static SuggestionProvider<CommandSourceStack> suggestIds() {
		return (ctx, builder) -> {
			PDCTitle.STORE.definitionsSnapshot().keySet().forEach(builder::suggest);
			return builder.buildFuture();
		};
	}

	private static SuggestionProvider<CommandSourceStack> suggestOwned() {
		return (ctx, builder) -> {
			ServerPlayer p = ctx.getSource().getPlayer();
			if (p != null) {
				PDCTitle.STORE.get(p.getUUID()).ifPresent(pd -> pd.owned().forEach(builder::suggest));
			}
			return builder.buildFuture();
		};
	}

	// ---------------- 根指令 ----------------

	/** /plt（无参数）：玩家等同 /plt my；控制台打印指令索引。 */
	private static int runRoot(CommandContext<CommandSourceStack> ctx) {
		if (ctx.getSource().isPlayer()) return runMy(ctx);
		ok(ctx.getSource(), comp("PDCTitle 指令：/pdctitle <add|edit|desc|remove|list|info|who|"
			+ "grant|set|revoke|clear|unequip|my|wear|unwear|on|off|reload>（简写 /plt）"
			+ "；玩家目标支持 @a/@p/@r/@s/@e[type=player,...] 与玩家名"));
		return 1;
	}

	/** /plt on|off：全局显示总开关（只影响显示，不影响任何数据与功能）。 */
	private static int runDisplay(CommandContext<CommandSourceStack> ctx, boolean on) {
		PDCTitle.CONFIG.display = on;
		PDCTitle.CONFIG.save(PDCTitle.CONFIG_DIR);
		PDCTitle.SERVICE.refreshAll(ctx.getSource().getServer());
		PDCTitle.LOGGER.info("全局称号显示被 {} 设置为 {}", ctx.getSource().getTextName(), on ? "开启" : "关闭");
		ok(ctx.getSource(), comp(on
			? "已开启称号显示：佩戴中的称号恢复显示在聊天栏 / 头顶 / Tab"
			: "已关闭称号显示：所有称号不再显示，玩家名回归原版（可用于活动临时占用名字）；"
				+ "称号的创建/修改/授权/佩戴/查询功能不受影响，/plt on 恢复显示"));
		return 1;
	}

	// ---------------- ① 池管理执行 ----------------

	private static int runAdd(CommandContext<CommandSourceStack> ctx) {
		return doAdd(ctx, "");
	}

	private static int runAddWithDesc(CommandContext<CommandSourceStack> ctx) {
		return doAdd(ctx, str(ctx, DESC));
	}

	private static int doAdd(CommandContext<CommandSourceStack> ctx, String desc) {
		String id = str(ctx, ID);
		String display = str(ctx, DISPLAY);
		if (!id.matches("[a-z0-9][a-z0-9_-]{0,31}")) {
			return err(ctx.getSource(), "id 不合法：仅小写字母/数字/_-，1-32 字符");
		}
		try {
			TitleDefinition def = new TitleDefinition(display, desc);
			PDCTitle.STORE.putDefinition(id, def);
			PDCTitle.STORE.save();
			MutableComponent fb = comp("已新增称号 ").append(titleChip(id))
				.append(comp("（id=" + id));
			fb.append(desc.isEmpty()
				? comp("；描述可用 desc 或 add 时末尾补上）")
				: comp("；描述：").append(TitleService.descriptionText(def.description())).append(comp("）")));
			ok(ctx.getSource(), fb);
		} catch (IllegalArgumentException ex) {
			return err(ctx.getSource(), ex.getMessage());
		}
		return 1;
	}

	private static int runEdit(CommandContext<CommandSourceStack> ctx) {
		String id = str(ctx, ID);
		Optional<TitleDefinition> old = PDCTitle.STORE.definition(id);
		if (old.isEmpty()) return err(ctx.getSource(), "池中不存在称号 id: " + id);
		try {
			PDCTitle.STORE.putDefinition(id, new TitleDefinition(str(ctx, TEXT), old.get().description()));
			PDCTitle.STORE.save();
			ok(ctx.getSource(), comp("已更新 " + id + " 的显示：").append(titleChip(id)));
		} catch (IllegalArgumentException ex) {
			return err(ctx.getSource(), ex.getMessage());
		}
		return 1;
	}

	private static int runDesc(CommandContext<CommandSourceStack> ctx) {
		String id = str(ctx, ID);
		Optional<TitleDefinition> old = PDCTitle.STORE.definition(id);
		if (old.isEmpty()) return err(ctx.getSource(), "池中不存在称号 id: " + id);
		String raw = str(ctx, TEXT);
		String desc = "none".equalsIgnoreCase(raw) ? "" : TitleDefinition.sanitize(raw, TitleDefinition.MAX_DESCRIPTION_LENGTH);
		PDCTitle.STORE.putDefinition(id, new TitleDefinition(old.get().display(), desc));
		PDCTitle.STORE.save();
		ok(ctx.getSource(), desc.isEmpty() ? "已清空 " + id + " 的描述" : "已设置 " + id + " 的描述");
		return 1;
	}

	private static int runRemove(CommandContext<CommandSourceStack> ctx) {
		String id = str(ctx, ID);
		if (!PDCTitle.STORE.hasDefinition(id)) return err(ctx.getSource(), "池中不存在称号 id: " + id);
		List<UUID> affected = PDCTitle.STORE.removeDefinition(id);
		PDCTitle.STORE.save();
		PDCTitle.SERVICE.refreshPlayers(ctx.getSource().getServer(), affected);
		ok(ctx.getSource(), "已删除称号 " + id + "，并清理 " + affected.size() + " 名玩家的该称号");
		return 1;
	}

	private static int runList(CommandContext<CommandSourceStack> ctx) {
		Map<String, TitleDefinition> defs = PDCTitle.STORE.definitionsSnapshot();
		if (defs.isEmpty()) {
			ok(ctx.getSource(), "称号池为空，用 /pdctitle add <id> <文案> 添加");
			return 1;
		}
		MutableComponent out = comp("称号池共 " + defs.size() + " 条：\n");
		for (Map.Entry<String, TitleDefinition> e : defs.entrySet()) {
			out.append(comp("  " + e.getKey() + " "));
			out.append(titleChip(e.getKey()));
			out.append(comp("（" + PDCTitle.STORE.ownerCount(e.getKey()) + " 人拥有）\n"));
		}
		ok(ctx.getSource(), out);
		return 1;
	}

	private static int runInfo(CommandContext<CommandSourceStack> ctx) {
		String id = str(ctx, ID);
		Optional<TitleDefinition> def = PDCTitle.STORE.definition(id);
		if (def.isEmpty()) return err(ctx.getSource(), "池中不存在称号 id: " + id);
		MutableComponent out = comp("称号 " + id + "：");
		out.append(titleChip(id));
		out.append(comp("\n描述："));
		out.append(def.get().description().isEmpty()
			? comp("（无）")
			: TitleService.descriptionText(def.get().description()));
		out.append(comp("\n拥有者：" + PDCTitle.STORE.ownerCount(id) + " 人"));
		ok(ctx.getSource(), out);
		return 1;
	}

	private static int runWho(CommandContext<CommandSourceStack> ctx) {
		String id = str(ctx, ID);
		if (PDCTitle.STORE.definition(id).isEmpty()) return err(ctx.getSource(), "池中不存在称号 id: " + id);
		List<String> names = PDCTitle.STORE.ownerNames(id);
		ok(ctx.getSource(), comp("拥有 " + id + "：" + (names.isEmpty() ? "（暂无）" : String.join("、", names))));
		return 1;
	}

	// ---------------- ② 归属执行 ----------------

	private static int runGrant(CommandContext<CommandSourceStack> ctx, boolean alsoWear) {
		MinecraftServer server = ctx.getSource().getServer();
		String id = str(ctx, ID);
		if (!PDCTitle.STORE.hasDefinition(id)) return err(ctx.getSource(), "池中不存在称号 id: " + id);
		List<PlayerLookup.Resolved> targets = resolveTargets(ctx);
		if (targets.isEmpty()) return err(ctx.getSource(), noTargetMsg(ctx));
		int added = 0;
		for (PlayerLookup.Resolved t : targets) {
			PDCTitle.STORE.rememberName(t.uuid(), t.name()); // 离线/新玩家也留下名字，who 里可读
			if (PDCTitle.STORE.grant(t.uuid(), id)) added++;
			if (alsoWear) PDCTitle.STORE.wear(t.uuid(), id);
			refreshOnline(server, t.uuid());
		}
		PDCTitle.STORE.save();
		MutableComponent fb = comp("已处理 " + targets.size() + " 名玩家 ")
			.append(titleChip(id))
			.append(comp(alsoWear
				? "（新增授权 " + added + " 人，并设为佩戴）"
				: "（新增授权 " + added + " 人，其余已拥有）"));
		ok(ctx.getSource(), fb);
		return 1;
	}

	private static int runRevoke(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		String id = str(ctx, ID);
		List<PlayerLookup.Resolved> targets = resolveTargets(ctx);
		if (targets.isEmpty()) return err(ctx.getSource(), noTargetMsg(ctx));
		int removed = 0;
		for (PlayerLookup.Resolved t : targets) {
			if (PDCTitle.STORE.revoke(t.uuid(), id)) removed++;
			refreshOnline(server, t.uuid());
		}
		PDCTitle.STORE.save();
		ok(ctx.getSource(), "已在 " + targets.size() + " 名玩家中收回 " + removed + " 个 " + id + "（佩戴中的自动卸下）");
		return 1;
	}

	private static int runClear(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		List<PlayerLookup.Resolved> targets = resolveTargets(ctx);
		if (targets.isEmpty()) return err(ctx.getSource(), noTargetMsg(ctx));
		int changed = 0;
		for (PlayerLookup.Resolved t : targets) {
			if (PDCTitle.STORE.clearAll(t.uuid())) changed++;
			refreshOnline(server, t.uuid());
		}
		PDCTitle.STORE.save();
		ok(ctx.getSource(), "已处理 " + targets.size() + " 名玩家，其中 " + changed + " 人的称号被清空");
		return 1;
	}

	/** 只卸下佩戴（保留授权）：不删定义、不收回授权。 */
	private static int runUnequip(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		List<PlayerLookup.Resolved> targets = resolveTargets(ctx);
		if (targets.isEmpty()) return err(ctx.getSource(), noTargetMsg(ctx));
		List<String> names = new ArrayList<>();
		for (PlayerLookup.Resolved t : targets) {
			if (PDCTitle.STORE.unwear(t.uuid())) names.add(t.name());
			refreshOnline(server, t.uuid());
		}
		PDCTitle.STORE.save();
		MutableComponent fb = comp("已卸下 " + names.size() + " 名玩家佩戴中的称号（授权保留，可用 /pdctitle set 重新戴上）");
		if (!names.isEmpty()) fb.append(comp("：" + String.join("、", names)));
		ok(ctx.getSource(), fb);
		return 1;
	}

	// ---------------- ③ 玩家自助执行 ----------------

	private static int runWear(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer me = ctx.getSource().getPlayer();
		String id = str(ctx, ID);
		if (!PDCTitle.STORE.wear(me.getUUID(), id)) {
			return err(ctx.getSource(), "你尚未拥有 " + id + "（/pdctitle my 查看）");
		}
		PDCTitle.STORE.save();
		PDCTitle.SERVICE.refreshPlayer(ctx.getSource().getServer(), me);
		MutableComponent fb = comp("已佩戴 ").append(titleChip(id));
		appendDisplayHint(fb);
		ok(ctx.getSource(), fb);
		return 1;
	}

	private static int runWearNone(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer me = ctx.getSource().getPlayer();
		PDCTitle.STORE.unwear(me.getUUID());
		PDCTitle.STORE.save();
		PDCTitle.SERVICE.refreshPlayer(ctx.getSource().getServer(), me);
		MutableComponent fb = comp("已卸下称号，显示原名");
		appendDisplayHint(fb);
		ok(ctx.getSource(), fb);
		return 1;
	}

	private static int runMy(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer me = ctx.getSource().getPlayer();
		MutableComponent out = comp("点击下面的称号即可佩戴/切换，点击末尾按钮卸下：");
		var pd = PDCTitle.STORE.get(me.getUUID());
		if (pd.isEmpty() || pd.get().owned().isEmpty()) {
			out.append(comp("\n（你还没有任何称号，等 OP 授予吧）"));
		} else {
			for (String id : pd.get().owned()) {
				boolean worn = pd.get().equipped().map(id::equals).orElse(false);
				out.append(comp("\n  "));
				TitleDefinition def = PDCTitle.STORE.definition(id).orElse(null);
				out.append(def != null ? TitleService.wearChip(def, id) : comp(id));
				if (worn) out.append(comp("（正在佩戴）"));
			}
		}
		out.append(comp("\n\n"));
		out.append(unwearButton());
		appendDisplayHint(out);
		ok(ctx.getSource(), out);
		return 1;
	}

	/** [不佩戴称号] 按钮：点击执行 /pdctitle unwear。 */
	private static MutableComponent unwearButton() {
		return Component.literal("[不佩戴称号]")
			.withStyle(s -> s.withColor(ChatFormatting.GRAY)
				.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/pdctitle unwear")));
	}

	private static int runReload(CommandContext<CommandSourceStack> ctx) {
		PDCTitle.CONFIG.load(PDCTitle.CONFIG_DIR);
		PDCTitle.STORE.load();
		PDCTitle.SERVICE.refreshAll(ctx.getSource().getServer());
		ok(ctx.getSource(), "已重新加载配置与数据，并刷新在线玩家（当前显示："
			+ (PDCTitle.CONFIG.display ? "开" : "关") + "）");
		return 1;
	}

	// ---------------- 工具 ----------------

	private static boolean isOp(CommandSourceStack src) {
		if (!src.isPlayer()) return true; // 控制台视同 OP
		ServerPlayer p = src.getPlayer();
		return src.getServer().getPlayerList().isOp(new NameAndId(p.getGameProfile()));
	}

	/**
	 * 解析目标：支持原版选择器（@a/@p/@r/@s/@e[type=player]）与玩家名；
	 * 名字在离线、甚至本模组从未记录过时，回退到服务器玩家数据 / 离线 UUID（见 PlayerLookup）。
	 */
	private static List<PlayerLookup.Resolved> resolveTargets(CommandContext<CommandSourceStack> ctx) {
		String raw = rawArg(ctx, TARGET);
		if (raw.startsWith("@")) {
			try {
				List<PlayerLookup.Resolved> out = new ArrayList<>();
				for (ServerPlayer p : EntityArgument.getPlayers(ctx, TARGET)) {
					out.add(new PlayerLookup.Resolved(p.getUUID(), p.getGameProfile().name(), "选择器"));
				}
				return out;
			} catch (CommandSyntaxException ex) {
				return List.of(); // 选择器无匹配/不合法
			}
		}
		return PlayerLookup.byName(ctx.getSource().getServer(), PDCTitle.STORE, raw)
			.map(List::of)
			.orElseGet(List::of);
	}

	/** 取目标参数的原始输入文本（用于选择器/名字判定与报错回显）。 */
	private static String rawArg(CommandContext<CommandSourceStack> ctx, String name) {
		var nodes = ctx.getNodes();
		for (int i = nodes.size() - 1; i >= 0; i--) {
			var node = nodes.get(i);
			if (node.getNode().getName().equals(name)) {
				var range = node.getRange();
				return ctx.getInput().substring(range.getStart(), range.getEnd());
			}
		}
		return "";
	}

	private static String noTargetMsg(CommandContext<CommandSourceStack> ctx) {
		return "没有匹配到玩家（选择器无结果，或名字无法解析）：" + rawArg(ctx, TARGET)
			+ "；可用 @a / @p / @r / @s / @e[type=player] / 玩家名（在线或进过服的）";
	}

	private static void refreshOnline(MinecraftServer server, UUID uuid) {
		ServerPlayer p = server.getPlayerList().getPlayer(uuid);
		if (p != null) PDCTitle.SERVICE.refreshPlayer(server, p);
	}

	/** 全局显示关闭时，在佩戴相关反馈后补一句提示（功能照常，只是不显示）。 */
	private static void appendDisplayHint(MutableComponent out) {
		if (!TitleService.displayEnabled()) out.append(comp("\n" + DISPLAY_OFF_HINT));
	}

	private static String str(CommandContext<CommandSourceStack> ctx, String name) {
		return StringArgumentType.getString(ctx, name);
	}

	/** 带悬停描述的称号块（指令反馈里所有称号都可悬停看描述）。 */
	private static MutableComponent titleChip(String id) {
		TitleDefinition def = PDCTitle.STORE.definition(id).orElse(null);
		return def == null ? comp(id) : TitleService.chatTitle(def);
	}

	private static MutableComponent comp(String s) {
		return Component.literal(s);
	}

	private static void ok(CommandSourceStack src, String msg) {
		src.sendSuccess(() -> Component.literal(msg), false);
	}

	private static void ok(CommandSourceStack src, Component msg) {
		src.sendSuccess(() -> msg, false);
	}

	private static int err(CommandSourceStack src, String msg) {
		src.sendFailure(Component.literal(msg));
		return 0;
	}
}
