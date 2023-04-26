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
 * 现在，你不必将整个文件读取到内存中
 * @author Roj234
 * @since 2022/12/11 0011 9:12
 * // 注释：实测未知原因asInputStream比DynByteBuf更快.
 */
public class StreamReader extends Reader implements CharSequence, Closeable, Finishable {
	private char[] buf;
	private int off, maxOff, len;
	protected byte eof;
	protected int forward = 4;

	private final Closeable in;
	private final byte type;

	private final CharsetDecoder cd;
	private final UnsafeCharset ucs;
	private final ByteBuffer ib;
	private CharBuffer ob;

	private BufferPool pool;

	public static StreamReader auto(File file) throws IOException { return new StreamReader(file, null); }
	public static StreamReader auto(InputStream in) throws IOException { return new StreamReader(in); }

	public StreamReader(InputStream in) throws IOException { this(in, (Charset) null); }
	public StreamReader(InputStream in, String charset) throws IOException { this(in, Charset.forName(charset)); }

	public StreamReader(File in, Charset charset) throws IOException {
		this(FileChannel.open(in.toPath(), StandardOpenOption.READ), charset, 4096, BufferPool.localPool());
	}

	public StreamReader(Closeable in, Charset charset) throws IOException { this(in, charset, 4096, BufferPool.localPool()); }
	public StreamReader(Closeable in, Charset charset, int buffer, BufferPool pool) throws IOException {
		this.in = in;

		buf = ArrayCache.getDefaultCache().getCharArray(128, false);

		if (in instanceof DynByteBuf) {
			ib = ((DynByteBuf) in).nioBuffer();
			lock = in;
			type = 2;
		} else {
			DynByteBuf buf = pool.buffer(!(in instanceof InputStream), buffer);

			this.pool = pool;
			lock = buf;

			ib = buf.nioBuffer();
			ib.flip();

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
				if (type == 0) cd.buffer(ib.array(),ib.arrayOffset(),ib.capacity());
				else if (type != 2) cd.limit(ib.capacity());

				charset = Charset.forName(cd.detect());
				if (type == 0) ib.limit(cd.length());
				else if (cd.length() > 0) { // when DynByteBuf, length == 0
					ib.clear();
					ib.put(cd.buffer(), 0, cd.length()).flip();
					ArrayCache.getDefaultCache().putArray(cd.buffer());
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

	public final StreamReader throwOnFail() {
		cd.onMalformedInput(CodingErrorAction.REPORT);
		return this;
	}
	public final StreamReader setForward(int extra) {
		forward = extra;
		return this;
	}

	public final int length() { return eof<0 ? off+len : 0x70000000; }
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
		if (rem > 0 && eof==0) {
			int dt = maxOff-off;
			if (dt > 0) {
				off = maxOff;
				System.arraycopy(buf, dt, buf, 0, len-dt);
				len -= dt;
			}

			// 不到一半的话，填一半
			rem = Math.max(rem, buf.length/2 - len);

			if (buf.length < len+rem) {
				ArrayCache ac = ArrayCache.getDefaultCache();
				char[] newBuf = ac.getCharArray(MathUtils.getMin2PowerOf(len+rem), false);
				System.arraycopy(buf, 0, newBuf, 0, len);
				ac.putArray(buf);
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
		if (eof == -2) return -1;

		ByteBuffer b = ib;
		if (ucs != null) {
			DynByteBuf ba = (DynByteBuf) lock;
			int prevLen = len;
			while (eof==0) {
				long x = ucs.unsafeDecode(ba.array(),ba._unsafeAddr(),b.position(),b.limit(), buf, off, len);
				b.position((int) (x >>> 32));
				int len1 = (int) x;
				off += len - len1;
				len = len1;
				if (len == 0) break;
				read(b);
			}
			if (eof < 0) eof = -2;
			return prevLen == len ? -1 : prevLen - len;
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
		b.compact();
		int r;
		switch (type) {
			case 0:
				r = ((InputStream) in).read(b.array(), b.arrayOffset() + b.position(), b.remaining());
				if (r >= 0) {
					b.position(b.position() + r);
					bytesRead += r;
				} else eof = -1;
				break;
			case 1:
				r = ((ReadableByteChannel) in).read(b);
				if (r >= 0) bytesRead += r;
				else eof = -1;
				break;
			case 2:
			default: eof = -1; break;
		}
		b.flip();
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
		in.close();
	}

	@Override
	public synchronized void finish() throws IOException {
		if (pool != null) {
			pool.reserve((DynByteBuf) lock);
			pool = null;
		}

		if (buf != null) {
			ArrayCache.getDefaultCache().putArray(buf);
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
	public int read(@Nonnull char[] cbuf, int off, int len) throws IOException { return fill(cbuf, off, len); }
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
