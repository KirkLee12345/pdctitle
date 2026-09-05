package net.kirklee.titlemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 称号持久化（服务端配置区：config/titlemod/titles.json）。
 *
 * 文件结构（v2，多称号模型）：
 * {
 *   "<uuid>": { "owned": ["&b[至尊]", "&a[创世元老]"], "equipped": "&a[创世元老]" },
 *   ...
 * }
 *  - equipped 为 owned 中的某条原文案，或缺失/空 = 不佩戴。
 *
 * 线程模型：单例 + synchronized，所有读写走本类；变更后立即 save()。
 * 写盘采用“临时文件 + 原子移动”，避免服务器崩溃导致 JSON 损坏。
 */
public final class TitleStore {
	public static final String FILE_NAME = "titles.json";

	private final Path file;
	private final Map<UUID, PlayerData> byPlayer = new HashMap<>();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public TitleStore(Path dir) {
		this.file = dir.resolve(FILE_NAME);
	}

	public synchronized Optional<PlayerData> get(UUID uuid) {
		return Optional.ofNullable(byPlayer.get(uuid));
	}

	public synchronized PlayerData getOrCreate(UUID uuid) {
		return byPlayer.computeIfAbsent(uuid, u -> new PlayerData());
	}

	public synchronized Map<UUID, PlayerData> snapshot() {
		return Map.copyOf(byPlayer);
	}

	// ---------- 持久化 ----------

	public synchronized void load() {
		byPlayer.clear();
		if (!Files.isRegularFile(file)) return;
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
			JsonObject root = gson.fromJson(r, JsonObject.class);
			if (root == null) return;
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				try {
					UUID uuid = UUID.fromString(e.getKey());
					PlayerData pd = parsePlayer(e.getValue().getAsJsonObject());
					byPlayer.put(uuid, pd);
				} catch (Exception ex) {
					TitleMod.LOGGER.warn("titles.json 中存在无法解析的条目，已跳过: {}", e.getKey());
				}
			}
		} catch (IOException | RuntimeException ex) {
			TitleMod.LOGGER.warn("读取 {} 失败: {}", file, ex.toString());
		}
	}

	private static PlayerData parsePlayer(JsonObject o) {
		PlayerData pd = new PlayerData();
		JsonArray owned = o.has("owned") ? o.getAsJsonArray("owned") : new JsonArray();
		for (JsonElement el : owned) {
			String s = el.getAsString();
			try {
				pd.grant(new TitleData(s));
			} catch (IllegalArgumentException ignore) {
				// 空/超长等非法条目跳过
			}
		}
		if (o.has("equipped") && !o.get("equipped").isJsonNull()) {
			pd.wear(o.get("equipped").getAsString());
		}
		return pd;
	}

	public synchronized void save() {
		try {
			Files.createDirectories(file.getParent());
			JsonObject root = new JsonObject();
			for (Map.Entry<UUID, PlayerData> e : byPlayer.entrySet()) {
				JsonObject o = new JsonObject();
				JsonArray owned = new JsonArray();
				for (TitleData t : e.getValue().owned()) owned.add(t.display());
				o.add("owned", owned);
				e.getValue().equipped().ifPresent(t -> o.addProperty("equipped", t.display()));
				root.add(e.getKey().toString(), o);
			}
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				gson.toJson(root, w);
			}
			Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			TitleMod.LOGGER.warn("写入 {} 失败: {}", file, ex.toString());
		}
	}
}
