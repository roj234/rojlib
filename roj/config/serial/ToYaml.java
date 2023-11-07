package roj.config.serial;

import roj.config.YAMLParser;
import roj.config.word.ITokenizer;
import roj.text.ACalendar;
import roj.text.TextUtil;

import java.util.TimeZone;

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
		if (multiline && val.indexOf('\n') >= 0) {
			sb.append(val.charAt(val.length()-1) == '\n'?"|+":"|-");

			int i = 0;
			do {
				sb.append('\n');
				if (i == 0 || val.charAt(i) != '\n') {
					int x = depth;
					while (x-- > 0) sb.append(indent);
				}

				i = TextUtil.gAppendToNextCRLF(val, i, sb);
			} while (i < val.length());
			return;
		}

		if (YAMLParser.literalSafe(val)<0) sb.append(val);
		else super.valString(val);
	}

	@Override
	protected final void valNull() { sb.append('~'); }

	@Override
	public final void key0(String key) {
		indent(depth);
		(YAMLParser.literalSafe(key)<0 ? sb.append(key) : ITokenizer.addSlashes(key, 0, sb.append('"'), '\'').append('"')).append(":");
	}

	@Override
	public final void reset() {
		super.reset();
		topLevel = false;
	}
}
