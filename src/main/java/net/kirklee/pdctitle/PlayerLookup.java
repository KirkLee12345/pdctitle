package net.kirklee.pdctitle;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.StringUtil;

/**
 * 名字 → UUID 解析（供 OP 的 grant/set/revoke/clear/unequip 使用）。
 *
 * 解析顺序：
 *   1. 在线玩家（名字以服务端为准）
 *   2. 模组本地记录（players.json 的 lastKnownName）
 *   3. 离线模式：按名字确定性推导离线 UUID —— 与玩家登录时
 *      UUIDUtil.createOfflineProfile(name) 的结果完全一致（大小写敏感）
 *   4. 在线模式：服务器玩家数据（userCache：进过服/被管理过的名字都有记录，
 *      必要时由验证服务器解析）
 *
 * 这样即使该玩家从未被本模组记录过（甚至从未上线），OP 也能先按名字把称号授予出去，
 * 等他首次进服即自动生效。
 *
 * 注意：离线模式下不能走 userCache —— 它会把未知名字统一小写化再推导 UUID，
 * 与登录时按原始大小写推导的结果不一致，会导致“授权给了不存在的 UUID”。
 */
public final class PlayerLookup {
	/** 解析结果：UUID + 名字 + 来源（用于反馈与日志）。 */
	public record Resolved(UUID uuid, String name, String source) {
	}

	private PlayerLookup() {
	}

	public static Optional<Resolved> byName(MinecraftServer server, TitleStore store, String raw) {
		ServerPlayer online = server.getPlayerList().getPlayer(raw);
		if (online != null) {
			return Optional.of(new Resolved(online.getUUID(), online.getGameProfile().name(), "在线玩家"));
		}
		Optional<UUID> local = store.findUuidByName(raw);
		if (local.isPresent()) {
			return Optional.of(new Resolved(local.get(), raw, "本地记录"));
		}
		if (!server.usesAuthentication()) {
			return StringUtil.isValidPlayerName(raw)
				? Optional.of(new Resolved(NameAndId.createOffline(raw).id(), raw, "离线 UUID"))
				: Optional.empty();
		}
		return lookupServerData(server, raw)
			.map(c -> new Resolved(c.id(), c.name(), "服务器玩家数据"));
	}

	/** 服务器玩家数据缓存；名字不合法或查询异常时返回 empty（不抛给指令层）。 */
	private static Optional<NameAndId> lookupServerData(MinecraftServer server, String raw) {
		if (!StringUtil.isValidPlayerName(raw)) return Optional.empty();
		try {
			return server.services().nameToIdCache().get(raw);
		} catch (Exception ex) {
			PDCTitle.LOGGER.warn("查询服务器玩家数据 {} 失败: {}", raw, ex.toString());
			return Optional.empty();
		}
	}
}
