package roj.config.serial;

import org.jetbrains.annotations.NotNull;
import roj.config.Tokenizer;
import roj.config.YAMLParser;
import roj.text.DateTime;
import roj.text.TextUtil;

import java.util.TimeZone;

/**
 * @author Roj233
 * @since 2022/2/19 19:14
 */
public final class ToYaml extends ToSomeString {
	public ToYaml() { super(" "); }
	public ToYaml(@NotNull String indent) { super(indent.isEmpty() ? " " : indent);  }

	public ToYaml multiline(boolean b) { this.multiline = b; return this; }
	public ToYaml timezone(TimeZone tz) { cal = DateTime.forTimezone(tz); return this; }

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

		if (comment != null) writeSingleLineComment("#");

		// there are > 0 check in padEnd()
		sb.padEnd(indent, indentCount*(x-1));
	}

	public final void valueMap() { push(MAP|32); }
	public final void valueList() { push(LIST|NEXT|32); }

	private DateTime cal;
	@Override
	public final void valueDate(long mills) {
		preValue(false);
		if (cal == null) cal = DateTime.GMT();
		cal.format("Y-m-d", mills, sb);
	}
	@Override
	public final void valueTimestamp(long mills) {
		preValue(false);
		if (cal == null) cal = DateTime.GMT();
		cal.toISOString(sb, mills);
	}

	@Override
	public final void valString(CharSequence val) {
		if (multiline && TextUtil.indexOf(val, '\n') >= 0) {
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

		if (YAMLParser.literalSafe(val, true)<0) sb.append(val);
		else super.valString(val);
	}

	@Override
	protected final void valNull() { sb.append('~'); }

	@Override
	protected final void key0(String key) {
		indent(depth);
		(YAMLParser.literalSafe(key, false)<0 ? sb.append(key) : Tokenizer.escape(sb.append('"'), key, 0, '\'').append('"')).append(":");
	}

	@Override
	public final ToYaml reset() {
		super.reset();
		topLevel = false;
		return this;
	}
}