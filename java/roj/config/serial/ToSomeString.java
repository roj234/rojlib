package roj.config.serial;

import org.jetbrains.annotations.NotNull;
import roj.config.Tokenizer;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/1/29 8:44
 */
public abstract class ToSomeString implements CVisitor {
	protected static final int END = 1, NEXT = 2, MAP = 4, LIST = 8, VALUE = 16;

	protected CharList sb = new CharList();
	protected int flag;

	public ToSomeString sb(CharList sb) {
		this.sb = sb;
		return this;
	}

	private int[] stack = new int[2];
	protected int depth;

	protected char indent;
	protected byte indentCount;

	protected ToSomeString() {}
	protected ToSomeString(@NotNull String indent) {
		this.indentCount = (byte) indent.length();
		if (indent.length() > 0)
			this.indent = indent.charAt(0);
	}

	protected void indent(int x) {
		if (indentCount > 0) {
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

	public final void value(int l) { preValue(false); sb.append(l); }
	public final void value(String s) {
		preValue(false);
		if (s == null) valNull();
		else valString(s);
	}
	public void valString(CharSequence l) {Tokenizer.addSlashes(l, 0, sb.append('"'), '\'').append('"');}

	public final void value(long l) { preValue(false); sb.append(l); }
	public final void value(double l) { preValue(false); sb.append(l); }
	public final void value(boolean l) { preValue(false); sb.append(l); }
	public final void valueNull() { preValue(false); valNull(); }
	protected void valNull() { sb.append("null"); }

	protected String comment;
	public final void comment(String s) { if (indentCount > 0) comment = s; }

	protected void preValue(boolean hasNext) {
		int f = flag;
		if ((f & END) != 0) throw new IllegalStateException("早前遇到了Terminator");
		if ((f & LIST) != 0) {
			if ((f & NEXT) != 0) listNext();
			listIndent();
		}
		flag = f >= MAP ? (f&15)|NEXT : f|END;
	}

	protected final void push(int type) {
		preValue(true);

		// 4bits
		int i = depth / 8;
		int j = (depth++ & 7) << 2;

		int[] LV = stack;
		if (LV.length <= i) {
			stack = LV = Arrays.copyOf(LV, i+1);
		}
		LV[i] = (LV[i] & ~(15 << j)) | ((flag&15) << j);

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
	public String toString() { return sb.toString(); }

	public ToSomeString reset() {
		comment = null;
		depth = 0;
		flag = 0;
		sb.clear();
		return this;
	}

	public final void close() throws IOException {
		if (sb instanceof AutoCloseable c) IOUtil.closeSilently(c);
	}
}