package roj.io;

import org.jetbrains.annotations.Nullable;
import roj.collect.BitSet;
import roj.io.buf.BufferPool;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.io.source.SourceInputStream;
import roj.math.MathUtils;
import roj.reflect.Unaligned;
import roj.text.logging.Level;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.*;
import java.util.Arrays;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj233
 * @since 2022/5/8 1:36
 */
public class MyRegionFile implements AutoCloseable {
	public static final int GZIP=1,DEFLATE=2,PLAIN=3;
	public static final int F_KEEP_TIME_IN_MEMORY = 1, F_DONT_STORE_TIME = 2, F_USE_NEW_RULE = 4, F_DONT_UPDATE_TIME = 8;

	public final String file;
	protected volatile Source raf;

	int[] offsets;
	private final int[] timestamps;
	private final BitSet free;
	private int sectorCount;

	protected final int chunkSize;
	protected byte flag;

	Source fpRead;
	static final long FPREAD_OFFSET = Unaligned.fieldOffset(MyRegionFile.class, "fpRead");
	protected Lock lock = new ReentrantLock();

	public MyRegionFile(File file) throws IOException {
		this(new FileSource(file), 4096, 1024, F_KEEP_TIME_IN_MEMORY);

		try {
			load();
		} catch (Exception e) {
			IOUtil.closeSilently(raf);
			throw e;
		}
	}

	public MyRegionFile(Source file, int chunkSize, int fileCap, int flags) throws IOException {
		this.file = file.toString();
		if (chunkSize <= 0 || fileCap <= 1) throw new IllegalStateException("chunkSize="+chunkSize+",fileSize="+fileCap);
		this.chunkSize = MathUtils.getMin2PowerOf(chunkSize);

		free = new BitSet(fileCap);
		flag = (byte) flags;
		raf = file;
		offsets = new int[fileCap];
		timestamps = (flags & F_KEEP_TIME_IN_MEMORY) != 0 ? new int[fileCap] : null;

		try {
			Source raf = this.raf;
			int header = (flag & F_DONT_STORE_TIME) != 0 ? fileCap<<2 : fileCap<<3;

			if (raf.length() < header) {
				raf.setLength(0);
				raf.setLength(header);
			}

			int bitmapMask = chunkSize - 1; // align
			if ((raf.length() & bitmapMask) != 0L) {
				raf.setLength((raf.length() & ~bitmapMask) + chunkSize);
			}

			int sectors = sectorCount = (int) (raf.length() / chunkSize);
			free.fill(sectors);
			free.removeRange(0, header/chunkSize + ((header&chunkSize) == 0 ? 0 : 1));
		} catch (Exception e) {
			IOUtil.closeSilently(file);
			throw e;
		}
	}

	public final boolean isOpen() {
		return raf != null;
	}
	public void close() throws IOException {
		IOUtil.closeSilently(raf);
		IOUtil.closeSilently(fpRead);
		raf = null;
		fpRead = null;
	}

	public void load() throws IOException {
		int fileCap = offsets.length;
		int header = (flag & F_DONT_STORE_TIME) != 0 ? fileCap<<2 : fileCap<<3;
		int sectors = sectorCount = (int) (raf.length() / chunkSize);
		free.fill(sectors);
		free.removeRange(0, header/chunkSize + ((header&chunkSize) == 0 ? 0 : 1));

		int headerBytes = fileCap << 2;
		ByteList tmp = IOUtil.getSharedByteBuf();

		raf.seek(0L);
		raf.readFully(tmp, headerBytes);

		for (int i = 0; i < fileCap; ++i) {
			int n = offsets[i] = tmp.readInt();
			if (n == 0) continue;

			int off = n >>> 8;
			int len = n & 255;
			if (len == 255 && off <= sectors) {
				raf.seek((long) off * chunkSize);
				len = chunkCount(raf.asDataInput().readInt() + 4);
			}

			if (off + len <= sectors) {
				free.removeRange(off, off + len);
			} else if (len > 0) {
				log("无效的分块: {} #{} 块范围:[{}+{}] 超出文件大小.", file, i, off, len);
			}
		}

		if (timestamps != null) {
			tmp.clear();
			raf.seek(headerBytes);
			raf.readFully(tmp, headerBytes);

			for (int i = 0; i < fileCap; ++i) {
				timestamps[i] = tmp.readInt();
			}
		}
	}

