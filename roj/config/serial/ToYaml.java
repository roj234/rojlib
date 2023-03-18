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

	private boolean topLevel, multiline;

	protected final void listIndent() {}
	protected final void listNext() { indent(depth); sb.append("- "); }

	protected final void mapNext() {}
	protected final void endLevel() {}

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

	public final void valueMap() { push(MAP); }
	public final void valueList() { push(LIST); listNext(); }

	private ACalendar cal;
	@Override
	public final void valueDate(long value) {
		preValue();
		if (cal == null) cal = new ACalendar(TimeZone.getTimeZone("UTC"));
		cal.formatDate("Y-m-d", value, sb);
	}
	@Override
	public final void valueTimestamp(long value) {
		preValue();
		if (cal == null) cal = new ACalendar(TimeZone.getTimeZone("UTC"));
		cal.formatDate("Y-m-dTH:i:s.xZ", value, sb);
	}

	@Override
	protected final void valString(String key) {
		int check = YAMLParser.literalSafe(key);
		noFound:
		if (check<0) {
			sb.append(key);
			return;
		} else if (multiline&&check>0) {
			found:
			if (check != 1) {
				for (int i = 0; i < key.length(); i++) {
					char c = key.charAt(i);
					if (WHITESPACE.contains(c)) {
						if (c == '\n') break found;
						check = 3;
					} else if (c == ':' && i > 0 && (i+1 >= key.length() || WHITESPACE.contains(key.charAt(i+1)))) break noFound;
				}
				break noFound;
			}

			sb.append(key.charAt(key.length()-1) == '\n'?"|+":"|-");

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
					int x = depth;
					while (x-- > 0) sb.append(indent);
				}
				len = sb.length();
				i = TextUtil.gAppendToNextCRLF(key, i, sb);
			} while (i < key.length());
			return;
		}

		ITokenizer.addSlashes(key, sb.append('"')).append('"');
	}

	@Override
	protected final void valNull() { sb.append('~'); }

	@Override
	public final void key0(String key) {
		indent(depth);
		(YAMLParser.literalSafe(key)<0 ? sb.append(key) : ITokenizer.addSlashes(key, sb.append('"')).append('"')).append(": ");
	}

	@Override
	public final void reset() {
		super.reset();
		topLevel = false;
	}
}
