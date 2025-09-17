package roj.config;

import org.jetbrains.annotations.NotNull;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.Tokenizer;

import java.io.IOException;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/1/29 8:44
 */
public abstract class TextEmitter implements ValueEmitter {
	protected static final int END = 1, NEXT = 2, MAP = 4, LIST = 8, VALUE = 16;

	protected CharList writer = new CharList();
	protected int flag;

	public TextEmitter to(CharList writer) {this.writer = writer;return this;}

	private int[] stack = new int[2];
	protected int depth;

	protected char indent;
	protected byte indentCount;

	protected TextEmitter() {}
	protected TextEmitter(@NotNull String indent) {
		this.indentCount = (byte) indent.length();
		if (indent.length() > 0)
			this.indent = indent.charAt(0);
	}

	protected void indent(int depth) {
		if (indentCount > 0) {
			writer.append('\n').padEnd(indent, depth * indentCount);
		}
	}
	protected final void flushComment(CharSequence prefix, int depth) {
		String str = comment;
		comment = null;

		int i = 0;
		while (i < str.length()) {
			writer.padEnd(indent, depth * indentCount).append(prefix);
			i = TextUtil.gAppendToNextCRLF(str, i, writer);
			writer.append('\n');
		}
	}

	public final void emit(int i) { preValue(false); writer.append(i); }
	public final void emit(String s) {
		preValue(false);
		if (s == null) valNull();
		else valString(s);
	}
	public void valString(CharSequence l) {Tokenizer.escape(writer.append('"'), l, 0, '\'').append('"');}

	public final void emit(long i) { preValue(false); writer.append(i); }
	public final void emit(double i) { preValue(false); writer.append(i); }
	public final void emit(boolean b) { preValue(false); writer.append(b); }
	public final void emitNull() { preValue(false); valNull(); }
	protected void valNull() { writer.append("null"); }

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
	public final void emitKey(String key) {
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

	public final CharList mapValue() {preValue(false);return writer;}
	public final CharList getValue() {
		while (depth > 0) pop();
		return writer;
	}
	public String toString() { return writer.toString(); }

	public TextEmitter reset() {
		comment = null;
		depth = 0;
		flag = 0;
		writer.clear();
		return this;
	}

	public final void close() throws IOException {
		if (writer instanceof AutoCloseable c) IOUtil.closeSilently(c);
	}
}