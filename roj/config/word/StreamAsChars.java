package roj.config.word;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.*;

/**
 * 现在，你不必将整个文件读取到内存中——相反，你只需要持有2KB的缓冲区
 * @author Roj234
 * @since 2022/12/11 0011 9:12
 */
public class StreamAsChars extends ReadOnDemand {
	private final InputStream in;
	private final ByteBuffer ib;
	private final CharsetDecoder cd;

	private CharBuffer ob;
	private char[] prevOb;

	protected int length;

	public static StreamAsChars from(File in) throws IOException {
		return new StreamAsChars(new FileInputStream(in), StandardCharsets.UTF_8);
	}
	public static StreamAsChars from(File in, Charset cs) throws IOException {
		return new StreamAsChars(new FileInputStream(in), cs);
	}

	public StreamAsChars(InputStream in) {
		this(in, StandardCharsets.UTF_8, 1024);
	}
	public StreamAsChars(InputStream in, Charset cs) {
		this(in, cs, 1024);
	}
	public StreamAsChars(InputStream in, String cs) {
		this(in, Charset.forName(cs), 1024);
	}
	public StreamAsChars(InputStream in, Charset cs, int readBuffer) {
		this.in = in;
		cd = cs.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPLACE);
		ib = ByteBuffer.allocate(readBuffer);
		ib.flip();
	}

	@Override
	protected int fillIn(char[] list, int off, int len) throws IOException {
		if (len == 0) return 0;

		int r = eof ? -1 : 0;
		ByteBuffer b = ib;
		CharBuffer ob = createCharBuffer(list,off,len);

		while (true) {
			CoderResult res = cd.decode(b, ob, r < 0);
			if (res.isMalformed()) throw new IOException("Malformed input near " + length);

			if (res.isOverflow() || r < 0) break;

			b.compact();
			r = in.read(b.array(), b.position(), b.remaining());
			if (r >= 0) {
				b.position(b.position()+r);
				length += r;
			} else {
				eof = true;
				in.close();
			}
			b.flip();
		}

		int provided = len-ob.remaining();
		return provided==0?-1:provided;
	}

	@Override
	public void close() throws IOException {
		eof = true;
		in.close();
	}

	private CharBuffer createCharBuffer(char[] list, int off, int len) {
		CharBuffer ob = this.ob;
		if (prevOb != list) {
			prevOb = list;
			ob = this.ob = CharBuffer.wrap(list);
		}
		ob.limit(off+len).position(off);
		return ob;
	}
}