	public MyDataInputStream getBufferedInputStream(int id) throws IOException {
		var in = getInputStream(id, null);
		return in == null ? null : new MyDataInputStream(in);
	}
	// holder[0]是(磁盘上的)数据长度 [1]是Unix时间戳/1000
	public InputStream getInputStream(int id) throws IOException {return _in(id, null);}
	public InputStream getInputStream(int id, int[] metadata) throws IOException {return _in(id, metadata);}
	private InputStream _in(int id, int[] metadata) throws IOException {
		var in = getRawdata(id, metadata);
		if (in == null) return null;
		return wrapDecoder(in);
	}
	public SourceInputStream getRawdata(int id, int[] metadata) throws IOException {
		int i = offsets[id];
		if (i == 0) return null;

		int off = i >>> 8;
		int len = i & 255;

		//assert off + len < sectorCount;

		Source src = getUnsharedSource();

		src.seek((long) off * chunkSize);
		int byteLength = src.asDataInput().readInt();
		if (byteLength > chunkSize*len - 4 && len != 255) {
			log("无效的块: {} #{} 范围:[{}+{}] 数据溢出(Block): {} > {}", file, id, off, len, byteLength, len*chunkSize - 4);
			return null;
		} else if (byteLength + (long) off * chunkSize > src.length()) {
			log("无效的块: {} #{} 范围:[{}+{}] 数据溢出(Global): {} > {}", file, id, off, len, byteLength + off*chunkSize, src.length());
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
		return new SourceInputStream.Shared(src, byteLength, this, FPREAD_OFFSET);
	}

	private Source getUnsharedSource() throws IOException {
		Source src = (Source) U.getAndSetObject(this, FPREAD_OFFSET, null);
		if (src == null) src = raf.threadSafeCopy();
		return src;
	}
	private void putUnsharedSource(Source raf) throws IOException {
		if (!U.compareAndSwapObject(this, FPREAD_OFFSET, null, raf))
			raf.close();
	}

	@Nullable
	public DataOutputStream getDataOutput(int id) { return outOfBounds(id) ? null : new DataOutputStream(new DeflaterOutputStream(new Out(id,DEFLATE))); }
	@Nullable
	public CancellableOutputStream getOutputStream(int id, int type) throws IOException {
		if (outOfBounds(id)) return null;
		Out out = new Out(id, type);
		return new CancellableOutputStream(wrapEncoder(type, out), out);
	}

	public void write(int id, DynByteBuf data) throws IOException {
		int cLen = chunkCount(data.readableBytes() + 5);
		if (cLen <= 0) throw new AssertionError("cLen==0");
		if (cLen >= 255) log("大数据: {} #{} 占用的块: {} 长度: ", file, id, cLen, data.wIndex());

		var raf = getUnsharedSource();

		int i1 = offsets[id];
		int off = i1 >>> 8;
		int oldCLen = i1 & 255;
		if (oldCLen == 255) {
			raf.seek((long) off * chunkSize);
			oldCLen = chunkCount(raf.asDataInput().readInt() + 4);
		}

		finish:
		try {
			if (cLen == oldCLen) {
				writeBlock(raf, off, data);
				break finish;
			}

			lock.lock();
			_found:
			try {
				if (cLen < oldCLen) {
					free.addRange(off+cLen, off+oldCLen);
					break _found;
				}

				free.addRange(off, off+oldCLen);

				// 找到连续的可用块
				off = 0;
				while (true) {
					off = free.nextTrue(off);
					if (off < 0) break;
					if (!free.allTrue(off,off+cLen)) {
						off++;
						continue;
					}

					free.removeRange(off, off+cLen);

					writePosAndBlock(raf, id, data, off, cLen);
					break _found;
				}

				int lastAvailBlock = (int) (raf.length() / chunkSize);
				while (lastAvailBlock > 0 && free.remove(lastAvailBlock-1)) lastAvailBlock--;

				// 在文件后增加
				int extraBlocks = cLen + lastAvailBlock;

				raf.setLength((long) chunkSize * extraBlocks);

				off = sectorCount;
				sectorCount = extraBlocks;
			} finally {
				lock.unlock();
			}

			writePosAndBlock(raf, id, data, off, cLen);
		} catch (Exception e) {
			putUnsharedSource(raf);
			throw e;
		}

		if ((flag & F_DONT_UPDATE_TIME) == 0) {
			setTimestamp(raf, id, (int) (System.currentTimeMillis() / 1000L));
		}
		putUnsharedSource(raf);
	}

	public void delete(int id) throws IOException {
		if (outOfBounds(id)) return;
		int i = U.getAndSetInt(offsets, Unaligned.ARRAY_INT_BASE_OFFSET + id, 0);
		if (i == 0) return;

		int off = i >>> 8;
		int len = i & 255;

		lock.lock();
		try {
			if (len == 255) {
				raf.seek((long) off * chunkSize);
				len = chunkCount(raf.asDataInput().readInt() + 4);
			}

			setOffset(raf, id, 0);
			free.addRange(off, off+len);

			if ((flag & F_DONT_UPDATE_TIME) == 0) {
				setTimestamp(raf, id, (int) (System.currentTimeMillis() / 1000L));
			}
		} finally {
			lock.unlock();
		}
	}

	public int copyTo(MyRegionFile target) throws IOException {
		int moved = 0;

		if (offsets.length != target.offsets.length || chunkSize != target.chunkSize)
			throw new IOException("Incompatible regions");

		boolean channel = raf.hasChannel() && target.raf.hasChannel();
		ByteList tmp = IOUtil.getSharedByteBuf();
		for (int i = 0; i < offsets.length; i++) {
			/*if (!hasData(i)) continue;
			if (channel) {

			} else */{
				var in = getRawdata(i, null);
				if (in == null) continue;

				try {
					tmp.clear();
					tmp.readStreamFully(in, false);
				} finally {
					IOUtil.closeSilently(in);
				}
				target.write(i, tmp);
			}

			moved++;
		}
		return moved;
	}

	public boolean copyOneTo(MyRegionFile target, int i) throws IOException {
		if (offsets.length != target.offsets.length || chunkSize != target.chunkSize)
			throw new IOException("Incompatible regions");

		var in = getRawdata(i, null);
		if (in == null) return false;

		ByteList tmp = IOUtil.getSharedByteBuf();
		tmp.readStreamFully(in);
		target.write(i, tmp);
		return true;
	}

	public final boolean hasData(int id) {return offsets[id] != 0;}
	public final int getTimestamp(int id) {
		if (timestamps != null) return timestamps[id];
		try {
			Source raf = getUnsharedSource();
			raf.seek((long) (offsets.length + id) <<2);
			int time = raf.asDataInput().readInt();
			putUnsharedSource(raf);
			return time;
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

	public int memorySize() {
		return (offsets.length<<(timestamps==null?2:3)) + (free.array().length << 3) + 192;
	}

	protected void log(String msg, Object... params) {Logger.getLogger("RegionFile").log(Level.ERROR, msg, null, params);}

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
			default: in.close(); throw new IOException("Unknown data type " + type);
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

	private void writePosAndBlock(Source raf, int id, DynByteBuf data, int off, int len) throws IOException {
		setOffset(raf, id, (off << 8) | (len > 255 ? 255 : len));
		writeBlock(raf, off, data);
	}
	private void writeBlock(Source raf, int off, DynByteBuf data) throws IOException {
		raf.seek((long) off * chunkSize);
		raf.writeInt(data.readableBytes());
		raf.write(data);
	}

	public final boolean outOfBounds(int id) {return id < 0 || id > offsets.length;}
	private void setOffset(Source raf, int id, int offset) throws IOException {
		offsets[id] = offset;
		raf.seek((long) id << 2);
		raf.writeInt(offset);
	}
	public final void setTimestamp(int id, int timestamp) throws IOException {setTimestamp(raf, id, timestamp);}
	private void setTimestamp(Source raf, int id, int timestamp) throws IOException {
		if ((flag & (F_DONT_STORE_TIME|F_DONT_UPDATE_TIME)) != 0) return;
		if (timestamps != null) timestamps[id] = timestamp;
		raf.seek((long) (offsets.length + id) << 2);
		raf.writeInt(timestamp);
	}

	final class Out extends OutputStream {
		final int id;
		DynByteBuf buf;

		Out(int id, int type) {
			this.id = id;
			buf = BufferPool.buffer(false, 1024).put(type);
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
			buf = BufferPool.localPool().expand(buf, MathUtils.getMin2PowerOf(more));
		}

		public synchronized void close() throws IOException {
			if (buf != null) {
				MyRegionFile.this.write(id, buf);
				BufferPool.reserve(buf);
				buf = null;
			}
		}

		public synchronized final void cancel() throws IOException {
			if (buf != null) {
				BufferPool.reserve(buf);
				buf = null;
				close();
			}
		}
	}
	public static final class CancellableOutputStream extends FilterOutputStream {
		private final Out man;
		CancellableOutputStream(OutputStream in, Out manager) {super(in);man = manager;}
		public void cancel() throws IOException {man.cancel();}
	}
}