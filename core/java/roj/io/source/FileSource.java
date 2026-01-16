package roj.io.source;

import roj.io.IOUtil;
import roj.util.DynByteBuf;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author Roj233
 * @since 2021/8/18 13:38
 */
public class FileSource extends Source {
	private final File file;
	private final long offset;
	private RandomAccessFile io;
	private final boolean write;

	public FileSource(String path) throws IOException { this(new File(path), 0); }
	public FileSource(File file) throws IOException { this(file, 0); }
	public FileSource(File file, long offset) throws IOException { this(file, offset, true); }
	public FileSource(File file, boolean write) throws IOException { this(file, 0, write); }
	public FileSource(File file, long offset, boolean write) throws IOException {
		this.write = write;
		this.file = file;
		this.offset = offset;
		reopen();
	}

	public File getFile() { return file; }

	public int read() throws IOException { return io.read(); }
	public int read(byte[] b, int off, int len) throws IOException { return io.read(b, off, len); }

	public void write(int b) throws IOException { io.write(b); }
	public void write(byte[] b, int off, int len) throws IOException { io.write(b, off, len); }
	public void write(DynByteBuf data) throws IOException {
		if (data.hasArray()) io.write(data.array(), data.relativeArrayOffset(), data.readableBytes());
		else io.getChannel().write(data.nioBuffer());
	}

	public void seek(long pos) throws IOException { io.seek(pos+offset); }
	public long position() throws IOException { return io.getFilePointer()-offset; }

	public void setLength(long length) throws IOException {
		length += offset;
		if (length < 0) throw new IOException("Negative length");
		io.setLength(length);
	}
	public long length() throws IOException { return io.length()-offset; }

	@Override
	public void put(Source src, long offset, long len) throws IOException {
		if (src instanceof FileSource fs) {
			fs.io.getChannel().transferTo(offset, len, io.getChannel());
		}
		super.put(src, offset, len);
	}

	@Override
	public void close() throws IOException { super.close(); io.close(); }
	@Override
	public void reopen() throws IOException {
		if (io != null) io.close();
		io = new RandomAccessFile(file, write ? "rw" : "r");
		io.seek(offset);
	}

	@Override public boolean isWritable() {return write;}
	@Override public DataInput asDataInput() {return io;}
	@Override public Source copy() throws IOException { return new FileSource(file, offset, write); }
	@Override public void moveSelf(long from, long to, long length) throws IOException { IOUtil.copyInternal(io.getChannel(), from, to, length); }
	@Override public String toString() { return file.getPath(); }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		FileSource source = (FileSource) o;

		if (offset != source.offset) return false;
		return file.equals(source.file);
	}

	@Override
	public int hashCode() {
		int result = file.hashCode();
		result = 31 * result + Long.hashCode(offset);
		return result;
	}
}