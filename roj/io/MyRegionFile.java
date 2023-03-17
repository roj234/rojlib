package roj.io;

import roj.collect.MyBitSet;
import roj.io.buf.BufferPool;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.math.MathUtils;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.ByteList;

import javax.annotation.Nullable;
import java.io.*;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * @author Roj233
 * @since 2022/5/8 1:36
 */
public class MyRegionFile implements AutoCloseable {
	public static final int GZIP=1,DEFLATE=2,PLAIN=3;
	public static final int F_KEEP_TIME_IN_MEMORY = 1, F_DONT_STORE_TIME = 2, F_USE_NEW_RULE = 4;

	public final String file;
	protected volatile Source raf;

	public final int[] offsets;
	private final int[] timestamps;
	final MyBitSet free;
	int sectorCount;

	protected BufferPool pool = BufferPool.localPool();
	protected final int chunkSize;
	protected byte flag;

	public MyRegionFile(File file) throws IOException {
		this(new FileSource(file), 4096, 1024, 0);
		load();
	}

	public MyRegionFile(Source file, int chunkSize, int fileCap, int flags) {
		this.file = file.toString();
		if (chunkSize <= 0 || fileCap <= 1) throw new IllegalStateException("chunkSize="+chunkSize+",fileSize="+fileCap);
		this.chunkSize = MathUtils.getMin2PowerOf(chunkSize);

		free = new MyBitSet(fileCap);
		flag = (byte) flags;
		raf = file;
		offsets = new int[fileCap];
		timestamps = (flags & F_KEEP_TIME_IN_MEMORY) != 0 ? new int[fileCap] : null;
	}

	public final boolean isOpen() {
		return raf != null;
	}
	public void close() throws IOException {
		raf.close();
		raf = null;
	}

	public void load() throws IOException {
		Source file = raf;
		int fileCap = offsets.length;
		int header = (flag & F_DONT_STORE_TIME) != 0 ? fileCap <<2 : fileCap <<3;

		if (raf.length() < header) {
			raf.setLength(0);
			raf.setLength(header);
		}

		int bitmapMask = chunkSize - 1;
		if ((raf.length() & bitmapMask) != 0L) {
			raf.setLength((raf.length() & ~bitmapMask) + chunkSize);
		}

		int sectors = sectorCount = (int) (raf.length() / chunkSize);
		free.fill(sectors);
		free.removeRange(0, header/ chunkSize + ((header& chunkSize) == 0 ? 0 : 1));

		int headerBytes = fileCap << 2;
		ByteList tmp = IOUtil.getSharedByteBuf();

		raf.seek(0L);
		raf.readFully(tmp.list, tmp.arrayOffset(), headerBytes);
		tmp.clear();
		tmp.wIndex(headerBytes);

		for (int i = 0; i < fileCap; ++i) {
			int n = offsets[i] = tmp.readInt();
			if (n == 0) continue;

			int off = n >>> 8;
			int len = n & 255;
			if (len == 255 && off <= sectors) {
				raf.seek(off * chunkSize);
				len = chunkCount(raf.asDataInput().readInt() + 4);
			}

			if (off + len <= sectors) {
				free.removeRange(off, off + len);
			} else if (len > 0) {
				log("无效的分块: {} #{} 块范围:[{}+{}] 超出文件大小.", file, i, off, len);
			}
		}

		if (timestamps != null) {
			raf.seek(headerBytes);
			raf.readFully(tmp.list, tmp.arrayOffset(), headerBytes);
			tmp.clear();
			tmp.wIndex(headerBytes);

			for (int i = 0; i < fileCap; ++i) {
				timestamps[i] = tmp.readInt();
			}
		}
	}

	public DataInputStream getDataInput(int id) throws IOException {
		InputStream in = getData(id, null);
		return in == null ? null : new DataInputStream(in);
	}

	// holder[0]是(磁盘上的)数据长度 [1]是Unix时间戳/1000
	public InputStream getData(int id, int[] metadata) throws IOException {
		return getData1(id, raf.threadSafeCopy(), metadata);
	}

	final InputStream getData1(int id, Source raf, int[] metadata) throws IOException {
		int i = offsets[id];
		if (i == 0) return null;

		int off = i >>> 8;
		int len = i & 255;

		assert off + len < sectorCount;

		raf.seek(off*chunkSize);
		int byteLength = raf.asDataInput().readInt();
		if (byteLength > chunkSize*len - 4 && len != 255) {
			log("无效的块: {} #{} 范围:[{}+{}] 数据溢出(Block): {} > {}", file, id, off, len, byteLength, len*chunkSize - 4);
			return null;
		} else if (byteLength + off*chunkSize > raf.length()) {
			log("无效的块: {} #{} 范围:[{}+{}] 数据溢出(Global): {} > {}", file, id, off, len, byteLength + off*chunkSize, raf.length());
			return null;
		} else {
			if (byteLength <= 0) {
				log("无效的块: {} #{} 范围:[{}+{}] 长度为 {}", file, id, off, len, byteLength);
				return null;
			}
		}

		if (metadata != null) {
			metadata[0] = byteLength;
			if (timestamps != null) metadata[1] = timestamps[id];
		}

		return wrapDecoder(new SourceInputStream(raf, byteLength));
	}

