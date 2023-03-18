package roj.io.source;

import roj.util.DynByteBuf;

import java.io.DataInput;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ReadOnlyBufferException;

/**
 * @author Roj234
 * @since 2023/5/26 0026 18:44
 */
public class ReadonlySource extends Source {
	private final Source s;

	public ReadonlySource(Source s) { this.s = s; }

	public int read(byte[] b, int off, int len) throws IOException { return s.read(b, off, len); }

	public void write(byte[] b, int off, int len) { throw new ReadOnlyBufferException(); }
	public void write(DynByteBuf data) { throw new ReadOnlyBufferException(); }

	public void seek(long pos) throws IOException { s.seek(pos); }
	public long position() throws IOException { return s.position(); }
	public void setLength(long length) throws IOException {
		if (length != s.length()) throw new ReadOnlyBufferException();
	}
	public long length() throws IOException { return s.length(); }

	public DataInput asDataInput() { return s.asDataInput(); }
	public InputStream asInputStream() { return s.asInputStream(); }
	public Source threadSafeCopy() throws IOException { return this; }

	public void moveSelf(long from, long to, long length) { throw new ReadOnlyBufferException(); }
}
