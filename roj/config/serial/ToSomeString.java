package roj.config.serial;

import roj.config.word.ITokenizer;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.Helpers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * @author Roj234
 * @since 2022/1/29 8:44
 */
public abstract class ToSomeString implements CConsumer {
	protected static final int END = 1, NEXT = 2, MAP = 4, LIST = 8;

	protected CharList sb = new CharList();
	protected OutputStream out;
	protected int flag;

	public ToSomeString sb(CharList sb) {
		this.sb = sb;
		return this;
	}
	public ToSomeString stream(OutputStream out) {
		this.out = out;
		return this;
	}

	private int[] stack = new int[2];
	protected int depth;

	protected char[] indent;

	public void setIndent(char... indent) {
		for (char c : indent) {
			if (c != ' ' && c != '\t')
				throw new IllegalStateException("Only space and/or tab can be used in indent");
		}
		this.indent = indent;
	}
	public void setIndent(String indent) {
		setIndent(indent.toCharArray());
	}

	protected final void indent(int x) {
		if (indent.length > 0) {
			sb.append('\n');
			while (x-- > 0) sb.append(indent);
		}
	}

	@Override
	public final void value(int l) {
		int f = preValue();
		sb.append(l);
		postValue(f);
	}

	@Override
	public final void value(String s) {
		if (s == null) {
			valueNull();
			return;
		}

		int f = preValue();
		valString(s);
		postValue(f);
	}
	protected void valString(String l) {
		ITokenizer.addSlashes(l, sb.append('"')).append('"');
	}

	@Override
	public final void value(long l) {
		int f = preValue();
		sb.append(l);
		postValue(f);
	}

	@Override
	public final void value(double l) {
		int f = preValue();
		sb.append(l);
		postValue(f);
	}

	@Override
	public final void value(boolean l) {
		int f = preValue();
		sb.append(l);
		postValue(f);
	}

	@Override
	public final void valueNull() {
		int f = preValue();
		valNull();
		postValue(f);
	}
	protected void valNull() {
		sb.append("null");
	}

	protected final int preValue() {
		int f = this.flag;
		if ((f & END) != 0) throw new IllegalStateException("EOF");
		if (f == (NEXT | LIST)) listNext();
		return f;
	}
	protected final void postValue(int f) {
		flag = f >= LIST ? f | NEXT : f | END;
	}

	protected final void push(int data) {
		int f = preValue();
		f = f >= LIST ? f | NEXT : f | END;

		// 4bits
		int i = depth / 8;
		int j = (depth++ & 7) << 2;

		int[] LV = this.stack;
		if (LV.length < i) {
			this.stack = LV = Arrays.copyOf(LV, i);
		}
		LV[i] = (LV[i] & ~(15 << j)) | (f << j);

		flag = data;
	}

	@Override
	public final void pop() {
		if (depth == 0) throw new IllegalStateException("level = 0");
		int i = --depth / 8;
		int j = (depth & 7) << 2;

		endLevel();
		flag = (stack[i] >>> j) & 15;

		try {
			mayFlush(false);
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}

	@Override
	public final void key(String key) {
		if ((flag & 12) != MAP) throw new IllegalStateException("Not map");
		if ((flag & NEXT) == 0) {
			if ((flag & END) == 0) throw new IllegalStateException("缺少值");
			mapNext();
			flag &= ~END;
		} else {
			flag &= ~NEXT;
		}
		key0(key);

		try {
			mayFlush(false);
		} catch (IOException e) {
			Helpers.athrow(e);
		}
	}

	protected abstract void listNext();
	protected abstract void mapNext();
	protected abstract void endLevel();

	protected abstract void key0(String key);

	public final CharList getValue() {
		while (depth > 0) pop();
		return sb;
	}

	public final CharList getHalfValue() {
		return sb;
	}

	public void reset() {
		depth = 0;
		flag = 0;
		sb.clear();
		out = null;
	}

	@Override
	public String toString() {
		return getValue().toString();
	}

	private void mayFlush(boolean flush) throws IOException {
		if (out != null && (flush||sb.length() > 1024)) {
			IOUtil.SharedCoder.get().encodeTo(sb, out);
			sb.clear();
		}
	}

	public final void flush() throws IOException {
		while (depth > 0) pop();
		mayFlush(true);
	}

	public final void close() throws IOException {
		if (out != null) out.close();
	}
}
