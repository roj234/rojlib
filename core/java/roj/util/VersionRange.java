package roj.util;

import roj.text.CharList;

import java.util.regex.Matcher;

/**
 * 表示一个版本范围，用于定义哪些版本满足特定条件。
 * 支持多种格式的版本范围表达式，包括比较运算符、区间表示法和波浪号范围。
 *
 * <p><strong>支持的格式：</strong></p>
 * <ul>
 *   <li>大于：{@code >1.0} 或 {@code >=1.0}</li>
 *   <li>等于：{@code =1.0} 或 {@code ==1.0}</li>
 *   <li>小于：{@code <1.0} 或 {@code <=1.0}</li>
 *   <li>波浪号范围：{@code ~1.2.3} 表示 [1.2.3, 1.3.0)</li>
 *   <li>区间表示法：{@code [1.0,2.0]}、{@code (1.0,2.0)}、{@code [1.0,)}、{@code (,2.0]}</li>
 * </ul>
 *
 * <p>示例：</p>
 * <pre>
 * VersionRange range = VersionRange.parse("[1.0,2.0)");
 * boolean suitable = range.suitable(new ArtifactVersion("1.5"));
 * </pre>
 *
 * <p>注意：此类是不可变的，</p>
 * @author Roj234
 * @since 2023/1/7 22:12
 */
public final class VersionRange {
	private ArtifactVersion lower, upper;
	private boolean lowerInclusive, upperInclusive;

	public static VersionRange parse(String s) {
		s = s.trim();

		var range = new VersionRange();

		char c = s.charAt(0);
		switch (c) {
			case '>' -> {
				int i = 1;
				if (s.charAt(i) == '=') {
					range.lowerInclusive = true;
					i++;
				}
				range.lower = new ArtifactVersion(s.substring(i).trim());

				return range;
			}
			case '=' -> {
				int i = 1;
				if (s.charAt(i) == '=') i++;
				range.lower = range.upper = new ArtifactVersion(s.substring(i).trim());
				return range;
			}
			case '<' -> {
				int i = 1;
				if (s.charAt(i) == '=') {
					range.upperInclusive = true;
					i++;
				}
				range.upper = new ArtifactVersion(s.substring(i).trim());

				return range;
			}
			case '~' -> {
				var min = new ArtifactVersion(s.substring(1).trim());
				range.lower = min;
				range.lowerInclusive = true;

				Matcher semVer = ArtifactVersion.SEMVER.matcher(min.toString());
				var version = new CharList().append(semVer.group(1)).append('.').append(Integer.parseInt(semVer.group(2))+1).append('.').append(semVer.group(3));
				if (semVer.group(4) != null) version.append('-').append(semVer.group(4));

				range.upper = new ArtifactVersion(version.toStringAndFree());

				return range;
			}
			case '[' -> range.lowerInclusive = true;
			case '(' -> {}
			default -> throw new IllegalArgumentException("版本范围格式错误: "+s);
		}

		c = s.charAt(s.length()-1);
		if (c == ']') range.upperInclusive = true;
		else if (c != ')') throw new IllegalArgumentException("版本范围格式错误: "+s);

		int pos = s.indexOf(',');
		if (pos < 0) {
			range.lower = range.upper = new ArtifactVersion(s.substring(1, s.length()-1).trim());
		} else {
			String lower = s.substring(1, pos).trim();
			if (!lower.isEmpty()) range.lower = new ArtifactVersion(lower);
			else if (range.lowerInclusive) throw new IllegalArgumentException("版本范围格式错误: "+s);

			String upper = s.substring(pos+1, s.length()-1).trim();
			if (!upper.isEmpty()) range.upper = new ArtifactVersion(upper);
			else if (range.upperInclusive) throw new IllegalArgumentException("版本范围格式错误: "+s);
		}

		if (range.lower != null && range.upper != null) {
			int i = range.lower.compareTo(range.upper);
			if (i == 0) {
				if (!(range.lowerInclusive & range.upperInclusive)) {
					throw new IllegalArgumentException("版本范围格式错误: "+s);
				}
			} else if (i > 0) {
				throw new IllegalArgumentException("最低版本号>最高版本号: "+s);
			}
		}

		return range;
	}

	public final boolean suitable(ArtifactVersion v) {
		int r;
		if (lower != null) {
			r = v.compareTo(lower);
			if (r < 0) return false;
			if (r == 0) return lowerInclusive;
		}
		if (upper != null) {
			r = v.compareTo(upper);
			if (r > 0) return false;
			if (r == 0) return upperInclusive;
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(lowerInclusive ? '[' : '(');
		if (lower != null) sb.append(lower);
		sb.append(',');
		if (upper != null) sb.append(upper);
		sb.append(upperInclusive ? ']' : ')');
		return sb.toString();
	}
}