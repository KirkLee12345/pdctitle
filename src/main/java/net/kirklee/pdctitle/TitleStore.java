package net.kirklee.pdctitle;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 称号持久化（v3，两个文件，均原子写盘）：
 *
 *   config/pdctitle/definitions.json   —— 称号池（全局定义，OP 管理）
 *   {
 *     "legend":  { "display": "&b[至尊]", "description": "开服元老纪念称号" },
 *     "founder": { "display": "&a[创世元老]", "description": "帮助建设服务器的人" }
 *   }
 *
 *   config/pdctitle/players.json       —— 玩家归属与佩戴
 *   {
 *     "<uuid>": { "owned": ["legend", "founder"], "equipped": "legend" }
 *   }
 *
 * 语义要点：
 *   - owned/equipped 存的是池 id；删池条目 → 所有玩家该 id 被级联清除（佩戴中自动卸下）；
 *   - 加载时对“指向不存在 id”的脏数据做清理（玩家侧未知 id 丢弃）。
 *
 * 线程模型：单例 + synchronized；变更即 save()。
 */
public final class TitleStore {
	public static final String DEFS_FILE = "definitions.json";
	public static final String PLAYERS_FILE = "players.json";

	private final Path defsFile;
	private final Path playersFile;
	private final Map<String, TitleDefinition> definitions = new LinkedHashMap<>();
	private final Map<UUID, PlayerData> byPlayer = new HashMap<>();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public TitleStore(Path dir) {
		this.defsFile = dir.resolve(DEFS_FILE);
		this.playersFile = dir.resolve(PLAYERS_FILE);
	}

	// ---------------- 称号池 ----------------

	public synchronized Optional<TitleDefinition> definition(String id) {
		return Optional.ofNullable(definitions.get(id));
	}

	public synchronized Map<String, TitleDefinition> definitionsSnapshot() {
		return Map.copyOf(definitions);
	}

	public synchronized boolean hasDefinition(String id) {
		return definitions.containsKey(id);
	}

	/** 新增或覆盖池条目（id 不可变，改文案/描述=覆盖 value）。 */
	public synchronized void putDefinition(String id, TitleDefinition def) {
		definitions.put(id, def);
	}

	/**
	 * 删除池条目并级联清理所有玩家的该 id（佩戴中自动卸下）。
	 * @return 受影响（此前拥有/佩戴过该 id）的玩家 UUID，供上层刷新
	 */
	public synchronized List<UUID> removeDefinition(String id) {
		definitions.remove(id);
		List<UUID> affected = new ArrayList<>();
		for (Map.Entry<UUID, PlayerData> e : byPlayer.entrySet()) {
			PlayerData pd = e.getValue();
			if (pd.revoke(id)) affected.add(e.getKey());
		}
		return affected;
	}

	// ---------------- 玩家归属 ----------------

	public synchronized Optional<PlayerData> get(UUID uuid) {
		return Optional.ofNullable(byPlayer.get(uuid));
	}

	public synchronized PlayerData getOrCreate(UUID uuid) {
		return byPlayer.computeIfAbsent(uuid, u -> new PlayerData());
	}

	/** 授权：目标 id 必须在池中存在，且玩家未拥有。 @return 是否成功 */
	public synchronized boolean grant(UUID uuid, String id) {
		if (!definitions.containsKey(id)) return false;
		return getOrCreate(uuid).grant(id);
	}

	/** 收回：从玩家 owned 移除（若佩戴中自动卸下）。 */
	public synchronized boolean revoke(UUID uuid, String id) {
		PlayerData pd = byPlayer.get(uuid);
		return pd != null && pd.revoke(id);
	}

	/** 佩戴：id 须存在池中且玩家拥有。 */
	public synchronized boolean wear(UUID uuid, String id) {
		if (!definitions.containsKey(id)) return false;
		PlayerData pd = byPlayer.get(uuid);
		return pd != null && pd.wear(id);
	}

	// ---------------- 持久化 ----------------

	public synchronized void load() {
		byPlayer.clear();
		definitions.clear();
		loadDefs();
		loadPlayers();
	}

	private void loadDefs() {
		if (!Files.isRegularFile(defsFile)) return;
		try (Reader r = Files.newBufferedReader(defsFile, StandardCharsets.UTF_8)) {
			JsonObject root = gson.fromJson(r, JsonObject.class);
			if (root == null) return;
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				String id = e.getKey();
				try {
					JsonObject o = e.getValue().getAsJsonObject();
					String display = o.has("display") ? o.get("display").getAsString() : "";
					String description = o.has("description") ? o.get("description").getAsString() : "";
					definitions.put(id, new TitleDefinition(display, description));
				} catch (Exception ex) {
					PDCTitle.LOGGER.warn("definitions.json 条目无法解析，已跳过: {}", id);
				}
			}
		} catch (IOException | RuntimeException ex) {
			PDCTitle.LOGGER.warn("读取 {} 失败: {}", defsFile, ex.toString());
		}
	}

	private void loadPlayers() {
		if (!Files.isRegularFile(playersFile)) return;
		try (Reader r = Files.newBufferedReader(playersFile, StandardCharsets.UTF_8)) {
			JsonObject root = gson.fromJson(r, JsonObject.class);
			if (root == null) return;
			for (Map.Entry<String, JsonElement> e : root.entrySet()) {
				try {
					UUID uuid = UUID.fromString(e.getKey());
					JsonObject o = e.getValue().getAsJsonObject();
					PlayerData pd = new PlayerData();
					if (o.has("owned")) {
						for (JsonElement el : o.getAsJsonArray("owned")) {
							String id = el.getAsString();
							// 只保留池中仍存在的 id（脏数据清理）
							if (definitions.containsKey(id)) pd.grant(id);
						}
					}
					if (o.has("equipped") && !o.get("equipped").isJsonNull()) {
						String id = o.get("equipped").getAsString();
						if (pd.owns(id)) pd.wear(id);
					}
					byPlayer.put(uuid, pd);
				} catch (Exception ex) {
					PDCTitle.LOGGER.warn("players.json 条目无法解析，已跳过: {}", e.getKey());
				}
			}
		} catch (IOException | RuntimeException ex) {
			PDCTitle.LOGGER.warn("读取 {} 失败: {}", playersFile, ex.toString());
		}
	}

	public synchronized void save() {
		writeJson(defsFile, toDefsJson());
		writeJson(playersFile, toPlayersJson());
	}

	private JsonObject toDefsJson() {
		JsonObject root = new JsonObject();
		for (Map.Entry<String, TitleDefinition> e : definitions.entrySet()) {
			JsonObject o = new JsonObject();
			o.addProperty("display", e.getValue().display());
			o.addProperty("description", e.getValue().description());
			root.add(e.getKey(), o);
		}
		return root;
	}

	private JsonObject toPlayersJson() {
		JsonObject root = new JsonObject();
		for (Map.Entry<UUID, PlayerData> e : byPlayer.entrySet()) {
			JsonObject o = new JsonObject();
			var arr = new com.google.gson.JsonArray();
			for (String id : e.getValue().owned()) arr.add(id);
			o.add("owned", arr);
			e.getValue().equipped().ifPresent(id -> o.addProperty("equipped", id));
			root.add(e.getKey().toString(), o);
		}
		return root;
	}

	private void writeJson(Path path, JsonObject root) {
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				gson.toJson(root, w);
			}
			Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ex) {
			PDCTitle.LOGGER.warn("写入 {} 失败: {}", path, ex.toString());
		}
	}
}
