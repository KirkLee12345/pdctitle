package net.kirklee.pdctitle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 称号系统唯一操作入口（“服务层”）：把数据变更翻译成三个显示通道的刷新动作。
 *
 * 显示通道（各自独立，详见 docs/01 §5）：
 *   1) 头顶名牌  -> Scoreboard PlayerTeam 的 prefix（原生机制，客户端自动渲染）
 *   2) Tab 列表  -> 覆写 ServerPlayer#getTabListDisplayName + 广播 UPDATE_DISPLAY_NAME
 *   3) 聊天栏    -> 1.19.1+ 签名不可改写显示名：拦截原消息→按自定义格式重发；
 *                   其中“称号”文本块带 HoverEvent：鼠标悬停显示 称号名+描述（类似成就/进度提示样式）
 *
 * 调用约定：
 *   - 数据变更（grant/revoke/wear/unwear/clear/池条目增删改）→ 调用 refreshXxx 刷新相关在线玩家；
 *   - 聊天不预刷新：玩家发言时由聊天 Mixin 实时读取佩戴的称号并组装显示行。
 */
public final class TitleService {
	private final TitleStore store;

	public TitleService(TitleStore store) {
		this.store = store;
	}

	/** 佩戴中的称号定义（无则 empty）。 */
	public Optional<TitleDefinition> equippedDefinition(UUID uuid) {
		Optional<PlayerData> pd = store.get(uuid);
		if (pd.isEmpty()) return Optional.empty();
		return pd.get().equipped().flatMap(store::definition);
	}

	/** 玩家上线/重生/数据变更后的统一落地。TODO(M2)：见 docs/01 §5 与 docs/03 H1/H2。 */
	public void refreshPlayer(UUID uuid) {
		// TODO(M2): 拿到在线 ServerPlayer 后执行
		//   applyNametag(player)：佩戴→进专属队伍并 setPlayerPrefix(解析后的 display)；
		//                         卸下/无授权→移出队伍。
		//   refreshTab(player)：广播 ClientboundPlayerInfoUpdatePacket(UPDATE_DISPLAY_NAME,...)。
		store.save();
	}

	/** 级联刷新：池条目删除/变更影响到的玩家。TODO(M2) 由 TitleCommands 调 store 后调用。 */
	public void refreshPlayers(List<UUID> affected) {
		affected.forEach(this::refreshPlayer);
	}

	/**
	 * 聊天行“发送者”组件（聊天 Mixin 在拦截重发时调用）。
	 * TODO(M2) 结构：
	 *   1) title 块：解析 display 的 & 颜色码 -> Component，并 setStyle(hoverEvent=SHOW_TEXT，
	 *      内容 = 称号名行 + "\n" + 描述行(灰色斜体)，样式参考成就/进度悬停提示)；
	 *   2) 追加空格与玩家名文本块；
	 *   3) 无佩戴称号 -> 返回空 Optional，调用方放行原版签名聊天（零影响）。
	 */
	// TODO(M2): Optional<Component> buildSenderComponent(ServerPlayer sender)
}