	@Nullable
	public DataOutputStream getDataOutput(int id) {
		return outOfBounds(id) ? null : new DataOutputStream(new DeflaterOutputStream(new Out(id,DEFLATE)));
	}

	@Nullable
	public ManagedOutputStream getOutput(int id, int type) throws IOException {
		if (outOfBounds(id)) return null;

		Out out = new Out(id, type);
		return new ManagedOutputStream(wrapEncoder(type, out), out);
	}

	public void write(int id, int type, ByteList data) throws IOException {
		write1(id, raf, type, data, null);
	}

	final void write1(int id, Source raf, int type, ByteList data, Lock lock) throws IOException {
		int i1 = offsets[id];
		int off = i1 >>> 8;
		int oldCLen = i1 & 255;
		if (oldCLen == 255) {
			raf.seek(off*chunkSize);
			oldCLen = chunkCount(raf.asDataInput().readInt() + 4);
		}

		int cLen = chunkCount(data.readableBytes() + 5);
		if (cLen <= 0) throw new AssertionError("cLen==0");
		if (cLen > 255) {
			log("大数据: {} #{} 占用的块: {} 长度: ", file, id, cLen, data.wIndex());
		}

		w:
		try {
			if (cLen == oldCLen) {
				writeBlock(raf, off, type, data);
				break w;
			}

			if (lock != null) lock.lock();
			if (cLen < oldCLen) {
				free.addRange(off+cLen, off+oldCLen);
				if (lock != null) lock.unlock();

				writePosAndBlock(raf, id, type, data, off, cLen);
			} else {
				free.addRange(off,off+oldCLen);

				// 找到连续的可用块
				int begin = free.first();
				int len = 0;
				int i = begin;
				while (begin < free.last()) {
					int next = free.nextTrue(i);
					if (next == -1) break;
					if (next == i) {
						i++;
						len++;
					} else {
						begin = next;
						i = begin;
						len = 0;
					}

					if (len == cLen) {
						free.removeRange(begin, begin+cLen);
						if (lock != null) lock.unlock();

						writePosAndBlock(raf, id, type, data, begin, cLen);
						break w;
					}
				}

				// 在文件后增加
				raf.setLength(raf.length() + chunkSize*cLen);
				int sec = sectorCount;
				sectorCount = sec+cLen;

				if (lock != null) lock.unlock();

				writePosAndBlock(raf, id, type, data, sec, cLen);
			}
		} catch (Throwable e) {
			close();
			try {
				while (lock != null) lock.unlock();
			} catch (IllegalMonitorStateException ignored) {}
			throw e;
		}

		setTimestamp(raf, id, (int) (System.currentTimeMillis() / 1000L));
	}

	public void delete(int id) throws IOException {
		int i = offsets[id];
		if (i == 0) return;

		int off = i >>> 8;
		int len = i & 255;

		if (len == 255) {
			raf.seek(off * chunkSize);
			len = chunkCount(raf.asDataInput().readInt() + 4);
		}

		offsets[id] = 0;
		while (len-- > 0) free.add(off + len);

		setTimestamp(raf, id, (int) (System.currentTimeMillis() / 1000L));
	}

	public int copyTo(MyRegionFile target) throws IOException {
		int moved = 0;

		if (offsets.length != target.offsets.length || chunkSize != target.chunkSize)
			throw new IOException("File not compatible");

		ByteList tmp = IOUtil.getSharedByteBuf();
		for (int i = 0; i < offsets.length; i++) {
			int id = offsets[i];
			if (id == 0) continue;

			int off = id >>> 8;
			int len = id & 255;
			if (len == 255) {
				raf.seek(off * chunkSize);
				len = chunkCount(raf.asDataInput().readInt() + 4);
			}

			if (off + len <= sectorCount) {
				raf.seek(off * chunkSize);
				int dataLen = raf.asDataInput().readInt();
				if (dataLen > chunkSize * len) {
					log("无效的分块: {} #{} 块范围:[{}+{}] 数据超出分块尾: {} > {}", file, id, off, len, dataLen, len * chunkSize);
				} else if (dataLen <= 0) {
					log("无效的分块: {} #{} 块范围:[{}+{}] 长度小于0: {}", file, id, off, len, dataLen);
				} else {
					byte type = raf.asDataInput().readByte();

					tmp.clear();
					tmp.ensureCapacity(--dataLen);
					raf.readFully(tmp.list, 0, dataLen);
					tmp.wIndex(dataLen);

					target.write(i, type, tmp);
				}
			}
		}
		return moved;
	}

