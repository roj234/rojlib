package roj.io.source;

import roj.util.DirectByteList;
import roj.util.DynByteBuf;

import java.io.DataInput;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

/**
 * 缓存: 能随大小切换内存和磁盘缓存，并在close时清空数据
 * @author Roj234
 * @since 2024/1/18 0018 19:40
 */
public class CacheSource extends Source {
	private Source source;
	private File next;
	private final int maxLength;
	private final String prefix;
	public CacheSource() { this(0, Integer.MAX_VALUE); }
	public CacheSource(int initMem, int maxMemory) { this(initMem, maxMemory, null, null); }
	public CacheSource(int initMem, int maxMemory, String prefix, File folder) {
		this.source = new MemorySource(DirectByteList.allocateDirect(initMem, maxMemory));
		this.maxLength = maxMemory;
		this.next = folder;
		this.prefix = prefix;
	}

	public int read(byte[] b, int off, int len) throws IOException { return source.read(b, off, len); }
	public void write(byte[] b, int off, int len) throws IOException {
		if (next != null && source.position()+len > maxLength) convert();
		source.write(b, off, len);
	}
	public void write(DynByteBuf data) throws IOException {
		int len = data.readableBytes();
		if (next != null && source.position()+len > maxLength) convert();
		source.write(data);
	}

	public void flush() throws IOException { source.flush(); }
	public void seek(long pos) throws IOException { if (next != null && pos > maxLength) convert(); source.seek(pos); }
	public long position() throws IOException { return source.position(); }
	public void setLength(long length) throws IOException { if (next != null && length > maxLength) convert(); source.setLength(length); }
	public long length() throws IOException { return source.length(); }
	public boolean hasChannel() { return source.hasChannel(); }
	public FileChannel channel() { return source.channel(); }
	public DynByteBuf buffer() { return source.buffer(); }
	public void close() throws IOException {
		if (source == null) return;

		source.close();
		if (next == null && prefix != null) Files.deleteIfExists(((FileSource) source).getFile().toPath());
		else ((DirectByteList) source.buffer())._free();
		source = null;
	}
	/*public void reopen() throws IOException { source.reopen(); }*/
	public DataInput asDataInput() { return source.asDataInput(); }
	public InputStream asInputStream() { return source.asInputStream(); }
	public Source threadSafeCopy() throws IOException { throw new UnsupportedOperationException("unexpected operation"); }
	public void moveSelf(long from, long to, long length) throws IOException { source.moveSelf(from, to, length); }
	public boolean isBuffered() { return source.isBuffered(); }

	private void convert() throws IOException {
		FileSource fs = new FileSource(File.createTempFile(prefix, ".tmp", next));
		DirectByteList buffer = (DirectByteList) source.buffer();
		fs.write(buffer);
		buffer._free();
		next = null;
		source = fs;
	}
}