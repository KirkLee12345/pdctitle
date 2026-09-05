package net.kirklee.titlemod;

/**
 * 一条称号 = 玩家“拥有”的单个称号。
 *
 * 本模组采用“称号池归属于玩家”模型：
 *   - 一名玩家可拥有 0..N 条称号（各自独立的原始文案）；
 *   - 至多“佩戴”其中 1 条（或 0 条 = 不佩戴，显示原名）；
 *   - 佩戴哪条由玩家自己决定，授予/收回由 OP 决定。
 *
 * display 为带 & 颜色码的原文案（例如 "&b[至尊]"），同名字符串即视为同一称号；
 * 展示用的 Component 在“使用时”才解析（见 TitleService 中的 TODO 注释）。
 */
public record TitleData(String display) {

	/** 限制：单条称号最大长度（含颜色码原文），防刷屏/超长名牌。 */
	public static final int MAX_DISPLAY_LENGTH = 32;

	public TitleData {
		display = sanitize(display);
		if (display.isEmpty()) {
			throw new IllegalArgumentException("称号文本不能为空");
		}
		if (display.length() > MAX_DISPLAY_LENGTH) {
			throw new IllegalArgumentException("称号过长（最多 " + MAX_DISPLAY_LENGTH + " 字符）");
		}
	}

	/**
	 * 剥离换行 / 控制字符（防聊天注入与数据包滥用），保留可读文本。
	 * & 颜色码原样保留，由后续解析阶段统一转换。
	 */
	public static String sanitize(String raw) {
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '\u00a7') {
				// 十六进制颜色段 "§x" 之类：这里只保留段落符本身，解析时再统一处理
				sb.append(c);
			} else if (c >= 0x20) {
				sb.append(c);
			}
			// 0x00-0x1F（换行/Tab 等）一律丢弃
		}
		return sb.toString();
	}
}
