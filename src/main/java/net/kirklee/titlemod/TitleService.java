package net.kirklee.titlemod;

import java.util.UUID;

/**
 * 称号系统唯一操作入口（“服务层”）：把数据变更翻译成三个显示通道的刷新动作。
 *
 * 三个显示通道各自独立（详见 docs/01-需求与设计方案.md §3）：
 *   1) 头顶名牌  -> Scoreboard PlayerTeam 的 prefix（原生机制，客户端自动渲染）
 *   2) Tab 列表  -> 覆写玩家“列表显示名” + 主动广播 UPDATE_DISPLAY_NAME 数据包
 *   3) 聊天栏    -> 1.19.1+ 聊天签名不可改写显示名，须“拦截原消息→按自定义格式重发”
 *
 * 调用约定：
 *   - 数据变更（grant/revoke/wear/unwear/clear）后调用本类的刷新方法；
 *   - 聊天无需预刷新：玩家发言时由聊天 Mixin 实时查 TitleStore 组装显示行。
 */
public final class TitleService {
	private final TitleStore store;

	public TitleService(TitleStore store) {
		this.store = store;
	}

	/**
	 * 玩家登录/重生/称号变更后的统一“落地”：确保该玩家当前三处显示与数据一致。
	 * TODO(M2)：需要拿到 ServerPlayer 后实现下列三个动作：
	 *   1. applyNametag(player)：把 player 放进专属队伍（队名 titlemod_ + uuid 片段），
	 *      用“佩戴称号”设置 Team#setPrefix；无佩戴则从队伍移除。
	 *      注意：26.2 队伍 API（ServerScoreboard/PlayerTeam）方法名需在 M0 用
	 *      mcsrc.dev 反编译源核对。若玩家已被其它玩法占用队伍，需合并或告警（docs/01 §7）。
	 *   2. refreshTab(player)：向所有在线玩家广播 ClientboundPlayerInfoUpdatePacket
	 *      (Action.UPDATE_DISPLAY_NAME, 该玩家条目)，显示名 = 佩戴称号 + 名字；
	 *      同时 Mixin ServerPlayer#getTabListDisplayName 让“新进服玩家首屏”自动正确。
	 *   3. 三通道全局开关（chat/tab/nametag）读取 TitleConfig。
	 */
	public void refreshPlayer(UUID uuid) {
		// TODO(M2): 由调用方传入在线 ServerPlayer（这里仅做数据层落盘）
		store.save();
	}

	/** 供聊天 Mixin 查询：某玩家此刻佩戴的称号（无则 Optional.empty）。 */
	public TitleData equippedOf(UUID uuid) {
		return store.get(uuid).flatMap(pd -> pd.equipped()).orElse(null);
	}

	/** 聊天行组装：<佩戴称号> 玩家名: 内容。TODO(M2) 具体样式策略见 docs/01 §5.2。 */
	// TODO(M2): buildChatLine(ServerPlayer sender, String plainText) -> Component
}
