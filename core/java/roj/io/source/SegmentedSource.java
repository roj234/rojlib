package roj.io.source;

import roj.util.DynByteBuf;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2025/1/17 13:50
 */
public class SegmentedSource extends Source {
	private final Source source;
	private final long offset, length;
	private final boolean writable;
	private long pos;

	public SegmentedSource(Source source, long length) throws IOException {this(source, 0, length, true);}
	public SegmentedSource(Source source, long offset, long length) throws IOException {this(source, offset, length, true);}
	public SegmentedSource(Source source, long offset, long length, boolean writable) throws IOException {
		this.writable = writable;
		this.source = source;
		this.offset = offset;
		this.length = length;
		seek(0);
	}

	public int read() throws IOException {
		if (pos >= length) return -1;
		int tmp = source.read();
		if (tmp > 0) pos++;
		return tmp;
	}
	public int read(byte[] b, int off, int len) throws IOException {
		if (pos >= length) return -1;
		len = (int) Math.min(len, length-pos);
		len = source.read(b, off, len);
		if (len > 0) pos += len;
		return len;
	}

	public void write(int b) throws IOException {ensureWritable(1);source.write(b);pos++;}
	public void write(byte[] b, int off, int len) throws IOException {ensureWritable(len);source.write(b, off, len);pos += len;}
	public void write(DynByteBuf data) throws IOException {
		int len = data.readableBytes();
		ensureWritable(len);
		source.write(data);
		pos += len;
	}
	private void ensureWritable(int i) throws IOException {
		if (!writable) throw new IOException("Not writable");
		if (pos >= length && i > 0) throw new IOException("Cannot call setSize() on SegmentSource");
	}

	public void seek(long pos) throws IOException {
		source.seek(pos+offset);
		this.pos = pos;
	}
	public long position() { return pos; }

	public void setLength(long length) throws IOException {throw new IOException("Cannot call setLength() on SomeSource");}
	public long length() throws IOException { return length; }

	@Override public void close() throws IOException {super.close();source.close();}
	@Override public void reopen() throws IOException {source.reopen();seek(pos);}

	@Override public boolean isWritable() {return writable && source.isWritable();}
	@Override public Source copy() throws IOException { return new SegmentedSource(source.copy(), offset, length, writable); }
	@Override public void moveSelf(long from, long to, long length) throws IOException {throw new UnsupportedOperationException();}
	@Override public String toString() { return "SegmentedSource{"+source+"}"; }
}