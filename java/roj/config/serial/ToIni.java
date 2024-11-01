package roj.config.serial;

import roj.config.IniParser;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.IOException;

import static roj.config.data.CMap.CONFIG_TOPLEVEL;

/**
 * @author Roj234
 * @since 2023/3/27 22:38
 */
public final class ToIni implements CVisitor {
	private CharList sb;
	public ToIni() {}

	public ToIni sb(CharList sb) {
		this.sb = sb;
		return this;
	}

	private int depth;
	private boolean hasTop;

	public final void value(boolean l) {sb.append(l).append('\n');}
	public final void value(int l) {sb.append(l).append('\n');}
	public final void value(long l) {sb.append(l).append('\n');}
	public final void value(float l) {sb.append(l).append('\n');}
	public final void value(double l) {sb.append(l).append('\n');}
	public final void value(String value) {
		if (IniParser.literalSafe(value)) sb.append(value);
		else Tokenizer.addSlashes(sb.append('"'), value).append('"');
		sb.append('\n');
	}
	public final void valueNull() {sb.append("null\n");}

	@Override
	public void comment(String comment) {
		int i = 0;
		while (i < comment.length()) {
			sb.append(';');
			i = TextUtil.gAppendToNextCRLF(comment, i, sb);
			sb.append('\n');
		}
	}

	// *no state check*
	public final void key(String key) {
		if (depth == 1) {
			if (key.equals(CONFIG_TOPLEVEL)) {
				if (hasTop) throw new IllegalArgumentException("TopLevel必须是第一个:"+key);
				return;
			}
			hasTop = true;
			sb.append("\n[");
			if (IniParser.literalSafe(key)) sb.append(key);
			else Tokenizer.addSlashes(sb, key);
			sb.append("]\n");
		} else if (depth == 2) {
			if (IniParser.literalSafe(key)) sb.append(key);
			else Tokenizer.addSlashes(sb, key);
			sb.append('=');
		} else {
			throw new IllegalArgumentException("INI不支持两级以上的映射");
		}

	}

	public final void valueList() {
		if (depth != 1) throw new IllegalArgumentException("Can not serialize LIST to INI/"+depth);
		depth++;
	}
	public final void valueMap() {
		if (depth > 1) throw new IllegalArgumentException("Can not serialize MAP to INI/"+depth);
		depth++;
	}
	public final void pop() {
		depth--;
	}

	public final ToIni reset() {
		depth = 0;
		hasTop = false;
		return this;
	}

	public final CharList getValue() {reset();return sb;}
	public String toString() {return sb.toString();}

	@Override public void close() throws IOException { if (sb instanceof AutoCloseable x) IOUtil.closeSilently(x);}
}