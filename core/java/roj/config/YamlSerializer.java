package roj.config;

import org.jetbrains.annotations.NotNull;
import roj.text.DateFormat;
import roj.text.TextUtil;
import roj.text.Tokenizer;

import java.util.TimeZone;

/**
 * @author Roj233
 * @since 2022/2/19 19:14
 */
public final class YamlSerializer extends TextEmitter {
	public YamlSerializer() { super(" "); }
	public YamlSerializer(@NotNull String indent) { super(indent.isEmpty() ? " " : indent);  }

	public YamlSerializer multiline(boolean b) { this.multiline = b; return this; }
	public YamlSerializer timezone(TimeZone tz) { timezone = tz; return this; }

	private boolean topLevel, multiline;

	protected final void listIndent() {}
	protected final void listNext() { indent(depth); writer.append("-"); }

	protected final void mapNext() {}
	protected final void endLevel() {
		if ((flag&32) != 0) writer.append((flag & 12) == LIST ? " []" : " {}");
	}

	@Override
	protected void preValue(boolean hasNext) {
		super.preValue(hasNext);
		if ((flag&(LIST|MAP)) != 0) {
			if (!hasNext) writer.append(' ');
		}
	}

	@Override
	protected void indent(int depth) {
		if (topLevel) writer.append('\n');
		else topLevel = true;

		depth--;
		if (comment != null) flushComment("#", depth);
		writer.padEnd(indent, indentCount*depth);
	}

	public final void emitMap() { push(MAP|32); }
	public final void emitList() { push(LIST|NEXT|32); }

	private TimeZone timezone;
	@Override
	public final void emitDate(long millis) {
		preValue(false);
		DateFormat.format("Y-m-d", millis, timezone, writer);
	}
	@Override
	public final void emitTimestamp(long millis) {
		preValue(false);
		DateFormat.format(millis%1000 != 0 ? DateFormat.ISO8601_Millis : DateFormat.ISO8601_Seconds, millis, timezone, writer);
	}

	@Override
	public final void valString(CharSequence val) {
		if (multiline && TextUtil.indexOf(val, '\n') >= 0) {
			writer.append(val.charAt(val.length()-1) == '\n'?"|+":"|-");

			int i = 0;
			do {
				writer.append('\n');
				if (i == 0 || val.charAt(i) != '\n') {
					writer.padEnd(indent, depth*indentCount);
				}

				i = TextUtil.gAppendToNextCRLF(val, i, writer);
			} while (i < val.length());
			return;
		}

		if (YamlParser.literalSafe(val, true)<0) writer.append(val);
		else super.valString(val);
	}

	@Override
	protected final void valNull() { writer.append('~'); }

	@Override
	protected final void key0(String key) {
		indent(depth);
		(YamlParser.literalSafe(key, false)<0 ? writer.append(key) : Tokenizer.escape(writer.append('"'), key, 0, '\'').append('"')).append(":");
	}

	@Override
	public final YamlSerializer reset() {
		super.reset();
		topLevel = false;
		return this;
	}
}