	public final boolean hasData(int id) {
		return offsets[id] != 0;
	}
	public final int getTimestamp(int id) {
		if (timestamps != null) return timestamps[id];
		try {
			Source raf = this.raf.threadSafeCopy();
			raf.seek((offsets.length<<2) + (id<<2));
			return raf.asDataInput().readInt();
		} catch (IOException e) {
			return 0;
		}
	}

	public void clear() throws IOException {
		int header = (flag & F_DONT_STORE_TIME) != 0 ? offsets.length : offsets.length<<1;

		Arrays.fill(offsets, 0);

		int sectors = sectorCount = (int) (raf.length() / chunkSize);
		free.fill(sectors);
		free.removeRange(0, header/chunkSize + ((header&chunkSize) == 0 ? 0 : 1));
	}

	public long memorySize() {
		return (offsets.length<<3) + (free.array().length << 3) + 192;
	}

	protected void log(String msg, Object... params) {
		Logger.getLogger("BlkFile").log(Level.ERROR, msg, null, params);
	}

	protected int chunkCount(int dataLen) {
		int i = dataLen / chunkSize;
		return ((flag & F_USE_NEW_RULE) != 0) && (dataLen&(chunkSize-1)) == 0 ? i : i+1;
	}

	protected InputStream wrapDecoder(SourceInputStream in) throws IOException {
		int type = in.read();
		switch (type) {
			case GZIP: return new GZIPInputStream(in);
			case DEFLATE: return new InflaterInputStream(in);
			case PLAIN: return in;
			default: raf.close(); throw new IOException("Unknown data type " + type);
		}
	}
	protected OutputStream wrapEncoder(int type, OutputStream out) throws IOException {
		switch (type) {
			case GZIP: return new GZIPOutputStream(out);
			case DEFLATE: return new DeflaterOutputStream(out);
			case PLAIN: return out;
			default: return null;
		}
	}

	private ByteList allocateBuffer(int i) {
		return (ByteList) pool.buffer(false, i);
	}

	final void writePosAndBlock(Source raf, int id, int type, ByteList data, int off, int len) throws IOException {
		setOffset(raf, id, (off << 8) | (len > 255 ? 255 : len));
		writeBlock(raf, off, type, data);
	}
	final void writeBlock(Source raf, int off, int type, ByteList data) throws IOException {
		raf.seek(off*chunkSize);
		DataOutput dos = raf.asDataOutput();
		dos.writeInt(data.readableBytes()+1);
		dos.writeByte(type);
		raf.write(data.array(), data.arrayOffset(), data.readableBytes());
	}

	public final boolean outOfBounds(int id) {
		return id < 0 || id > offsets.length;
	}
	final void setOffset(Source raf, int id, int offset) throws IOException {
		offsets[id] = offset;
		raf.seek(id<<2);
		raf.asDataOutput().writeInt(offset);
	}
	final void setTimestamp(Source raf, int id, int timestamp) throws IOException {
		if ((flag & F_DONT_STORE_TIME) != 0) return;
		if (timestamps != null) timestamps[id] = timestamp;
		raf.seek((offsets.length<<2) + (id<<2));
		raf.asDataOutput().writeInt(timestamp);
	}

	class Out extends OutputStream {
		final int id;
		final byte type;
		ByteList buf;

		Out(int id, int type) {
			this.id = id;
			this.type = (byte) type;
			buf = allocateBuffer(1024);
		}

		@Override
		public final void write(int b) {
			if (!buf.isWritable()) expand(1);
			buf.put((byte) b);
		}

		@Override
		public final void write(byte[] b, int off, int len) {
			if (buf.writableBytes() < len) expand(len);
			buf.put(b, off, len);
		}

		private synchronized void expand(int more) {
			buf = (ByteList) pool.expand(buf, MathUtils.getMin2PowerOf(more));
		}

		public synchronized void close() throws IOException {
			if (buf != null) {
				MyRegionFile.this.write(id, type, buf);
				pool.reserve(buf);
				buf = null;
			}
		}

		public synchronized final void fail() throws IOException {
			if (buf != null) {
				pool.reserve(buf);
				buf = null;
				close();
			}
		}
	}
	public static final class ManagedOutputStream extends FilterOutputStream {
		private final Out man;

		public ManagedOutputStream(OutputStream in, Out manager) {
			super(in);
			man = manager;
		}

		public void fail() throws IOException {
			man.fail();
		}

		public Out out() {
			return man;
		}
	}
}