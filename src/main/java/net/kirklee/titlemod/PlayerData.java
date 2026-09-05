package net.kirklee.titlemod;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 一名玩家与称号相关的全部状态：
 *   - owned   ：拥有的称号集合（0..N），LinkedHashSet 保证命令补全/展示顺序稳定；
 *   - equipped：当前“佩戴”的称号（至多 1 条，可为 null = 不佩戴）。
 *
 * 设计约束：
 *   - 同一条原文案只能拥有一次（重复授予报“已拥有”）；
 *   - 收回正佩戴的称号时自动卸下；
 *   - 所有变更应通过方法完成，保持不变量，方便 TitleStore 变更即落盘。
 */
public final class PlayerData {
	private final Set<TitleData> owned = new LinkedHashSet<>();
	private TitleData equipped;

	/** 只读快照（防止外部直接改集合破坏不变量）。 */
	public Set<TitleData> owned() {
		return Set.copyOf(owned);
	}

	public Optional<TitleData> equipped() {
		return Optional.ofNullable(equipped);
	}

	public boolean owns(String display) {
		for (TitleData t : owned) {
			if (t.display().equals(display)) return true;
		}
		return false;
	}

	/** @return true = 新增成功；false = 已拥有同名称号 */
	public boolean grant(TitleData title) {
		return owned.add(title);
	}

	/** 收回。若收回的正是佩戴中的称号，自动卸下。 @return 是否确实存在并移除 */
	public boolean revoke(TitleData title) {
		boolean removed = owned.remove(title);
		if (removed && title.equals(equipped)) {
			equipped = null;
		}
		return removed;
	}

	/** 佩戴自己拥有的称号。 @return 是否成功（不存在则失败） */
	public boolean wear(String display) {
		for (TitleData t : owned) {
			if (t.display().equals(display)) {
				equipped = t;
				return true;
			}
		}
		return false;
	}

	/** 卸下佩戴（= 不佩戴任何称号，显示原名）。 */
	public void unwear() {
		equipped = null;
	}

	/** 清空全部称号并卸下。 @return 是否发生变更 */
	public boolean clear() {
		boolean changed = !owned.isEmpty() || equipped != null;
		owned.clear();
		equipped = null;
		return changed;
	}
}
