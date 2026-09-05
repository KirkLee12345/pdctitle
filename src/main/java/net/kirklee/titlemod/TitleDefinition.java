package net.kirklee.titlemod;

/**
 * 称号池中的一条“称号定义”（全局唯一，由 OP 通过指令增删改）。
 *
 * 模型 v3（池 + 归属）：
 *   - 称号池：id -> TitleDefinition（display=显示文案，description=描述），单一权威源；
 *   - 玩家拥有：PlayerData.owned 保存的是**池中的 id**，不是文案副本；
 *   - 因而“同一称号发给很多人”只需授权一次池 id；池条目被删除/修改会对所有拥有者即时生效。
 *
 * 校验规则：
 *   - display 可含 & 颜色码，长度上限较短（聊天/名牌长度友好）；
 *   - description 为纯文本（不解析 &），单行、有独立上限，用于聊天悬停说明。
 */
public record TitleDefinition(String display, String description) {

	public static final int MAX_DISPLAY_LENGTH = 32;
	public static final int MAX_DESCRIPTION_LENGTH = 128;

	public TitleDefinition {
		display = sanitize(display, MAX_DISPLAY_LENGTH);
		description = sanitize(description, MAX_DESCRIPTION_LENGTH);
		if (display.isEmpty()) {
			throw new IllegalArgumentException("称号显示文案不能为空");
		}
	}

	/** 通用文本清洗：剥换行/控制字符，超长截断；& 颜色码原样保留（解析放在展示时）。 */
	public static String sanitize(String raw, int maxLength) {
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length() && sb.length() < maxLength; i++) {
			char c = raw.charAt(i);
			if (c >= 0x20 && c != 0x7f) {
				sb.append(c);
			}
			// 0x00-0x1F 与 0x7F（换行/Tab/DEL 等）一律丢弃
		}
		return sb.toString();
	}
}
