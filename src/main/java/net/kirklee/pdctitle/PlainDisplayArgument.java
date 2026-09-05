package net.kirklee.pdctitle;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Arrays;
import java.util.Collection;

/**
 * “显示文案”专用参数：逐字符读到空格为止，不受 Brigadier 默认字符白名单限制，
 * 因此 &、[ ]、中文等均可作为显示文案；文案含空格时可用英文双引号括起。
 * 读完后不消费分隔空格，因此其后仍可用普通空格参数再接可选描述。
 */
public final class PlainDisplayArgument implements ArgumentType<String> {
	private static final Collection<String> EXAMPLES = Arrays.asList("&b[至尊]", "[中文称号]");

	@Override
	public String parse(StringReader reader) throws CommandSyntaxException {
		if (!reader.canRead()) {
			throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
		}
		char first = reader.peek();
		if (first == '"') {
			reader.skip();
			return reader.readStringUntil('"');
		}
		StringBuilder sb = new StringBuilder();
		while (reader.canRead() && !Character.isWhitespace(reader.peek())) {
			sb.append(reader.read());
		}
		if (sb.length() == 0) {
			throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader);
		}
		return sb.toString();
	}

	@Override
	public Collection<String> getExamples() {
		return EXAMPLES;
	}
}
