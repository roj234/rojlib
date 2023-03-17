package roj.config.word;

import roj.math.MathUtils;
import roj.text.CharList;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/12/11 0011 9:12
 */
public abstract class ReadOnDemand implements CharSequence, Closeable {
	private char[] buf = new char[128];
	private int off, maxOff, len;
	protected boolean eof;
	protected int forwardBuffer, backwardBuffer;

	ReadOnDemand() {
		forwardBuffer = 4;
		backwardBuffer = 1;
	}

	@Override
	public final int length() {
		return eof ? off+len : 0x70000000;
	}

	@Override
	public final char charAt(int i) {
		ensureReadable(i);
		if (i > off+len) return 0;
		return buf[i-off];
	}

	@Nonnull
	@Override
	public final CharSequence subSequence(int start, int end) {
		ensureReadable(end);
		return new CharList.Slice(buf, start-off, end-off);
	}
	public final String subSequence_String(int start, int end) {
		ensureReadable(end);
		return new String(buf, start-off, end-start);
	}

	protected void ensureReadable(int i) {
		int pos = i-off;
		if (pos < 0) throw new UnsupportedOperationException("Buffer flushed at " + off + " and you're getting " + i);

		int rem = pos - len + forwardBuffer;
		if (rem > 0 && !eof) {
			int dt = maxOff-off;
			if (dt > 0) {
				off = maxOff;
				System.arraycopy(buf, dt, buf, 0, len-dt);
				len -= dt;
			}

			// 不到一半的话，填一半
			rem = Math.max(rem, buf.length/2 - len);

			if (buf.length < len+rem) {
				char[] newBuf = new char[MathUtils.getMin2PowerOf(len+rem)];
				System.arraycopy(buf, 0, newBuf, 0, len);
				buf = newBuf;
			}

			try {
				while (rem > 0) {
					int r = fillIn(buf, len, rem);
					if (r < 0) {
						eof = true;
						break;
					}

					len += r;
					rem -= r;
				}
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}
	}

	protected abstract int fillIn(char[] list, int off, int len) throws IOException;

	public final void freeBufferBefore(int pos) {
		pos -= backwardBuffer;
		int dt = pos - off;
		if (dt > 0) maxOff = pos;
	}

	public void setForwardBuffer(int forwardBuffer) {
		this.forwardBuffer = forwardBuffer;
	}
	public void setBackwardBuffer(int backwardBuffer) {
		this.backwardBuffer = backwardBuffer;
	}

	public abstract void close() throws IOException;

	protected void reset() {
		off = maxOff = len = 0;
		eof = false;
	}

	@Override
	public final String toString() {
		return "ReaderSeq@" + off + ":" + new String(buf,0,len);
	}
}
