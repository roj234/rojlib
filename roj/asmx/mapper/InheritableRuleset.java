package roj.asmx.mapper;

import roj.collect.ToIntMap;
import roj.text.CharList;

public class InheritableRuleset {
	public static final int IMPORTANT = 0x80000000;
	private final ToIntMap<CharSequence> map = new ToIntMap<>();
	private final String delimiter;
	private final CharList sb = new CharList();

	public InheritableRuleset() { this("|"); }
	public InheritableRuleset(String delimiter) {this.delimiter = delimiter;}

	public void set(String level, int value, boolean important, boolean inheritable) {
		assert value >= 0;
		if (important) value |= IMPORTANT;
		if (inheritable) map.putInt(level.concat(delimiter), value);
		map.putInt(level, value);
	}

	public int get(CharSequence level, int def) {
		ToIntMap.Entry<CharSequence> ent = (ToIntMap.Entry<CharSequence>) map.getEntry(level);
		if (ent != null) return ent.v;

		this.sb.clear();
		CharList sb = this.sb.append(level);
		boolean hasVal = false;

		while (true) {
			int i = sb.lastIndexOf(delimiter, sb.length()-2);
			if (i < 0) break;
			sb.setLength(i+1);

			ent = (ToIntMap.Entry<CharSequence>) map.getEntry(sb);
			if (ent == null) continue;

			if ((ent.v & IMPORTANT) == 0) {
				if (!hasVal) {
					def = ent.v & ~IMPORTANT;
					hasVal = true;
				}
			} else {
				// important
				def = ent.v & ~IMPORTANT;
				break;
			}
		}

		return def;
	}
}