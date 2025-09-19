package roj.config;

import org.jetbrains.annotations.NotNull;
import roj.text.Tokenizer;

/**
 * @author Roj233
 * @since 2025/9/17 10:25
 */
public class TomlSerializer extends TextEmitter {
	public TomlSerializer() { super(); }
	public TomlSerializer(@NotNull String indent) { super(indent); }

	protected final void listNext() { writer.append(','); }
	protected final void mapNext() { writer.append(','); }
	protected final void endLevel() {
		if ((flag&NEXT) != 0) indent(depth);
		writer.append((flag & 12) == LIST ? ']' : '}');
	}

	public final void emitMap() { push(MAP); writer.append('{'); }
	public final void emitList() { push(LIST); writer.append('['); }

	@Override
	protected void indent(int depth) {
		if (indentCount > 0) {
			writer.append('\n');
			if (comment != null) flushComment("//", depth);
			writer.padEnd(indent, indentCount*depth);
		}
	}

	public final void key0(String key) {
		indent(depth);
		Tokenizer.escape(writer.append('"'), key, 0, '\'').append("\":");
		if (indentCount > 0) writer.append(' ');
	}
}