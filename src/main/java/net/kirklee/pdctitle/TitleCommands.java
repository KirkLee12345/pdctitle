package net.kirklee.pdctitle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

/**
 * PDCTitle 指令树：根 /pdctitle（别名 /pdc）。原版已占用 /title，故不使用。
 *
 * ① 池管理（OP）add/edit/desc/remove/list/info/who
 * ② 归属（OP）  grant/set(=授权+佩戴)/revoke/clear
 * ③ 玩家自助    my / wear [id] / wear none / unwear
 * 通用          reload
 */
public final class TitleCommands {
	private static final String TARGET = "target";
	private static final String ID = "id";
	private static final String DISPLAY = "display";
	private static final String DESC = "desc";
	private static final String TEXT = "text";

	private TitleCommands() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		registerRoot(dispatcher, "pdctitle");
		registerRoot(dispatcher, "pdc");
	}

	private static void registerRoot(CommandDispatcher<CommandSourceStack> d, String name) {
		LiteralArgumentBuilder<CommandSourceStack> root = LiteralArgumentBuilder.<CommandSourceStack>literal(name);
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

	private static RequiredArgumentBuilder<CommandSourceStack, String> argTarget() {
		return RequiredArgumentBuilder.<CommandSourceStack, String>argument(TARGET, StringArgumentType.word())
			.suggests(suggestPlayers());
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

	private static SuggestionProvider<CommandSourceStack> suggestPlayers() {
		return (ctx, builder) -> {
			ctx.getSource().getServer().getPlayerList().getPlayers()
				.forEach(p -> builder.suggest(p.getGameProfile().name()));
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
		String target = str(ctx, TARGET);
		String id = str(ctx, ID);
		Optional<UUID> uuid = resolveTarget(server, target);
		if (uuid.isEmpty()) return err(ctx.getSource(), "找不到玩家：" + target);
		if (!PDCTitle.STORE.hasDefinition(id)) return err(ctx.getSource(), "池中不存在称号 id: " + id);
		boolean added = PDCTitle.STORE.grant(uuid.get(), id);
		if (alsoWear) PDCTitle.STORE.wear(uuid.get(), id);
		PDCTitle.STORE.save();
		refreshOnline(server, uuid.get());
		ok(ctx.getSource(), comp("已向 " + target + " ")
			.append(titleChip(id))
			.append(comp(added ? "（新增授权）" : "（已拥有，本次" + (alsoWear ? "设为佩戴" : "无变化") + "）")));
		return 1;
	}

	private static int runRevoke(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		String target = str(ctx, TARGET);
		String id = str(ctx, ID);
		Optional<UUID> uuid = resolveTarget(server, target);
		if (uuid.isEmpty()) return err(ctx.getSource(), "找不到玩家：" + target);
		boolean removed = PDCTitle.STORE.revoke(uuid.get(), id);
		PDCTitle.STORE.save();
		refreshOnline(server, uuid.get());
		ok(ctx.getSource(), removed ? "已收回 " + target + " 的 " + id : target + " 并没有 " + id);
		return 1;
	}

	private static int runClear(CommandContext<CommandSourceStack> ctx) {
		MinecraftServer server = ctx.getSource().getServer();
		String target = str(ctx, TARGET);
		Optional<UUID> uuid = resolveTarget(server, target);
		if (uuid.isEmpty()) return err(ctx.getSource(), "找不到玩家：" + target);
		boolean changed = PDCTitle.STORE.clearAll(uuid.get());
		PDCTitle.STORE.save();
		refreshOnline(server, uuid.get());
		ok(ctx.getSource(), changed ? "已清空 " + target + " 的全部称号" : target + " 本来就没有称号");
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
		ok(ctx.getSource(), comp("已佩戴 ").append(titleChip(id)));
		return 1;
	}

	private static int runWearNone(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer me = ctx.getSource().getPlayer();
		PDCTitle.STORE.unwear(me.getUUID());
		PDCTitle.STORE.save();
		PDCTitle.SERVICE.refreshPlayer(ctx.getSource().getServer(), me);
		ok(ctx.getSource(), "已卸下称号，显示原名");
		return 1;
	}

	private static int runMy(CommandContext<CommandSourceStack> ctx) {
		ServerPlayer me = ctx.getSource().getPlayer();
		MutableComponent out = comp("你拥有的称号：");
		var pd = PDCTitle.STORE.get(me.getUUID());
		if (pd.isEmpty() || pd.get().owned().isEmpty()) {
			out.append(comp("（暂无，等 OP 授予吧）"));
		} else {
			for (String id : pd.get().owned()) {
				boolean worn = pd.get().equipped().map(id::equals).orElse(false);
				out.append(comp("\n  "));
				out.append(titleChip(id));
				if (worn) out.append(comp("（正在佩戴）"));
			}
		}
		ok(ctx.getSource(), out);
		return 1;
	}

	private static int runReload(CommandContext<CommandSourceStack> ctx) {
		PDCTitle.CONFIG.load(PDCTitle.CONFIG_DIR);
		PDCTitle.STORE.load();
		ctx.getSource().getServer().getPlayerList().getPlayers()
			.forEach(p -> PDCTitle.SERVICE.refreshPlayer(ctx.getSource().getServer(), p));
		ok(ctx.getSource(), "已重新加载配置与数据，并刷新在线玩家");
		return 1;
	}

	// ---------------- 工具 ----------------

	private static boolean isOp(CommandSourceStack src) {
		if (!src.isPlayer()) return true; // 控制台视同 OP
		ServerPlayer p = src.getPlayer();
		return src.getServer().getPlayerList().isOp(new NameAndId(p.getGameProfile()));
	}

	private static Optional<UUID> resolveTarget(MinecraftServer server, String raw) {
		try {
			return Optional.of(UUID.fromString(raw));
		} catch (IllegalArgumentException ignore) {
			// 按名字解析
		}
		ServerPlayer online = server.getPlayerList().getPlayer(raw);
		if (online != null) return Optional.of(online.getUUID());
		return PDCTitle.STORE.findUuidByName(raw);
	}

	private static void refreshOnline(MinecraftServer server, UUID uuid) {
		ServerPlayer p = server.getPlayerList().getPlayer(uuid);
		if (p != null) PDCTitle.SERVICE.refreshPlayer(server, p);
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
