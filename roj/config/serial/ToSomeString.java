package roj.config.serial;

import roj.config.word.ITokenizer;
import roj.text.CharList;
import roj.text.StreamWriter;
import roj.text.TextUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/1/29 8:44
 */
public abstract class ToSomeString implements CVisitor {
	private static final char[] TAB = {'\t'};

	protected static final int END = 1, NEXT = 2, MAP = 4, LIST = 8, VALUE = 16;

	protected CharList sb = new CharList();
	protected int flag;

	public ToSomeString sb(CharList sb) {
		this.sb = sb;
		return this;
	}
	public ToSomeString to(OutputStream out) {
		return to(out, StandardCharsets.UTF_8);
	}
	public ToSomeString to(OutputStream out, Charset charset) {
		sb = new StreamWriter(out, charset).append(sb);
		return this;
	}

	private int[] stack = new int[2];
	protected int depth;

	protected char[] indent;

	public final void tabIndent() { indent = TAB; }
	public final void spaceIndent(int len) {
		indent = new char[len];
		Arrays.fill(indent, ' ');
	}

	protected void indent(int x) {
		if (indent.length > 0) {
			sb.append('\n');
			while (x-- > 0) sb.append(indent);
		}
	}
	protected final void writeSingleLineComment(CharSequence prefix) {
		String str = comment;
		comment = null;

		int i = 0;
		while (i < str.length()) {
			for (int j = 0; j < depth; j++) sb.append(indent);
			sb.append(prefix);
			i = TextUtil.gAppendToNextCRLF(str, i, sb);
			sb.append('\n');
		}
	}

	public final void value(int l) { preValue(); sb.append(l); }
	public final void value(String s) {
		preValue();
		if (s == null) valNull();
		else valString(s);
	}
	protected void valString(String l) {
		ITokenizer.addSlashes(l, sb.append('"')).append('"');
	}

	public final void value(long l) { preValue(); sb.append(l); }
	public final void value(double l) { preValue(); sb.append(l); }
	public final void value(boolean l) { preValue(); sb.append(l); }
	public final void valueNull() { preValue(); valNull(); }
	protected void valNull() {
		sb.append("null");
	}

	protected String comment;
	public final void comment(String s) {
		if (indent.length == 0) return;
		comment = s;
	}

	protected final void preValue() {
		int f = flag;
		if ((f & END) != 0) throw new IllegalStateException("早前遇到了Terminator");
		if ((f & LIST) != 0) {
			if ((f & NEXT) != 0) listNext();
			listIndent();
		}
		flag = f >= MAP ? (f&15)|NEXT : f|END;
	}

	protected final void push(int type) {
		preValue();

		// 4bits
		int i = depth / 8;
		int j = (depth++ & 7) << 2;

		int[] LV = this.stack;
		if (LV.length < i) {
			this.stack = LV = Arrays.copyOf(LV, i);
		}
		LV[i] = (LV[i] & ~(15 << j)) | (flag << j);

		flag = type;
	}

	@Override
	public final void pop() {
		if (depth == 0) throw new IllegalStateException("level = 0");
		int i = --depth / 8;
		int j = (depth & 7) << 2;

		endLevel();
		flag = (stack[i] >>> j) & 15;
	}

	@Override
	public final void key(String key) {
		if ((flag & 12) != MAP) throw new IllegalStateException("不是MAP");

		if ((flag & VALUE) != 0) throw new IllegalStateException("缺少值");
		flag |= VALUE;

		if ((flag & NEXT) != 0) mapNext();
		key0(key);
	}

	protected void listIndent() { indent(depth); }
	protected abstract void listNext();
	protected abstract void mapNext();
	protected abstract void endLevel();

	protected abstract void key0(String key);

	public final CharList getValue() {
		while (depth > 0) pop();
		return sb;
	}
	public final CharList getHalfValue() { return sb; }
	public String toString() { return getValue().toString(); }

	public void reset() {
		comment = null;
		depth = 0;
		flag = 0;
		sb.clear();
	}

	public final void flush() throws IOException {
		if (sb instanceof StreamWriter) {
			while (depth > 0) pop();
			((StreamWriter) sb).flush();
		}
	}
	public final void finish() throws IOException {
		if (sb instanceof StreamWriter) {
			while (depth > 0) pop();
			((StreamWriter) sb).finish();
		}
	}
	public final void close() throws IOException {
		if (sb instanceof StreamWriter)
			((StreamWriter) sb).close();
	}
}
