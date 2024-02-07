package roj.text;

import roj.io.Finishable;
import roj.io.buf.BufferPool;
import roj.util.ArrayCache;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.*;
import java.nio.file.StandardOpenOption;

/**
 * @author Roj234
 * @since 2023/3/18 0018 0:11
 */
public class TextWriter extends CharList implements Appender, AutoCloseable, Finishable {
	private final Closeable out;
	private final byte type;

	private final CharsetEncoder ce;
	private final UnsafeCharset ucs;
	private final ByteBuffer ob;
	private CharBuffer ib;

	private DynByteBuf buf1;

	public static TextWriter to(File file) throws IOException { return to(file, StandardCharsets.UTF_8); }
	public static TextWriter to(File file, Charset cs) throws IOException {
		return new TextWriter(FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING), cs);
	}
	public static TextWriter append(File file) throws IOException { return append(file, StandardCharsets.UTF_8); }
	public static TextWriter append(File file, Charset cs) throws IOException {
		return new TextWriter(FileChannel.open(file.toPath(), StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.APPEND), cs);
	}
	public static void write(File file, CharSequence str) throws IOException {
		try (TextWriter tw = to(file)) { tw.append(str); }
	}

	public TextWriter(Closeable out, Charset charset) {
		this(out,charset,BufferPool.localPool());
	}
	public TextWriter(Closeable out, Charset cs, BufferPool pool) {
		if (out instanceof DynByteBuf) {
			type = 2;
		} else if (out instanceof OutputStream) {
			type = 0;
		} else if (out instanceof WritableByteChannel) {
			type = 1;
		} else {
			throw new IllegalArgumentException("无法确定 " + out.getClass().getName() + " 的类型");
		}

		if (cs == null) cs = TextUtil.DefaultOutputCharset;

		if (StandardCharsets.UTF_8 == cs) {
			ucs = UTF8MB4.CODER;
			ce = null;
		} else if (GB18030.is(cs)) {
			ucs = GB18030.CODER;
			ce = null;
		} else {
			ucs = null;
			ce = cs.newEncoder().onUnmappableCharacter(CodingErrorAction.REPORT);
		}

		buf1 = pool.allocate(!(out instanceof OutputStream), 1024);

		ob = buf1.nioBuffer();
		ob.clear();

		this.list = ArrayCache.getCharArray(512, false);
		this.out = out;
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
				char[] newArray = ArrayCache.getCharArray(cap, false);
				System.arraycopy(list, 0, newArray, 0, len);
				ArrayCache.putArray(list);
				list = newArray;
				ib = null;
			}
		}
	}

	private CharBuffer excb;
	public final CharList append(char[] c, int start, int end) {
		try {
			flush();
			if (excb == null || excb.array() != c) excb = CharBuffer.wrap(c, start, end-start);
			else excb.limit(end).position(start);
			encode(false, excb);
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return this;
	}

	@Override
	public final CharList append(String s, int start, int end) {
		try {
			flush();
			char[] ch = list;
			while (start < end) {
				len = Math.min(end-start, ch.length);
				s.getChars(start, start+len, ch, 0);
				if (len < ch.length) break;

				start += len;
				flush();
			}
		} catch (IOException e) {
			Helpers.athrow(e);
		}
		return this;
	}

	public final CharList append(CharSequence cs, int start, int end) {
		Class<?> c = cs.getClass();
		if (c == CharList.class) return append((CharList) cs, start, end);
		if (c == String.class) return append(cs.toString(), start, end);
		if (c == CharList.Slice.class) super.append(cs, start, end);

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
		if (len > 0) {
			if (ib == null) ib = CharBuffer.wrap(list);
			ib.position(0).limit(len);

			encode(false, ib);
		}
		flush_ce();
	}

	private void encode(boolean EOF, CharBuffer ib) throws IOException {
		if (ucs != null) {
			if (type == 2) {
				ucs.encodeFully(ib, (DynByteBuf) out);
			} else {
				int i = 0;
				while (i < ib.length()) {
					i = ucs.encodeFixedOut(ib, i, ib.length(), buf1, buf1.writableBytes());

					ob.position(buf1.wIndex());
					if (buf1.writableBytes() < 8) flush_ce();
				}
			}

			len = 0;
			return;
		}

		while (true) {
			CoderResult cr = ce.encode(ib, ob, false);
			if (cr.isUnmappable()) throw new IOException(ce + " cannot encode");

			if (cr.isUnderflow()) {
				ib.compact().flip();
				len = ib.limit();
				break;
			}

			if (cr.isOverflow()) flush_ce();
		}

		if (EOF) {
			CoderResult cr = ce.encode(ib, ob, true);
			do {
				flush_ce();
			} while (!ce.flush(ob).isUnderflow());
			flush_ce();
		}
	}
	private void flush_ce() throws IOException {
		buf1.clear();
		if (ob.position() > 0) {
			switch (type) {
				case 2:
				case 0: ((OutputStream) out).write(ob.array(),ob.arrayOffset(),ob.position()); break;
				case 1: ob.flip(); ((WritableByteChannel) out).write(ob); break;
			}
		}
		ob.clear();
	}

	@Override
	public synchronized void finish() throws IOException {
		if (list != null) {
			flush();

			if (ce != null) {
				encode(true, ib == null ? CharBuffer.wrap(ArrayCache.CHARS) : ib);
			}

			if (buf1 != null) {
				BufferPool.reserve(buf1);
				buf1 = null;
			}

			ArrayCache.putArray(list);
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