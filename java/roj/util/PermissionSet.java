package roj.util;

import org.jetbrains.annotations.Range;
import roj.collect.ToIntMap;
import roj.io.IOUtil;

public class PermissionSet {
	private static final int IMPORTANT = 0x80000000;
	private final ToIntMap<CharSequence> map = new ToIntMap<>();
	private final String delimiter;

	public PermissionSet() { this("/"); }
	public PermissionSet(String delimiter) {this.delimiter = delimiter;}

	public void addAll(PermissionSet set) {
		if (!delimiter.equals(set.delimiter)) throw new IllegalArgumentException("delimiter not same");
		map.putAll(set.map);
	}

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

	public boolean has(String permissionNode) {return get(permissionNode, 0) != 0;}
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

	@Override public String toString() {return "PermissionSet{"+map+'}';}
}