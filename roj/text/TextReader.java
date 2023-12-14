package roj.text;

import roj.io.Finishable;
import roj.io.buf.BufferPool;
import roj.math.MathUtils;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.*;
import java.nio.file.StandardOpenOption;

/**
 * @author Roj234
 * @since 2022/12/11 0011 9:12
 */
public class TextReader extends Reader implements CharSequence, Closeable, Finishable, LinedReader {
	private char[] buf;
	private int off, maxOff, len;
	protected byte eof;

	private final Closeable in;
	private final byte type;

	private final CharsetDecoder cd;
	private final UnsafeCharset ucs;
	private ByteBuffer ib;
	private CharBuffer ob;

	public static TextReader auto(File file) throws IOException { return new TextReader(file, null); }
	public static TextReader auto(InputStream in) throws IOException { return new TextReader(in); }

	public TextReader(InputStream in) throws IOException { this(in, (Charset) null); }
	public TextReader(InputStream in, String charset) throws IOException { this(in, Charset.forName(charset)); }

	public TextReader(File in, Charset charset) throws IOException {
		this(FileChannel.open(in.toPath(), StandardOpenOption.READ), charset, 4096, BufferPool.localPool());
	}

	public TextReader(Closeable in, Charset charset) throws IOException { this(in, charset, 4096, BufferPool.localPool()); }
	public TextReader(Closeable in, Charset charset, int buffer, BufferPool pool) throws IOException {
		this.in = in;

		buf = ArrayCache.getCharArray(128, false);

		if (in instanceof DynByteBuf.BufferInputStream) {
			in = ((DynByteBuf.BufferInputStream) in).buffer();
		}

		if (in instanceof DynByteBuf) {
			ib = ((DynByteBuf) in).nioBuffer();
			lock = in;
			type = 2;
		} else {
			DynByteBuf buf = pool.allocate(!(in instanceof InputStream), buffer);
			buf.clear();

			lock = buf;
			ib = buf.nioBuffer();

			if (in instanceof InputStream) {
				type = 0;
			} else if (in instanceof ReadableByteChannel) {
				type = 1;
			} else {
				throw new IllegalArgumentException("无法确定 " + in.getClass().getName() + " 的类型");
			}
		}

		if (charset == null) {
			try (ChineseCharsetDetector cd = new ChineseCharsetDetector(in)) {
				charset = Charset.forName(cd.detect());

				if (type == 2) {
					ib.position(ib.position() + cd.skip());
				} else {
					if (ib.capacity() < cd.limit()) {
						BufferPool.reserve((DynByteBuf) lock);
						DynByteBuf buf = pool.allocate(ib.isDirect(), cd.limit());

						lock = buf;
						ib = buf.nioBuffer();
					}

					ib.clear();
					ib.put(cd.buffer(), 0, cd.limit());
					ib.flip();
				}
			} catch (Throwable e) {
				in.close();
				throw e;
			}
		}

		if (StandardCharsets.UTF_8 == charset) {
			ucs = UTF8MB4.CODER;
			cd = null;
		} else if (GB18030.is(charset)) {
			ucs = GB18030.CODER;
			cd = null;
		} else {
			ucs = null;
			cd = charset.newDecoder().onMalformedInput(CodingErrorAction.REPLACE).onUnmappableCharacter(CodingErrorAction.REPLACE).replaceWith("�");
		}
	}

	public String charset() { return ucs == null ? cd.charset().name() : ucs.name(); }

	public final TextReader throwOnFail() {
		cd.onMalformedInput(CodingErrorAction.REPORT);
		return this;
	}

	public final int length() { return eof<0 ? off+len : 0x70000000; }
	public final char charAt(int i) {
		fillBuffer(i+1);
		if (i > off+len) {
			if (i > off+len + 32) throw new IllegalStateException("not a sequence unless in Parser");
			return 0;
		}
		return buf[i-off];
	}
	@Nonnull
	public final CharSequence subSequence(int start, int end) {
		fillBuffer(end);
		return new CharList.Slice(buf, start-off, end-off);
	}

	private boolean unbuffered;
	public TextReader unbuffered() {
		unbuffered = true;
		return this;
	}

