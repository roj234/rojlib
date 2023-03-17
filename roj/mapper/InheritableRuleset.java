package roj.mapper;

import roj.collect.ToIntMap;
import roj.io.IOUtil;
import roj.text.CharList;

public class InheritableRuleset {
	private final ToIntMap<CharSequence> map = new ToIntMap<>();
	private final String delimiter;

	public InheritableRuleset() { this("|"); }
	public InheritableRuleset(String delimiter) {this.delimiter = delimiter;}

	public void set(String level, int value, boolean important, boolean inheritable) {
		assert value >= 0;
		if (important) value |= 0x80000000;
		if (inheritable) map.putInt(level.concat(delimiter), value);
		map.putInt(level, value);
	}

	public int get(CharSequence level, int def) {
		ToIntMap.Entry<CharSequence> ent = map.getEntry(level);
		if (ent != null) return ent.v;

		CharList sb = IOUtil.ddLayeredCharBuf().append(level);
		boolean hasVal = false;

		while (true) {
			int i = sb.lastIndexOf(delimiter);
			if (i < 0) break;
			sb.setLength(i);

			ent = map.getEntry(sb);
			if (ent == null) continue;

			if ((ent.v & 0x80000000) == 0) {
				if (!hasVal) {
					def = ent.v & 0x7fffffff;
					hasVal = true;
				}
			} else {
				// important
				def = ent.v & 0x7fffffff;
				break;
			}
		}

		sb._free();
		return def;
	}
}
