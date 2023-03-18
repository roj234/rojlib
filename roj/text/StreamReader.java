package roj.text;

import roj.io.ChineseInputStream;
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
 * 现在，你不必将整个文件读取到内存中
 * @author Roj234
 * @since 2022/12/11 0011 9:12
 */
public class StreamReader extends Reader implements CharSequence, Closeable, Finishable {
	private char[] buf;
	private int off, maxOff, len;
	protected boolean eof;
	protected int forward = 4;

	private InputStream in;
	private ReadableByteChannel in2;

	private boolean throwOnFail;
	private final CharsetDecoder cd;
	private final ByteBuffer ib;
	private CharBuffer ob;

	private final ArrayCache cache = ArrayCache.getDefaultCache();
	private BufferPool pool;

	public StreamReader(InputStream in) { this(in, StandardCharsets.UTF_8); }
	public StreamReader(InputStream in, String charset) { this(in, Charset.forName(charset)); }
	public StreamReader(InputStream in, Charset charset) { this(in, charset, 1024, BufferPool.localPool()); }
	public StreamReader(InputStream in, Charset charset, int buffer, BufferPool pool) {
		this(charset, pool, pool.buffer(false, buffer));
		this.in = in;
		this.in2 = null;
	}

	public StreamReader(File in, Charset charset) throws IOException {
		this(FileChannel.open(in.toPath(), StandardOpenOption.READ), charset, 4096, BufferPool.localPool());
	}
	public StreamReader(ReadableByteChannel in, Charset charset) { this(in, charset, 4096, BufferPool.localPool()); }
	public StreamReader(ReadableByteChannel in, Charset charset, int buffer, BufferPool pool) {
		this(charset, pool, pool.buffer(true, buffer));
		this.in = null;
		this.in2 = in;
	}

	private StreamReader(Charset charset, BufferPool pool, DynByteBuf buf1) {
		buf = cache.getCharArray(128, false);
		cd = charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPLACE).replaceWith("�");

		this.pool = pool;
		lock = buf1;

		ib = buf1.nioBuffer();
		ib.flip();
	}

	public static StreamReader auto(File file) throws IOException {
		FileInputStream in = new FileInputStream(file);
		try {
			return auto(in);
		} catch (Throwable e) {
			in.close();
			throw e;
		}
	}
	public static StreamReader auto(InputStream in) throws IOException {
		ChineseInputStream cin = new ChineseInputStream(in);
		return new StreamReader(cin, cin.getCharset());
	}

	public String charset() { return ucs == null ? cd.charset().name() : ucs.name(); }

	public final StreamReader throwOnFail() {
		throwOnFail = true;
		return this;
	}
	public final StreamReader setForward(int extra) {
		forward = extra;
		return this;
	}

	public final int length() { return eof ? off+len : 0x70000000; }
	public final char charAt(int i) {
		fillBuffer(i);
		if (i > off+len) return 0;
		return buf[i-off];
	}
	@Nonnull
	public final CharSequence subSequence(int start, int end) {
		fillBuffer(end);
		return new CharList.Slice(buf, start-off, end-off);
	}
	public final String subSequence_String(int start, int end) {
		fillBuffer(end);
		return new String(buf, start-off, end-start);
	}

	private void fillBuffer(int i) {
		int pos = i-off;
		if (pos < 0) throw new IllegalStateException("Buffer flushed at " + off + " and you're getting " + i);

		int rem = pos - len + forward;
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
				char[] newBuf = cache.getCharArray(MathUtils.getMin2PowerOf(len+rem), false);
				System.arraycopy(buf, 0, newBuf, 0, len);
				cache.putArray(buf);
				buf = newBuf;
				ob = null;
			}

			try {
				while (rem > 0) {
					int r = fill(buf, len, rem);
					if (r < 0) break;

					len += r;
					rem -= r;
				}
			} catch (IOException e) {
				Helpers.athrow(e);
			}
		}
	}

	private int bytesRead;
	protected int fill(char[] buf, int off, int len) throws IOException {
		if (len == 0) return 0;

		int r = eof ? -1 : 0;
		ByteBuffer b = ib;

		CharBuffer ob = this.ob;
		if (ob == null || ob.array() != buf) ob = this.ob = CharBuffer.wrap(buf);
		ob.limit(off+len).position(off);

		while (true) {
			CoderResult res = cd.decode(b, ob, r < 0);
			if (res.isMalformed()) {
				if (throwOnFail) throw new IOException("在第 " + (bytesRead - b.remaining()) + " 个字节处解码失败.");
				if (b.hasRemaining()) b.get();
			}

			if (res.isOverflow() || r < 0) break;

			b.compact();

			if (in != null) {
				r = in.read(b.array(), b.arrayOffset()+b.position(), b.remaining());
				if (r >= 0) {
					b.position(b.position()+r);
					bytesRead += r;
				} else {
					eof = true;
				}
			} else {
				r = in2.read(b);
				if (r >= 0) {
					bytesRead += r;
				} else {
					eof = true;
				}
			}

			b.flip();
		}

		int provided = len-ob.remaining();
		return provided==0?-1:provided;
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

		if (in != null) in.close();
		else in2.close();
	}

	@Override
	public synchronized void finish() throws IOException {
		if (lock != this) {
			pool.reserve((DynByteBuf) lock);
			pool = null;
			lock = this;

			cache.putArray(buf);
			buf = null;
		}
	}

	@Override
	public int read() throws IOException {
		int c = fill(buf, 0, 1);
		if (c < 0) return c;
		return buf[0];
	}

	@Override
	public int read(@Nonnull char[] cbuf, int off, int len) throws IOException {
		return fill(cbuf, off, len);
	}

	@Override
	public long skip(long n) throws IOException {
		long n1 = n;
		while (n > 0) {
			int i = fill(buf, 0, buf.length);
			if (i < 0) break;
			n -= i;
		}
		return n1-n;
	}

	@Override
	public final String toString() {
		return "ReaderSeq@" + off + ":" + new String(buf,0,len);
	}
}
