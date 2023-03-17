package roj.config.serial;

import roj.config.word.ITokenizer;

/**
 * @author Roj233
 * @since 2022/2/19 19:14
 */
public class ToJson extends ToSomeString {
	public ToJson() {
		this(0);
	}

	public ToJson(int indent) {
		this.indent = new char[indent];
		for (int i = 0; i < indent; i++) {
			this.indent[i] = ' ';
		}
	}

	public ToJson(String indent) {
		this.indent = indent.toCharArray();
	}

	@Override
	protected void listNext() {
		sb.append(",");
		indent(depth);
	}

	@Override
	protected void mapNext() {
		sb.append(",");
	}

	@Override
	protected void endLevel() {
		indent(depth);
		sb.append((flag & 12) == LIST ? ']' : '}');
	}

	@Override
	public void valueMap() {
		push(MAP | NEXT);
		sb.append('{');
	}

	@Override
	public void valueList() {
		push(LIST);
		sb.append('[');
		indent(depth);
	}

	@Override
	public void key0(String key) {
		indent(depth);
		ITokenizer.addSlashes(key, sb.append('"')).append("\":");
		if (indent.length > 0) sb.append(' ');
	}
}
