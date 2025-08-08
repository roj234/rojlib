package roj.collect;

import org.jetbrains.annotations.Range;
import roj.io.IOUtil;

import java.util.Objects;

/**
 * 可继承的IntSet，通常用来处理权限继承等
 */
public class FlagSet {
	private static final int IMPORTANT = 0x80000000;
	private final ToIntMap<CharSequence> map = new ToIntMap<>();
	private final String delimiter;

	public FlagSet() { this("/"); }
	public FlagSet(String delimiter) {this.delimiter = Objects.requireNonNull(delimiter, "分隔符不能为空");}

	public void addAll(FlagSet set) {
		if (!delimiter.equals(set.delimiter)) throw new IllegalArgumentException("分隔符不匹配");
		map.putAll(set.map);
	}

	/**
	 * 添加或更新权限节点
	 *
	 * @param node 权限节点路径
	 * @param value 权限值，必须为非负整数
	 * @param important 是否标记为重要权限（具有最高优先级）
	 * @param inheritable 是否可被下级节点继承
	 * @throws IllegalArgumentException 如果权限值为负数
	 * @throws NullPointerException 如果节点为null
	 */
	public void add(String node, @Range(from = 0, to = Integer.MAX_VALUE) int value, boolean important, boolean inheritable) {
		assert value >= 0;
		if (important) value |= IMPORTANT;
		if (inheritable) map.putInt(node.concat(delimiter), value);
		map.putInt(node, value);
	}
	public boolean remove(String node) {
		var remove = map.remove(node);
		map.remove(node.concat(delimiter));
		return remove != null;
	}

	/**
	 * 检查是否存在指定权限节点（直接或继承）
	 *
	 * @param permissionNode 要检查的权限节点
	 * @return 如果权限存在则返回true
	 * @throws NullPointerException 如果节点为null
	 */
	public boolean has(String permissionNode) {return get(permissionNode, 0) != 0;}

	/**
	 * 获取指定权限节点的有效权限值，考虑继承和重要标记
	 *
	 * @param node 权限节点
	 * @param def 默认权限值
	 * @return 计算后的权限值（不含重要标记位）
	 * @throws NullPointerException 如果节点为null
	 */
	public int get(CharSequence node, int def) {
		var entry = (ToIntMap.Entry<CharSequence>) map.getEntry(node);
		if (entry != null) def = entry.value;
		else {
			var sb = IOUtil.getSharedCharBuf().append(node);
			boolean deepest = false;

			while (true) {
				int i = sb.lastIndexOf(delimiter, sb.length()-2);
				if (i < 0) break;
				sb.setLength(i+1);

				entry = (ToIntMap.Entry<CharSequence>) map.getEntry(sb);
				if (entry == null) continue;

				if ((entry.value & IMPORTANT) != 0) {
					// important
					def = entry.value;
					break;
				}

				if (!deepest) {
					def = entry.value;
					deepest = true;
				}
			}
		}

		return def & ~IMPORTANT;
	}

	/**
	 * 获取权限节点的完整位表示，包括所有继承的权限
	 *
	 * @param node 权限节点
	 * @return 合并所有相关权限后的位表示（不含重要标记位）
	 * @throws NullPointerException 如果节点为null
	 */
	public int getBits(CharSequence node) {
		var entry = (ToIntMap.Entry<CharSequence>) map.getEntry(node);
		var bits = entry != null ? entry.value : 0;

		var sb = IOUtil.getSharedCharBuf().append(node);

		while (true) {
			int i = sb.lastIndexOf(delimiter, sb.length()-2);
			if (i < 0) break;
			sb.setLength(i+1);

			entry = (ToIntMap.Entry<CharSequence>) map.getEntry(sb);
			if (entry == null) continue;

			if ((entry.value & IMPORTANT) != 0) {
				if ((bits&IMPORTANT) == 0) {
					bits = entry.value;
					continue;
				}
			} else {
				if ((bits&IMPORTANT) != 0)
					continue;
			}

			bits |= entry.value;
		}

		return bits & ~IMPORTANT;
	}

	@Override public String toString() {return "FlagSet{"+map+'}';}
}