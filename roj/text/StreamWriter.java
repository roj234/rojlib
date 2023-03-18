package roj.text;

import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * @author Roj234
 * @since 2023/3/18 0018 0:11
 */
public class StreamWriter extends CharList implements Appender, AutoCloseable, Finishable {
	private final OutputStream out;

	private final CharsetEncoder ce;
	private final ByteBuffer ob;
	private CharBuffer ib;

	private BufferPool pool;
	private final DynByteBuf buf1;

	private final ArrayCache cache = ArrayCache.getDefaultCache();

	public static StreamWriter to(File file) throws FileNotFoundException {
		return new StreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
	}
	public static StreamWriter to(File file, Charset cs) throws FileNotFoundException {
		return new StreamWriter(new FileOutputStream(file), cs);
	}

	public StreamWriter(OutputStream out, Charset charset) {
		this(out,charset,BufferPool.localPool());
	}
	public StreamWriter(OutputStream out, Charset cs, BufferPool pool) {
		this.list = cache.getCharArray(512, false);
		this.out = out;

		ce = cs == StandardCharsets.UTF_8 ? null : cs.newEncoder().onUnmappableCharacter(CodingErrorAction.REPORT);

		this.pool = pool;
		this.buf1 = pool.buffer(false, 1024);

		ob = buf1.nioBuffer();
	}

	@Override
	public void ensureCapacity(int cap) {
		if (list == null) Helpers.athrow(new IOException("Stream closed"));

		if (cap > list.length) {
			try {
				flush();
			} catch (IOException e) {
				Helpers.athrow(e);
			}

			if (list.length-len < cap) {
				char[] newArray = cache.getCharArray(cap, false);
				System.arraycopy(list, 0, newArray, 0, len);
				cache.putArray(list);
				list = newArray;
				ib = null;
			}
		}
	}

	public final void write(String str) throws IOException { write(str, 0, str.length()); }
	public final void write(String str, int off, int len) throws IOException {
		flush();
		len += off;
		while (off < len) {
			int myLen = Math.min(list.length, len-off);
			str.getChars(off, myLen, list, 0);
			this.len = myLen;
			flush();
			off += len;
		}
	}

	public final CharList append(char[] c, int start, int end) {
		try {
			flush();
			if (ce == null) IOUtil.SharedCoder.get().encodeTo(c,start,end-start,out);
			else writeBuf(false, CharBuffer.wrap(c, start, end-start));
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return this;
	}

	public final CharList append(CharSequence cs, int start, int end) {
		if (cs instanceof CharList) return append((CharList) cs, start, end);
		if (cs instanceof Slice) return super.append(cs, start, end);

		char[] ch = list;
		int j = len;
		for (int i = start; i < end; i++) {
			if (j == list.length) {
				len = j;
				try {
					flush();
				} catch (IOException e) {
					Helpers.athrow(e);
				}
				j = 0;
			}

			ch[j++] = cs.charAt(i);
		}
		len = j;

		return this;
	}

	public void flush() throws IOException {
		if (len == 0) return;
		if (ce == null) {
			IOUtil.SharedCoder.get().encodeTo(list, 0, len, out);
			len = 0;
			return;
		}

		if (ib == null) ib = CharBuffer.wrap(list);
		ib.position(0).limit(len);

		writeBuf(false, ib);
	}

	private void writeBuf(boolean EOF, CharBuffer ib) throws IOException {
		while (true) {
			ob.clear();
			CoderResult cr = ce.encode(ib, ob, EOF);
			if (cr.isUnmappable()) throw new IOException(ce + " cannot encode");

			out.write(ob.array(),ob.arrayOffset(),ob.position());

			if (cr.isUnderflow()) {
				ib.compact().flip();
				len = ib.limit();
				break;
			}
		}

		if (EOF) {
			ob.clear();
			ce.flush(ob);
			if (ob.position() > 0)
				out.write(ob.array(),ob.arrayOffset(),ob.position());
		}
	}

	@Override
	public synchronized void finish() throws IOException {
		if (pool != null) {
			flush();

			if (ce != null) {
				if (ib == null) ib = CharBuffer.allocate(0);
				writeBuf(true, ib);
			}

			pool.reserve(buf1);
			pool = null;

			cache.putArray(list);
			list = null;
		}
	}

	@Override
	public synchronized void close() throws IOException {
		try {
			finish();
		} finally {
			out.close();
		}
	}
}