	private void fillBuffer(int i) {
		i -= off;
		if (i < 0) throw new IllegalStateException("Buffer flushed at ["+off+"+"+len+"] and you're getting " + (i+off));

		int toRead = i-len;
		if (toRead > 0 && eof==0) {
			int moveBefore = maxOff-off;
			if (moveBefore > 0) {
				off = maxOff;
				System.arraycopy(buf, moveBefore, buf, 0, len -= moveBefore);
			}

			if (!unbuffered) {
				toRead = Math.max(toRead, buf.length/2 - len);
				if (toRead < 32) toRead = 32;
			}

			if (buf.length < len+toRead) grow(toRead);

			try {
				while (toRead > 0) {
					int r = fill(buf, len, toRead);
					if (r < 0) break;

					len += r;
					toRead -= r;
				}
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}
	}
	private void grow(int toRead) {
		char[] newBuf = ArrayCache.getCharArray(MathUtils.getMin2PowerOf(len+toRead), false);
		System.arraycopy(buf, 0, newBuf, 0, len);
		ArrayCache.putArray(buf);
		buf = newBuf;
		ob = null;
	}

	private int bytesRead;
	final int fill(char[] buf, int off, int len) throws IOException {
		if (len == 0) return 0;
		if (eof == -2) return -1;

		ByteBuffer b = ib;
		if (ucs != null) {
			DynByteBuf ba = (DynByteBuf) lock;
			len += off;
			int prevOff = off;
			while (true) {
				long x = ucs.unsafeDecode(ba.array(),ba._unsafeAddr(),b.position(),b.limit(),buf,off,len-off);
				b.position((int) (x >>> 32));
				off = (int) x;

				if (len-off <= 1 || (eof < 0 && !b.hasRemaining())) break;

				read(b);
			}
			if (eof < 0) eof = -2;
			return prevOff == off ? -1 : off - prevOff;
		}

		CharBuffer ob = this.ob;
		if (ob == null || ob.array() != buf) ob = this.ob = CharBuffer.wrap(buf);
		ob.limit(off+len).position(off);

		while (true) {
			CoderResult res = cd.decode(b, ob, eof<0);
			if (res.isMalformed()) throw new IOException("在第 " + (bytesRead - b.remaining()) + " 个字节处解码失败.");
			if (res.isOverflow()) break;
			if (eof < 0) {
				if (eof == -1) {
					cd.flush(ob);
					eof = -2;
				}
				break;
			}
			read(b);
		}

		int provided = len-ob.remaining();
		return provided==0?-1:provided;
	}

	private void read(ByteBuffer b) throws IOException {
		int r;
		switch (type) {
			case 0:
				b.compact();
				r = ((InputStream) in).read(b.array(), b.arrayOffset() + b.position(), b.remaining());
				if (r >= 0) {
					b.position(b.position() + r);
					bytesRead += r;
				} else eof = -1;
				b.flip();
				break;
			case 1:
				b.compact();
				r = ((ReadableByteChannel) in).read(b);
				if (r >= 0) bytesRead += r;
				else eof = -1;
				b.flip();
				break;
			case 2:
			default: eof = -1; break;
		}
	}

	public final void releaseBefore(int pos) {
		// one char for backward read
		pos--;
		int dt = pos - off;
		if (dt > 0) maxOff = pos;
	}

	@Override
	public synchronized void close() throws IOException {
		finish();
		if (!(in instanceof DynByteBuf)) in.close();
	}

	@Override
	public synchronized void finish() throws IOException {
		if (lock instanceof DynByteBuf) {
			((DynByteBuf) lock).close();
			lock = this;
		}

		if (buf != null) {
			ArrayCache.putArray(buf);
			buf = null;
		}
	}

	@Override
	public int read() throws IOException {
		if (off < len) return buf[off++];

		int c = fill(buf, 0, buf.length);
		if (c < 0) return c;

		off = 1;
		len = c;

		return buf[0];
	}
	@Override
	public int read(@Nonnull char[] cbuf, int coff, int clen) throws IOException {
		int remain = clen;

		int blen = len-off;
		if (blen > 0) {
			if (clen < blen) blen = clen;

			System.arraycopy(buf, off, cbuf, coff, blen);
			off += blen;

			coff += blen;
			remain -= blen;
		}

		if (remain == 0) return clen;
		if (remain < buf.length) {
			int r = fill(buf, 0, buf.length);
			if (r <= 0) return r;
			this.off = 0;
			this.len = Math.max(r - remain, 0);
			System.arraycopy(buf, 0, cbuf, coff, remain);
			return clen;
		}

		return fill(cbuf, coff, remain) + clen-remain;
	}
	@Override
	public long skip(long n) throws IOException {
		long n1 = n;

		int blen = len-off;
		if (n < blen) {
			off += n;
			return n;
		} else {
			n -= blen;
			off = len = 0;
		}

		while (n > 0) {
			int i = read(buf, 0, (int) Math.min(n, buf.length));
			if (i < 0) break;
			n -= i;
		}
		return n1-n;
	}

	public boolean readLine(CharList ob) throws IOException {
		boolean append = false;
		while (true) {
			for (int i = off; i < len;) {
				char c = buf[i];
				if (c == '\r') {
					if (++i == len) {
						append = true;
						ob.append(buf, off, len-1);

						len = fill(buf, 0, buf.length);
						if (len <= 0) {
							ob.append('\r');
							return true;
						}

						if (buf[0] == '\n') {
							off = 1;
							return true;
						}

						ob.append('\r');
						off = i = 0;
						continue;
					}

					if (buf[i] != '\n') { continue; }

					ob.append(buf, off, i-1);
					off = i+1;
					return true;
				} else if (c == '\n') {
					ob.append(buf, off, i);
					off = i+1;
					return true;
				}

				i++;
			}
			if (off < len) {
				append = true;
				ob.append(buf, off, len);
			}

			off = 0;
			len = fill(buf, 0, buf.length);
			if (len <= 0) return append;
		}
	}

	private int markPos;
	public void mark(int lim) { maxOff = off + lim; markPos = off; }
	//public boolean markSupported() { return true; }
	public void reset() throws IOException {
		if (maxOff < 0) throw new IOException("mark cleared");
		off = markPos;
	}

	@Override
	public final String toString() {
		return "ReaderSeq@" + off + ":" + new String(buf,0,len);
	}
}
