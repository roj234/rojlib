package roj.util;

import org.jetbrains.annotations.NotNull;
import roj.collect.ArrayList;
import roj.collect.IntList;
import roj.config.data.CEntry;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ArtifactVersion {
	public static final Pattern SEMVER = Pattern.compile("^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([a-zA-Z\\d.-]+?))?(\\+.+?)?$");
	private static final Pattern DOT = Pattern.compile("[-.]");

	private final String canonical;
	private final int[] version;
	private final Comparable<?>[] tags;
	private final byte qualifierType;

	public ArtifactVersion(String version) {
		Matcher matcher = SEMVER.matcher(version);
		String tag;
		String[] splittedTag;
		var sb = new CharList(10);
		var tags = new ArrayList<Comparable<?>>();
		if (matcher.matches()) {
			this.version = new int[] {
					Integer.parseInt(matcher.group(1)),
					matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)),
					matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3))
			};

			tag = matcher.group(4);
			if (tag != null) splittedTag = TextUtil.split1(tag, '.');
			else splittedTag = null;
		} else {
			var items = new IntList();
			boolean foundSplitter = false;
			int pos = 0;
			for (; pos < version.length(); pos++) {
				char c = version.charAt(pos);
				if (c == '.' || c == '-') {
					foundSplitter = true;
				} else if (c >= '0' && c <= '9') {
					if (foundSplitter) {
						foundSplitter = false;

						items.add(TextUtil.parseInt(sb));
						sb.clear();
					}

					sb.append(c);
				} else {
					while (sb.charAt(sb.length()-1) == '.') sb.setLength(sb.length()-1);
					break;
				}
			}
			if (sb.length() != 0) {
				items.add(TextUtil.parseInt(sb));
				sb.clear();
			}

			this.version = items.toArray();
			tag = version.substring(pos);
			splittedTag = DOT.split(tag);
		}

		int[] ver = this.version;
		for (int i = 0; i < ver.length;) {
			sb.append(ver[i++]).append(i == ver.length ? '-' : '.');
		}

		if (tag != null && !tag.isBlank()) {
			for (String t : splittedTag) {
				if (TextUtil.isNumber(t) == 0) {
					if (TextUtil.isNumber(t, TextUtil.INT_MAXS) == 0) {
						tags.add((Comparable<?>) CEntry.valueOf(Integer.parseInt(t)));
					} else {
						tags.add((Comparable<?>) CEntry.valueOf(Long.parseLong(t)));
					}
				} else {
					tags.add((Comparable<?>) CEntry.valueOf(t));
				}
			}

			// alpha, beta, rc, snapshot等标签应当小于无标签，而未知标签, 例如Final, 应大于无标签
			qualifierType = switch (tags.get(0).toString().toLowerCase(Locale.ROOT)) {
				case "alpha" -> -4;
				case "beta" -> -3;
				case "rc" -> -2;
				case "snapshot" -> -1;
				default -> 0;
			};
		} else {
			qualifierType = 0;
		}
		this.tags = tags.toArray(new Comparable<?>[tags.size()]);

		if (tag != null) {
			canonical = sb.append(tag).toString();
		} else {
			sb.setLength(sb.length()-1);
			canonical = sb.toString();
		}
	}

	public int compareTo(@NotNull ArtifactVersion o) {
		int cmp;
		int max = Math.max(version.length, o.version.length);
		for (int i = 0; i < max; i++) {
			int self = i >= version.length ? 0 : version[i];
			int other = i >= o.version.length ? 0 : o.version[i];

			cmp = Integer.compare(self, other);
			if (cmp != 0) return cmp;
		}

		cmp = Integer.compare(qualifierType, o.qualifierType);
		if (cmp != 0) return cmp;

		int min = Math.min(tags.length, o.tags.length);
		for (int i = 0; i < min; i++) {
			cmp = tags[i].compareTo(Helpers.cast(o.tags[i]));
			if (cmp != 0) return cmp;
		}
		return tags.length - o.tags.length;
	}

	public String toString() { return canonical; }
}