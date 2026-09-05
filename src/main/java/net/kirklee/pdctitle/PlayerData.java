package net.kirklee.pdctitle;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 一名玩家与称号相关的全部状态：
 *   - owned       ：被 OP 授权的称号池 id 集合（0..N），LinkedHashSet 保持顺序稳定；
 *   - equipped    ：当前佩戴的池 id（至多 1 个，null = 不佩戴），必须是 owned 成员；
 *   - lastKnownName：最近一次在线时记录的游戏名，供 OP 对离线玩家按名字操作时反查 UUID。
 *
 * 不变量：equipped == null || owned.contains(equipped)。
 * 池条目被删除时的级联清理由 TitleStore.removeDefinition 统一完成。
 */
public final class PlayerData {
	private final Set<String> owned = new LinkedHashSet<>();
	private String equipped;
	private String lastKnownName;

	public Set<String> owned() {
		return Set.copyOf(owned);
	}

	public Optional<String> equipped() {
		return Optional.ofNullable(equipped);
	}

	public Optional<String> lastKnownName() {
		return Optional.ofNullable(lastKnownName);
	}

	public void rememberName(String name) {
		this.lastKnownName = name;
	}

	public boolean owns(String id) {
		return owned.contains(id);
	}

	/** 授权一条池中称号。 @return true=新增；false=已拥有 */
	public boolean grant(String id) {
		return owned.add(id);
	}

	/** 收回授权。若收回的正是佩戴中的称号则自动卸下。 */
	public boolean revoke(String id) {
		boolean removed = owned.remove(id);
		if (removed && id.equals(equipped)) {
			equipped = null;
		}
		return removed;
	}

	/** 佩戴自己拥有的称号。 @return 是否成功 */
	public boolean wear(String id) {
		if (!owned.contains(id)) return false;
		equipped = id;
		return true;
	}

	/** 卸下（不佩戴任何称号）。 */
	public void unwear() {
		equipped = null;
	}

	/** 清空全部授权并卸下。 */
	public boolean clear() {
		boolean changed = !owned.isEmpty() || equipped != null;
		owned.clear();
		equipped = null;
		return changed;
	}
}
