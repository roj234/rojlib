package roj.mod.mapping;

import roj.math.Version;

/**
 * @author Roj234
 * @since 2023/1/7 0007 22:12
 */
public final class VersionRange {
	Version from, to;
	boolean fromInclusive, toInclusive;

	public static VersionRange parse(String s) {
		s = s.trim();

		VersionRange vr = new VersionRange();
		// [1.12.2, )
		char c = s.charAt(0);
		if (c == '[') vr.fromInclusive = true;
		else if (c != '(') throw new IllegalArgumentException("Illegal char " + c);

		c = s.charAt(s.length() - 1);
		if (c == ']') vr.toInclusive = true;
		else if (c != ')') throw new IllegalArgumentException("Illegal char " + c);

		int pos = s.indexOf(',');
		if (pos < 0) throw new IllegalArgumentException("No ',' found in " + s);
		String fr = s.substring(1, pos).trim();
		vr.from = fr.isEmpty() ? null : new Version(fr);
		String to = s.substring(pos + 1, s.length() - 1).trim();
		vr.to = to.isEmpty() ? null : new Version(to);

		return vr;
	}

	public final boolean suitable(Version v) {
		int r;
		if (from != null) {
			r = v.compareTo(from);
			if (r < 0) return false;
			if (r == 0) return fromInclusive;
		}
		if (to != null) {
			r = v.compareTo(to);
			if (r > 0) return false;
			if (r == 0) return toInclusive;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(fromInclusive ? '[' : '(');
		if (from != null) sb.append(from);
		sb.append(',');
		if (to != null) sb.append(to);
		sb.append(toInclusive ? ']' : ')');
		return sb.toString();
	}
}
