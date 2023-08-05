package roj.text;

import java.io.IOException;
import java.io.Reader;
import java.nio.CharBuffer;

/**
 * @author Roj234
 * @since 2023/9/10 0010 10:00
 */
public class CharSequenceReader extends Reader {
	private CharSequence seq;
	private char[] arr;

	private int pos, len, prevPos;

	public CharSequenceReader(CharSequence seq) { this.seq = seq; }

	public int read(CharBuffer target) throws IOException {
		int len = Math.min(target.remaining(), this.len-pos);

		if (arr != null) {
			target.put(arr, pos, len);
		} else {
			for(int i = 0; i < len; i++)
				target.put(seq.charAt(pos+i));
		}
		pos += len;
		return len;
	}

	public int read() throws IOException { return pos < len ? seq.charAt(pos++) : -1; }
	public int read(char[] b, int off, int len) throws IOException {
		if (len <= 0) return 0;
		if (this.len == pos) return -1;

		len = Math.min(len, this.len-pos);

		if (arr != null) {
			System.arraycopy(arr, pos, b, off, len);
		} else {
			for(int i = 0; i < len; i++)
				b[off + i] = seq.charAt(pos+i);
		}
		pos += len;

		return len;
	}

	public long skip(long len) throws IOException {
		len = Math.min(len, this.len-pos);
		pos += len;
		return len;
	}

	public boolean ready() { return true; }
	public boolean markSupported() { return true; }
	public void mark(int readAheadLimit) { prevPos = pos; }
	public void reset() { pos = prevPos; }

	public void close() {}
}
