package roj.config.serial;

import roj.config.word.ITokenizer;
import roj.util.ArrayCache;

/**
 * @author Roj233
 * @since 2022/2/19 19:14
 */
public class ToJson extends ToSomeString {
	public ToJson() {
		this.indent = ArrayCache.CHARS;
	}

	public ToJson(int indent) {
		spaceIndent(indent);
	}

	public ToJson(String indent) {
		this.indent = indent.toCharArray();
	}

	protected final void listNext() { sb.append(","); }
	protected final void mapNext() { sb.append(","); }
	protected final void endLevel() {
		if ((flag&NEXT) != 0) indent(depth);
		sb.append((flag & 12) == LIST ? ']' : '}');
	}

	public final void valueMap() { push(MAP); sb.append('{'); }
	public final void valueList() { push(LIST); sb.append('['); }

	@Override
	protected void indent(int x) {
		if (indent.length > 0) {
			sb.append('\n');
			if (comment != null) writeSingleLineComment("//");
			while (x > 0) {
				sb.append(indent);
				x--;
			}
		}
	}

	public final void key0(String key) {
		indent(depth);
		ITokenizer.addSlashes(sb.append('"'), key).append("\":");
		if (indent.length > 0) sb.append(' ');
	}
}
