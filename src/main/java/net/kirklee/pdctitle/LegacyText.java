package net.kirklee.pdctitle;

import java.util.function.UnaryOperator;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * 轻量 “& 颜色码” → 文本组件 解析器（服务端，无客户端依赖）。
 * 支持 &0-9、&a-f、&k-o、&r；不支持旧式 “§x” 十六进制序列（可后续扩展）。
 *
 * 26.2 的 ChatFormatting 仅为纯枚举常量，颜色/样式判定直接按常量 switch。
 */
public final class LegacyText {
	private LegacyText() {
	}

	public static MutableComponent parse(String raw) {
		MutableComponent out = Component.literal("");
		StringBuilder buf = new StringBuilder(raw.length());
		Style style = Style.EMPTY;
		for (int i = 0; i < raw.length(); i++) {
			char c = raw.charAt(i);
			if (c == '&' && i + 1 < raw.length()) {
				ChatFormatting fmt = ChatFormatting.getByCode(raw.charAt(i + 1));
				if (fmt != null) {
					flush(out, buf, style);
					style = apply(style, fmt);
					i++;
					continue;
				}
			}
			buf.append(c);
		}
		flush(out, buf, style);
		return out;
	}

	private static void flush(MutableComponent out, StringBuilder buf, Style style) {
		if (buf.length() > 0) {
			out.append(Component.literal(buf.toString()).withStyle(style));
			buf.setLength(0);
		}
	}

	/**
	 * 把函数 f 施加到容器的每个可见子 run（悬停/点击等须挂在真正渲染文字的 run 上才生效）。
	 */
	public static MutableComponent mapRuns(MutableComponent source, UnaryOperator<Style> fn) {
		MutableComponent out = Component.literal("");
		for (Component run : source.getSiblings()) {
			out.append(run.copy().withStyle(fn));
		}
		return out;
	}

	private static Style apply(Style style, ChatFormatting fmt) {
		return switch (fmt) {
			// 颜色
			case BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD,
				GRAY, DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE ->
				style.withColor(fmt);
			// 重置
			case RESET -> Style.EMPTY;
			// 样式
			case BOLD -> style.withBold(true);
			case ITALIC -> style.withItalic(true);
			case UNDERLINE -> style.withUnderlined(true);
			case STRIKETHROUGH -> style.withStrikethrough(true);
			case OBFUSCATED -> style.withObfuscated(true);
		};
	}
}
