package roj.io.source;

import roj.io.IOUtil;
import roj.io.SourceInputStream;
import roj.util.DynByteBuf;

import java.io.*;
import java.nio.channels.FileChannel;

/**
 * @author Roj233
 * @since 2021/8/18 13:38
 */
public class FileSource extends Source {
	private final File source;
	private final long offset;
	private RandomAccessFile file;

	public FileSource(String path) throws IOException {
		this(new File(path), 0);
	}

	public FileSource(File file, long offset) throws IOException {
		this.file = new RandomAccessFile(file, "rw");
		this.source = file;
		this.offset = offset;
		this.file.seek(offset);
	}

	public FileSource(File file) throws IOException {
		this(file, 0);
	}

	public File getSource() {
		return source;
	}

	public int read() throws IOException {
		return file.read();
	}
	public int read(byte[] b, int off, int len) throws IOException {
		return file.read(b, off, len);
	}

	public void write(int b) throws IOException {
		file.write(b);
	}
	public void write(byte[] b, int off, int len) throws IOException {
		file.write(b, off, len);
	}
	public void write(DynByteBuf data) throws IOException {
		if (data.hasArray()) {
			file.write(data.array(), data.arrayOffset() + data.rIndex, data.readableBytes());
		} else {
			file.getChannel().write(data.nioBuffer());
		}
		data.rIndex = data.wIndex();
	}

	public void seek(long pos) throws IOException {
		file.seek(pos + offset);
	}
	public long position() throws IOException {
		return file.getFilePointer() - offset;
	}

	public void setLength(long length) throws IOException {
		if (length < 0) throw new IOException();
		file.setLength(length + offset);
	}
	public long length() throws IOException {
		return file.length() - offset;
	}

	public boolean hasChannel() {
		return true;
	}
	public FileChannel channel() {
		return file.getChannel();
	}

	@Override
	public void close() throws IOException {
		file.close();
	}
	@Override
	public void reopen() throws IOException {
		if (file != null) file.close();
		file = new RandomAccessFile(source, "rw");
	}

	public DataInput asDataInput() {
		return file;
	}
	public DataOutput asDataOutput() {
		return file;
	}
	public InputStream asInputStream() {
		try {
			return new SourceInputStream(this, file.length() - offset);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Source threadSafeCopy() throws IOException {
		return new FileSource(source, offset);
	}

	@Override
	public void moveSelf(long from, long to, long length) throws IOException {
		IOUtil.transferFileSelf(channel(), from, to, length);
	}

	@Override
	public String toString() {
		return source.getPath();
	}
}