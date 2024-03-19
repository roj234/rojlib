package roj.config.serial;

import org.jetbrains.annotations.NotNull;
import roj.config.Tokenizer;

/**
 * @author Roj233
 * @since 2022/2/19 19:14
 */
public final class ToJson extends ToSomeString {
	public ToJson() { super(); }
	public ToJson(@NotNull String indent) { super(indent); }

	protected final void listNext() { sb.append(','); }
	protected final void mapNext() { sb.append(','); }
	protected final void endLevel() {
		if ((flag&NEXT) != 0) indent(depth);
		sb.append((flag & 12) == LIST ? ']' : '}');
	}

	public final void valueMap() { push(MAP); sb.append('{'); }
	public final void valueList() { push(LIST); sb.append('['); }

	@Override
	protected void indent(int x) {
		if (indentCount > 0) {
			sb.append('\n');
			if (comment != null) writeSingleLineComment("//");
			sb.padEnd(indent, indentCount*x);
		}
	}

	public final void key0(String key) {
		indent(depth);
		Tokenizer.addSlashes(key, 0, sb.append('"'), '\'').append("\":");
		if (indentCount > 0) sb.append(' ');
	}
}