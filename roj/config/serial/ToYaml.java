package roj.config.serial;

import roj.config.YAMLParser;
import roj.config.word.ITokenizer;
import roj.text.ACalendar;
import roj.text.TextUtil;

import java.util.TimeZone;

import static roj.config.word.ITokenizer.WHITESPACE;

/**
 * @author Roj233
 * @since 2022/2/19 19:14
 */
public class ToYaml extends ToSomeString {
	private static final char[] DEFAULT_INDENT = {' '};

	public ToYaml() {
		indent = DEFAULT_INDENT;
	}

	public ToYaml(int indent) {
		if (indent == 0) throw new IllegalArgumentException("YAML requires indent");
		spaceIndent(indent);
	}

	public ToYaml multiline(boolean b) {
		this.multiline = b;
		return this;
	}

	public ToYaml timezone(TimeZone tz) {
		cal = new ACalendar(tz);
		return this;
	}

	private boolean topLevel, multiline;

	protected final void listIndent() {}
	protected final void listNext() { indent(depth); sb.append("-"); }

	protected final void mapNext() {}
	protected final void endLevel() {
		if ((flag&32) != 0) sb.append((flag & 12) == LIST ? " []" : " {}");
	}

	@Override
	protected void preValue(boolean hasNext) {
		super.preValue(hasNext);
		if ((flag&(LIST|MAP)) != 0) {
			if (!hasNext) sb.append(' ');
		}
	}

	@Override
	protected void indent(int x) {
		if (topLevel) sb.append('\n');
		else topLevel = true;

		x--;

		if (comment != null) writeSingleLineComment("#");

		while (x > 0){
			sb.append(indent);
			x--;
		}
	}

	public final void valueMap() { push(MAP|32); }
	public final void valueList() { push(LIST|NEXT|32); }

	private ACalendar cal;
	@Override
	public final void valueDate(long value) {
		preValue(false);
		if (cal == null) cal = new ACalendar(TimeZone.getTimeZone("UTC"));
		cal.format("Y-m-d", value, sb);
	}
	@Override
	public final void valueTimestamp(long value) {
		preValue(false);
		if (cal == null) cal = new ACalendar(TimeZone.getTimeZone("UTC"));
		cal.toISOString(sb, value);
	}

	@Override
	protected final void valString(String val) {
		int check = YAMLParser.literalSafe(val);
		noFound:
		if (check<0) {
			sb.append(val);
			return;
		} else if (multiline&&check>0) {
			found:
			if (check != 1) {
				for (int i = 0; i < val.length(); i++) {
					char c = val.charAt(i);
					if (WHITESPACE.contains(c)) {
						if (c == '\n') break found;
						check = 3;
					}
				}
				break noFound;
			}

			sb.append(val.charAt(val.length()-1) == '\n'?"|+":"|-");

			boolean first;
			if (check == 3) {
				sb.append('\n');
				int x = depth;
				while (x-- > 0) sb.append(indent);

				first = false;
			} else {
				first = true;
			}

			int i = 0, len = -1;
			do {
				if (len != sb.length() || first) {
					if (len > 0) first = false;

					sb.append('\n');
					if (val.charAt(i) != '\n') {
						int x = depth;
						while (x-- > 0) sb.append(indent);
					}
				}
				len = sb.length();
				i = TextUtil.gAppendToNextCRLF(val, i, sb);
			} while (i < val.length());
			return;
		}

		ITokenizer.addSlashes(sb.append('"'), val).append('"');
	}

	@Override
	protected final void valNull() { sb.append('~'); }

	@Override
	public final void key0(String key) {
		indent(depth);
		(YAMLParser.literalSafe(key)<0 ? sb.append(key) : ITokenizer.addSlashes(sb.append('"'), key).append('"')).append(":");
	}

	@Override
	public final void reset() {
		super.reset();
		topLevel = false;
	}
}
