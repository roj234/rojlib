package roj.ui;

import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import org.jetbrains.annotations.NotNull;
import roj.io.DummyOutputStream;
import roj.text.CharList;
import roj.text.GB18030;
import roj.text.TextUtil;
import roj.text.UTF8MB4;
import roj.util.ByteList;

import java.io.PrintStream;
import java.nio.charset.Charset;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class DelegatedPrintStream extends PrintStream {
	protected CharList sb = new CharList();
	protected ByteList bb = new ByteList();
	protected final int MAX;

	public DelegatedPrintStream(int max) {
		super(DummyOutputStream.INSTANCE);
		MAX = max;
	}

	@OverrideOnly
	protected void newLine() { flushBytes(); sb.clear(); }
	protected void partialLine() { if (bb.length() >= 128) flushBytes(); }
	protected void flushBytes() {
		Charset cs = Charset.defaultCharset();
		(GB18030.is(cs) ? GB18030.CODER : UTF8MB4.CODER).decodeLoop(bb, bb.readableBytes(), sb, MAX, true);
	}

	public void flush() { flushBytes(); }
	public boolean checkError() { return false; }

	// region stub
	public synchronized final void write(int v) {
		if (v == '\n') {
			newLine();
			return;
		}

		bb.put(v);
		partialLine();
	}
	public synchronized final void write(@NotNull byte[] b, int i, int len) {
		int prevI = i;
		while (len-- > 0) {
			if (b[i] == '\n') {
				bb.put(b, prevI, i-prevI);
				prevI = i+1;

				newLine();
			}
			i++;
		}

		if (i > prevI) {
			bb.put(b, prevI, i-prevI);
			partialLine();
		}
	}

	public final PrintStream append(CharSequence v, int s, int e) { append(v == null ? "null" : v.subSequence(s, e)); return this; }
	public synchronized final PrintStream append(CharSequence str) { return append0(str, false); }
	private PrintStream append0(CharSequence str, boolean newLine) {
		flushBytes();

		int i = 0;
		while (true) {
			i = TextUtil.gAppendToNextCRLF(str, i, sb);
			if (sb.length() > MAX) {
				sb.setLength(MAX-9);
				sb.append("<该行过长...>");
			}
			if (i < str.length()) newLine();
			else break;
		}
		// gAppendToNextCRLF无法确定最后是不是\n (i+1 and str.length)
		if (i > 0 && str.charAt(i-1) == '\n') newLine();

		if (newLine) newLine();
		else partialLine();
		return this;
	}

	public synchronized final void print(boolean v) { flushBytes(); sb.append(v); partialLine(); }
	public synchronized final void print(char v) { flushBytes(); sb.append(v); partialLine(); }
	public synchronized final void print(int v) { flushBytes(); sb.append(v); partialLine(); }
	public synchronized final void print(long v) { flushBytes(); sb.append(v); partialLine(); }
	public synchronized final void print(float v) { flushBytes(); sb.append(v); partialLine(); }
	public synchronized final void print(double v) { flushBytes(); sb.append(v); partialLine(); }
	public final void print(@NotNull char[] v){ append(new CharList.Slice(v,0,v.length)); }
	public final void print(String v) { append(v == null ? "null" : v); }
	public final void print(Object v) { append(v == null ? "null" : v.toString()); }

	public final synchronized void println() { newLine(); }
	public final synchronized void println(boolean v) { flushBytes(); sb.append(v); newLine(); }
	public final synchronized void println(char v) { flushBytes(); sb.append(v); newLine(); }
	public final synchronized void println(int v) { flushBytes(); sb.append(v); newLine(); }
	public final synchronized void println(long v) { flushBytes(); sb.append(v); newLine(); }
	public final synchronized void println(float v) { flushBytes(); sb.append(v); newLine(); }
	public final synchronized void println(double v) { flushBytes(); sb.append(v); newLine(); }
	public final synchronized void println(@NotNull char[] v) { flushBytes(); sb.append(v); newLine(); }
	public final synchronized void println(String v) { append0(v == null ? "null" : v, true); }
	public final synchronized void println(Object v) { append0(v == null ? "null" : v.toString(), true); }
	// endregion
}