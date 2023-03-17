package roj.math;

import roj.collect.IntList;
import roj.config.word.ITokenizer;
import roj.text.CharList;

import javax.annotation.Nonnull;

public final class Version {
	public static final Version INFINITY = new Version("999999.999999.999999");

	private String value;
	private final IntList items;

	public Version() {
		this.items = new IntList();
	}

	public Version(String version) {
		this.items = new IntList();

		this.parse(version, false);
	}

	public Version parse(String version, boolean ex) {
		items.clear();

		CharList buf = new CharList(10);

		filterNonNumber(buf, version, ex);
		value = version = buf.toString();
		buf.clear();

		boolean dot = false;
		for (int i = 0; i < version.length(); i++) {
			char c = version.charAt(i);
			if (c == '.') {
				if (dot) {
					// duplicate dot
					throw new IllegalArgumentException("Invalid version " + version);
				}
				dot = true;
			} else {
				if (dot) {
					// only 1 \r or \n
					this.items.add(MathUtils.parseInt(buf));
					buf.clear();
				}
				buf.append(c);
				dot = false;
			}
		}
		if (buf.length() != 0) {
			this.items.add(MathUtils.parseInt(buf));
		}

		return this;
	}

	private static void filterNonNumber(CharList buf, String s, boolean thr) {
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (ITokenizer.NUMBER.contains(c) || c == '.') {
				buf.append(c);
			} else {
				if (thr) throw new IllegalArgumentException("Illegal version " + s);
				while (buf.charAt(buf.length()-1) == '.') buf.setLength(buf.length()-1);
				break;
			}
		}
	}

	/**
	 * 1 自己大于别人
	 * 0 自己等于别人
	 * -1 自己小于别人
	 */
	public int compareTo(@Nonnull Version o) {
		for (int i = 0; i < items.size(); i++) {
			int self = items.get(i);
			int other = o.items.size() <= i ? 0 : o.items.get(i);
			if (self > other) return 1;
			if (self < other) return -1;
		}
		return 0;
	}

	public String toString() {
		return this.value;
	}
}