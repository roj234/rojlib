package roj.ui;

import org.jetbrains.annotations.NotNull;
import roj.io.DummyOutputStream;
import roj.text.CharList;
import roj.text.FastCharset;
import roj.text.TextUtil;
import roj.util.ByteList;

import java.io.PrintStream;
import java.util.Formatter;
import java.util.Locale;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public abstract class DelegatedPrintStream extends PrintStream {
	protected CharList sb = new CharList();
	protected ByteList bb = new ByteList();

	public DelegatedPrintStream() {
		super(DummyOutputStream.INSTANCE);
		super.close();
	}

	protected void newLine() { sb.clear(); }
	protected void partialLine() {}
	protected void flushBytes() {
		NativeVT.decode(bb, sb);
		bb.clear();
	}
	public void flush() {}

	// region stub
	public final synchronized void write(int v) {
		if (v == '\n') {flushBytes();newLine();}
		else {
			bb.put(v);
			partialLine();
		}
	}
	public final synchronized void write(@NotNull byte[] b, int i, int len) {
		int prevI = i;
		while (len-- > 0) {
			if (b[i] == '\n') {
				int isRn = i > prevI && b[i - 1] == '\r' ? 1 : 0;
				bb.put(b, prevI, i-prevI-isRn);
				prevI = i+1;

				flushBytes();
				newLine();
			}
			i++;
		}

		if (i > prevI) {
			bb.put(b, prevI, i-prevI);
			partialLine();
		}

		flush();
	}

	public final PrintStream append(CharSequence v, int s, int e) { append(v == null ? "null" : v.subSequence(s, e)); return this; }
	public synchronized final PrintStream append(CharSequence str) { return append0(str, false); }
	private PrintStream append0(CharSequence str, boolean newLine) {
		flushBytes();

		int i = 0;
		do {
			i = TextUtil.gAppendToNextCRLF(str, i, sb, -1);
			if (i < 0) {
				if (newLine) newLine();
				else partialLine();
				break;
			}

			newLine();
		} while (i < str.length());

		flush();
		return this;
	}

	public synchronized final void print(boolean v) { flushBytes(); sb.append(v); partialLine(); flush(); }
	public synchronized final void print(char v) { flushBytes(); sb.append(v); partialLine(); flush(); }
	public synchronized final void print(int v) { flushBytes(); sb.append(v); partialLine(); flush(); }
	public synchronized final void print(long v) { flushBytes(); sb.append(v); partialLine(); flush(); }
	public synchronized final void print(float v) { flushBytes(); sb.append(v); partialLine(); flush(); }
	public synchronized final void print(double v) { flushBytes(); sb.append(v); partialLine(); flush(); }
	public final void print(@NotNull char[] v){ append(new CharList(v)); }
	public final void print(String v) { append(v == null ? "null" : v); }
	public final void print(Object v) { append(v == null ? "null" : v.toString()); }

	public final synchronized void println() { newLine(); flush(); }
	public final synchronized void println(boolean v) { flushBytes(); sb.append(v); newLine(); flush(); }
	public final synchronized void println(char v) { flushBytes(); sb.append(v); newLine(); flush(); }
	public final synchronized void println(int v) { flushBytes(); sb.append(v); newLine(); flush(); }
	public final synchronized void println(long v) { flushBytes(); sb.append(v); newLine(); flush(); }
	public final synchronized void println(float v) { flushBytes(); sb.append(v); newLine(); flush(); }
	public final synchronized void println(double v) { flushBytes(); sb.append(v); newLine(); flush(); }
	public final synchronized void println(@NotNull char[] v) { append0(new CharList(v), true); }
	public final synchronized void println(String v) { append0(v == null ? "null" : v, true); }
	public final synchronized void println(Object v) { append0(v == null ? "null" : v.toString(), true); }

	public final PrintStream format(@NotNull String format, Object... args) {return format(Locale.getDefault(Locale.Category.FORMAT), format, args);}
	private Formatter formatter;
	public final synchronized PrintStream format(Locale l, String format, Object ... args) {
		if (formatter == null || formatter.locale() != l) formatter = new Formatter(this, l);
		formatter.format(l, format, args);
		return this;
	}
	// endregion
}