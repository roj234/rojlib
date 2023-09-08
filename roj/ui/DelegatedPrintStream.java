package roj.ui;

import org.jetbrains.annotations.ApiStatus.OverrideOnly;
import roj.io.DummyOutputStream;
import roj.text.CharList;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;

/**
 * @author Roj234
 * @since 2021/5/29 20:45
 */
public class DelegatedPrintStream extends PrintStream {
	public static CharList exceptionToString(Throwable e) {
		DelegatedPrintStream err = new DelegatedPrintStream(99999);
		e.printStackTrace(err);
		return err.sb;
	}

	protected CharList sb = new CharList();
	protected final int MAX;
	private CharsetDecoder cd;

	public DelegatedPrintStream(int max) {
		super(DummyOutputStream.INSTANCE);
		MAX = max;
	}

	public synchronized final void write(int var1) {
		if (var1 == '\n') newLine();
		else {
			sb.append((char) var1);
			if (sb.length() > MAX) {
				sb.delete(0);
			}
		}
	}

	public synchronized final void write(@Nonnull byte[] b, int off, int len) {
		if (sb.length() + len > MAX) {
			sb.delete(0, sb.length() + len - MAX);
		}

		int pOff = off;
		while (len-- > 0) {
			if (b[off] == '\n') {
				decode(b, pOff, off-pOff);
				pOff = off+1;
				newLine();
			}
			off++;
		}

		if (off > pOff) decode(b, pOff, off-pOff);
	}

	private void decode(byte[] b, int off, int len) { sb.append(Charset.defaultCharset().decode(ByteBuffer.wrap(b, off, len))); }

	private synchronized void write(CharSequence str) {
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
	}

	@OverrideOnly
	protected synchronized void newLine() {
		sb.clear();
	}

	public final void flush() {}
	public final void close() {}
	public final boolean checkError() {
		return false;
	}

	public final PrintStream append(CharSequence var1, int var2, int var3) {
		write(var1 == null ? "null" : var1.subSequence(var2, var3).toString());
		return this;
	}

	public final void print(boolean var1) {
		write(var1 ? "true" : "false");
	}

	public final void print(char var1) {
		write(String.valueOf(var1));
	}

	public final void print(int var1) {
		write(String.valueOf(var1));
	}

	public final void print(long var1) {
		write(String.valueOf(var1));
	}

	public final void print(float var1) {
		write(String.valueOf(var1));
	}

	public final void print(double var1) {
		write(String.valueOf(var1));
	}

	public final void print(@Nonnull char[] var1) {
		write(new CharList(var1));
	}

	public final void print(String var1) {
		if (var1 == null) var1 = "null";
		write(var1);
	}

	public final void print(Object var1) {
		write(var1 instanceof CharSequence ? (CharSequence) var1 : String.valueOf(var1));
	}

	public final void println() {
		newLine();
	}

	public final synchronized void println(boolean var1) {
		print(var1);
		newLine();
	}

	public final synchronized void println(char var1) {
		print(var1);
		newLine();
	}

	public final synchronized void println(int var1) {
		print(var1);
		newLine();
	}

	public final synchronized void println(long var1) {
		print(var1);
		newLine();
	}

	public final synchronized void println(float var1) {
		print(var1);
		newLine();
	}

	public final synchronized void println(double var1) {
		print(var1);
		newLine();
	}

	public final synchronized void println(@Nonnull char[] var1) {
		print(var1);
		newLine();
	}

	public final synchronized void println(String var1) {
		print(var1);
		newLine();
	}

	public final void println(Object var1) {
		String var2 = String.valueOf(var1);
		synchronized (this) {
			print(var2);
			newLine();
		}
	}

	public CharList getChars() {
		return sb;
	}

	public int getMax() {
		return MAX;
	}
}
