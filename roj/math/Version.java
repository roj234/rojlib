package roj.math;

import org.jetbrains.annotations.NotNull;
import roj.collect.IntList;
import roj.config.word.ITokenizer;
import roj.text.CharList;
import roj.text.TextUtil;

public final class Version {
	public static final Version INFINITY = new Version("6.6.6-Inf");

	private String value;
	private final IntList items;

	public Version() { items = new IntList(); }
	public Version(String version) {
		items = new IntList();
		parse(version, false);
	}

	public Version parse(String version, boolean ex) {
		items.clear();

		CharList buf = new CharList(10);

		int pos = filterNonNumber(buf, version, ex);
		value = buf.append(version, pos, version.length()).toString();
		buf.clear();

		boolean dot = false;
		for (int i = 0; i < version.length(); i++) {
			char c = version.charAt(i);
			if (c == '.') {
				if (dot) throw new IllegalArgumentException("Invalid version " + version);
				dot = true;
			} else {
				if (dot) {
					items.add(TextUtil.parseInt(buf));
					buf.clear();
				}

				buf.append(c);
				dot = false;
			}
		}

		if (buf.length() != 0 && TextUtil.isNumber(buf) == 0) {
			items.add(TextUtil.parseInt(buf));
		}

		return this;
	}

	private static int filterNonNumber(CharList buf, String s, boolean thr) {
		int i = 0;
		for (; i < s.length(); i++) {
			char c = s.charAt(i);
			if (ITokenizer.NUMBER.contains(c) || c == '.') {
				buf.append(c);
			} else {
				if (thr) throw new IllegalArgumentException("Illegal version " + s);
				while (buf.charAt(buf.length()-1) == '.') buf.setLength(buf.length()-1);
				break;
			}
		}
		return i;
	}

	public int compareTo(@NotNull Version o) {
		if (this == INFINITY) return o == INFINITY ? 0 : 1;
		if (o == INFINITY) return -1;

		for (int i = 0; i < items.size(); i++) {
			int self = items.get(i);
			int other = o.items.size() <= i ? 0 : o.items.get(i);
			if (self > other) return 1;
			if (self < other) return -1;
		}
		return 0;
	}

	public String toString() { return value; }
}