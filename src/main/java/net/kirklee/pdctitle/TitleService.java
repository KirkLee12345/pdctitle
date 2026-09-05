package net.kirklee.pdctitle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.scores.PlayerTeam;

/**
 * 称号系统编排层：把“佩戴状态”翻译成三个显示通道的动作。
 *
 *   头顶名牌：专属 Scoreboard Team 的 prefix（原生同步，客户端自动渲染）
 *   Tab：  覆写 ServerPlayer#getTabListDisplayName（mixin 读取本类结果）+ 主动广播 UPDATE_DISPLAY_NAME
 *   聊天：  拦截签名消息并重发系统行，称号块挂 HoverEvent（称号名 + 描述，仿成就提示）
 */
public final class TitleService {
	private static final String TEAM_PREFIX = "pdc_";

	private final TitleStore store;

	public TitleService(TitleStore store) {
		this.store = store;
	}

	// ---------------- 查询 ----------------

	public Optional<TitleDefinition> equipped(UUID uuid) {
		return store.get(uuid)
			.flatMap(pd -> pd.equipped())
			.flatMap(store::definition);
	}

	// ---------------- 生命周期入口 ----------------

	/** 玩家加入/重生后调用：记住名字 + 刷新名牌 + Tab 兜底广播。 */
	public void onPlayerJoin(MinecraftServer server, ServerPlayer p) {
		store.rememberName(p.getUUID(), p.getGameProfile().name());
		refreshPlayer(server, p);
	}

	/** 数据变更后的统一落地（名牌 + Tab；聊天为实时读取无需刷新）。 */
	public void refreshPlayer(MinecraftServer server, ServerPlayer p) {
		applyNameTag(server, p);
		broadcastTab(server, p);
		store.save();
	}

	public void refreshPlayers(MinecraftServer server, List<UUID> uuids) {
		PlayerList list = server.getPlayerList();
		for (UUID uuid : uuids) {
			ServerPlayer p = list.getPlayer(uuid);
			if (p != null) refreshPlayer(server, p);
		}
	}

	// ---------------- 头顶名牌（H1） ----------------

	private void applyNameTag(MinecraftServer server, ServerPlayer p) {
		ServerScoreboard sb = server.getScoreboard();
		String scoreName = p.getScoreboardName();
		String myTeamName = teamName(p);
		PlayerTeam current = sb.getPlayersTeam(scoreName);
		Optional<TitleDefinition> def = equipped(p.getUUID());

		boolean show = PDCTitle.CONFIG.nametag && def.isPresent();
		if (!show) {
			if (current != null && current.getName().equals(myTeamName)) {
				sb.removePlayerFromTeam(scoreName, current);
				dropIfEmpty(sb, current);
			}
			return;
		}
		if (current != null && !current.getName().equals(myTeamName)) {
			PDCTitle.LOGGER.warn("跳过 {} 的头顶名牌：已被其它队伍 {} 占用",
				p.getGameProfile().name(), current.getName());
			return;
		}
		PlayerTeam team = sb.getPlayerTeam(myTeamName);
		if (team == null) {
			team = sb.addPlayerTeam(myTeamName);
		}
		team.setPlayerPrefix(nametagPrefix(def.get()));
		team.setColor(java.util.Optional.empty());
		sb.addPlayerToTeam(scoreName, team);
	}

	private static void dropIfEmpty(ServerScoreboard sb, PlayerTeam team) {
		if (team.getPlayers().isEmpty()) {
			sb.removePlayerTeam(team);
		}
	}

	private static MutableComponent nametagPrefix(TitleDefinition def) {
		MutableComponent c = LegacyText.parse(def.display());
		c.append(" ");
		return c;
	}

	private static String teamName(ServerPlayer p) {
		String hex = p.getUUID().toString().replace("-", "");
		return TEAM_PREFIX + hex.substring(0, Math.min(10, hex.length()));
	}

	// ---------------- Tab（H2） ----------------

	/** Tab 显示名：称号 + 空格 + 玩家名（ServerPlayerTabMixin 使用）。 */
	public Optional<Component> tabDisplayName(ServerPlayer p) {
		if (!PDCTitle.CONFIG.tab) return Optional.empty();
		return equipped(p.getUUID()).map(def -> {
			MutableComponent c = LegacyText.parse(def.display());
			c.append(" ");
			c.append(p.getName());
			return (Component) c;
		});
	}

	/** 变更后向所有在线玩家广播 UPDATE_DISPLAY_NAME。 */
	public void broadcastTab(MinecraftServer server, ServerPlayer p) {
		if (!PDCTitle.CONFIG.tab) return;
		server.getPlayerList().broadcastAll(
			new ClientboundPlayerInfoUpdatePacket(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME, p));
	}

	// ---------------- 聊天（H3） ----------------

	/** 聊天重发的整行（含称号悬停）；未佩戴/通道关闭返回 empty，调用方放行原版消息。 */
	public Optional<Component> chatLine(ServerPlayer sender, String text) {
		if (!PDCTitle.CONFIG.chat) return Optional.empty();
		return equipped(sender.getUUID()).map(def -> {
			MutableComponent line = Component.literal("");
			line.append(chatTitle(def));
			line.append(" ");
			line.append(Component.literal(sender.getGameProfile().name()));
			line.append(": ");
			line.append(Component.literal(text));
			return (Component) line;
		});
	}

	/** 称号块：& 码样式 + 悬停（第一行称号名，第二行灰色斜体描述，仿成就提示）。 */
	public static MutableComponent chatTitle(TitleDefinition def) {
		MutableComponent hover = LegacyText.parse(def.display()).copy();
		if (!def.description().isEmpty()) {
			hover.append(Component.literal("\n" + def.description())
				.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		MutableComponent title = LegacyText.parse(def.display());
		title.withStyle(s -> s.withHoverEvent(new HoverEvent.ShowText(hover)));
		return title;
	}
}
