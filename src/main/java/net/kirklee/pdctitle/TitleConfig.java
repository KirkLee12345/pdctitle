package net.kirklee.pdctitle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 服务器级开关（config/pdctitle/config.json）：
 *   display  —— 全局显示总开关（/plt off 关闭后所有称号不再显示，玩家名回归原版；
 *               称号的创建/授权/回收/佩戴等一切功能照常工作，只是不显示）
 *   chat/tab/nametag —— 三个显示通道，在 display 打开时才各自生效
 * 文件不存在或字段缺失时用默认值。
 */
public final class TitleConfig {
	public static final String FILE_NAME = "config.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** 全局显示总开关：ON 才显示称号（聊天/名牌/Tab 均受它约束）。 */
	public volatile boolean display = true;
	public volatile boolean chat = true;
	// Tab 默认关闭：与服上其它管理 Tab 的模组冲突时让位（需显示时置 true）
	public volatile boolean tab = false;
	public volatile boolean nametag = true;

	public void load(Path dir) {
		Path file = dir.resolve(FILE_NAME);
		if (!Files.isRegularFile(file)) {
			save(dir);
			return;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject o = GSON.fromJson(r, JsonObject.class);
			if (o == null) return;
			display = bool(o, "display", display);
			chat = bool(o, "chat", chat);
			tab = bool(o, "tab", tab);
			nametag = bool(o, "nametag", nametag);
		} catch (Exception ex) {
			PDCTitle.LOGGER.warn("读取 {} 失败，使用默认配置: {}", file, ex.toString());
		}
	}

	public void save(Path dir) {
		try {
			Files.createDirectories(dir);
			JsonObject o = new JsonObject();
			o.addProperty("display", display);
			o.addProperty("chat", chat);
			o.addProperty("tab", tab);
			o.addProperty("nametag", nametag);
			Path file = dir.resolve(FILE_NAME);
			try (var w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
				GSON.toJson(o, w);
			}
		} catch (Exception ex) {
			PDCTitle.LOGGER.warn("写入 {} 失败: {}", dir.resolve(FILE_NAME), ex.toString());
		}
	}

	private static boolean bool(JsonObject o, String key, boolean def) {
		return o.has(key) ? o.get(key).getAsBoolean() : def;
	}
}
