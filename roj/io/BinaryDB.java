package roj.io;

import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.io.buf.BufferPool;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.util.ByteList;

import java.io.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.AbstractQueuedSynchronizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.zip.*;

/**
 * @author Roj234
 * @since 2022/10/14 0014 23:07
 */
public class BinaryDB {
	static final byte VERSION = 2;

	public final File base;
	private final RandomAccessFile index;
	private final int chunkBits, chunkMask, smallChunkSize;
	private final byte flags;

	private final IntMap<DataFile> files;
	private long nextEmpty;

	private final Lock insertLock = new ReentrantLock();
	private final Lock mapLock = new ReentrantLock();

	private final BufferPool pool;

	private int memoryRemain;
	private DataFile LRUHead, LRUTail;

	public BinaryDB(File base, int chunkBits, int smallChunkSize, int memoryMax, int flags) throws IOException {
		this.base = base;
		if (chunkBits <= 0 || chunkBits >= 28) throw new IllegalStateException();

		pool = BufferPool.localPool();

		File index = new File(base, "_.idx");
		this.index = new RandomAccessFile(index, "rw");
		this.chunkBits = chunkBits;
		this.chunkMask = (1<<chunkBits)-1;
		this.smallChunkSize = smallChunkSize;
		this.flags = (byte) flags;

		this.memoryRemain = memoryMax;

		long each = chunkMask * 10;
		files = new IntMap<>();
		if (0==index.length()) {
			updateIndex();
			return;
		}

		ByteList shared = IOUtil.getSharedByteBuf().readStreamFully(new FileInputStream(index));
		if (shared.length() != 18)
			throw new IOException("无效的索引文件 - 大小不是18");
		if (VERSION != shared.readUnsignedByte())
			throw new IOException("无效的索引文件 - 版本不匹配");
		if (chunkBits != shared.readInt())
			throw new IOException("无效的索引文件 - chunkBits不匹配");
		if (smallChunkSize != shared.readInt())
			throw new IOException("无效的索引文件 - smallChunkSize不匹配");
		if ((byte) flags != shared.readByte())
			throw new IOException("无效的索引文件 - flags不匹配");
		nextEmpty = shared.readLong();
	}

	public InputStream get(long id) throws IOException {
		return get(id, null);
	}
	public InputStream get(long id, int[] info) throws IOException {
		if (id < 0) return null;
		while (true) {
			DataFile file = loadFile1(id);

			Lock lock = file.lock;
			lock.lock();

			if (!file.isOpen()) {
				lock.unlock();
				continue;
			}

			int id1 = (int) (id & chunkMask);

			DataFile.PieceLock pLock = file.convertToPiece(id1);
			try {
				InputStream in = file._getData(id1, pLock.file, info);
				if (in != null) return new BufferedInputStream(in, 1024) {
					DataFile.PieceLock p = pLock;

					@Override
					public synchronized void close() throws IOException {
						try {
							in.close();
						} finally {
							if (p != null) {
								p.unlock();
								p = null;
							}
						}
					}
				};
			} catch (Throwable e) {
				pLock.unlock();
				throw e;
			}

			pLock.unlock();
			return null;
		}
	}

	public long getModifyTime(long id) throws IOException {
		if (id < 0) return 0;
		return (loadFile1(id).getTimestamp((int) (id & chunkMask))&0xFFFFFFFFL) * 1000;
	}

	public MyRegionFile.ManagedOutputStream insert(int type, long[] holder) throws IOException {
		insertLock.lock();
		long empty;
		try {
			empty = nextEmpty;
			nextInsertIndex();
		} finally {
			insertLock.unlock();
		}

		MyRegionFile.ManagedOutputStream out = modify(empty, type, false);
		if (null == out) throw new AssertionError("Fatal Error: lock violation");
		holder[0] = empty;
		return out;
	}

	public MyRegionFile.ManagedOutputStream modify(long id, int type, Boolean except) throws IOException {
		if (id < 0) return null;
		while (true) {
			DataFile file = loadFile1(id);

			int subId = (int) (id & chunkMask);
			if (except != null && except != file.hasData(subId)) return null;
			if (file.outOfBounds(subId)) return null;

			Lock lock = file.lock;
			lock.lock();

			// lock is locked outside
			if (file.isOpen()) {
				if (except != null && except != file.hasData(subId)) {
					lock.unlock();
					return null;
				}

				try {
					DataFile.PieceLock pLock = file.convertToPiece(subId);
					MyRegionFile.Out out = file.new Out(subId, type) {
						DataFile.PieceLock p = pLock;
						@Override
						public synchronized void close() throws IOException {
							if (buf != null) {
								file._write(id,p.file,buf,file.lock);
								BufferPool.reserve(buf);
								buf = null;
							}

							if (p != null) {
								p.unlock();
								p = null;
							}
						}
					};
					return new MyRegionFile.ManagedOutputStream(type==2?pLock.din.reset(out):file.wrapEncoder(type, out), out);
				} catch (Throwable e) {
					lock.unlock();
					throw e;
				}
			}

			lock.unlock();
		}
	}

	public boolean delete(long id) throws IOException {
		if (id < 0) return false;
		while (true) {
			DataFile file = loadFile1(id);

			int subId = (int) (id & chunkMask);
			if (!file.hasData(subId)) return false;

			Lock lock = file.lock;
			lock.lock();
			if (file.isOpen()) {
				try {
					file.delete(subId);
				} finally {
					lock.unlock();
				}

				insertLock.lock();
				if (id < nextEmpty) nextEmpty = id;
				insertLock.unlock();

				return true;
			}

			lock.unlock();
		}
	}

	public int unloadIdle(int timeout) {
		int count = 0;
		long time = System.currentTimeMillis()-timeout;

		for (DataFile file : files.values()) {
			if (file.timestamp < time) {
				memoryRemain += file.memorySize();
				mapLock.lock();
				safeClose(file);
				mapLock.unlock();
				count++;
			}
		}

		return count;
	}

	private DataFile loadFile1(long id) throws IOException {
		return loadFile((int) (id >>> chunkBits));
	}
	private DataFile loadFile(int arrId) throws IOException {
		DataFile f = files.get(arrId);
		create:
		if (f == null) {
			mapLock.lock();
			try {
				f = files.get(arrId);
				if (f != null) break create;

				f = new DataFile(storage(arrId), smallChunkSize, 1 << chunkBits, pool, flags);
				f.id = arrId;

				files.putInt(arrId, f);

				memoryRemain -= f.memorySize();
				while (memoryRemain < 0 && LRUTail != null) {
					DataFile prev = LRUTail;
					safeClose(prev);
					memoryRemain += prev.memorySize();
				}

				addFirst(f);
			} finally {
				mapLock.unlock();
			}
		} else {
			mapLock.lock();
			moveFirst(f);
			mapLock.unlock();
		}
		f.timestamp = System.currentTimeMillis();
		return f;
	}

	// 这是唯一的remove
	private void safeClose(DataFile file) {
		file.lock.lock();

		try {
			file.close();
		} catch (Throwable e) {
			e.printStackTrace();
		}

		files.remove(file.id);

		unlink(file);

		file.lock.unlock();
	}

	// LRU处理
	private void unlink(DataFile file) {
		if (file.prev != null) file.prev.next = file.next;
		else LRUHead = file.next;
		if (file.next != null) file.next.prev = file.prev;
		else LRUTail = file.prev;

		file.prev = file.next = null;
	}
	private void moveFirst(DataFile file) {
		if (file.prev != null) file.prev.next = file.next;
		else return;

		if (file.next != null) file.next.prev = file.prev;
		else LRUTail = file.prev;

		file.prev = null;
		file.next = LRUHead;
		LRUHead.prev = file;
		LRUHead = file;
	}
	private void addFirst(DataFile file) {
		if (LRUTail == null) {
			LRUHead = LRUTail = file;
		} else {
			file.next = LRUHead;
			LRUHead.prev = file;
			LRUHead = file;
		}
	}

	private void nextInsertIndex() throws IOException {
		long empty = this.nextEmpty;
		int fileId = (int) (empty >>> chunkBits);
		int id = (int) (empty & chunkMask);
		while (true) {
			DataFile file = loadFile(fileId);
			int[] arr = file.offsets;
			while (id < arr.length) {
				// 这个文件还有剩余
				if (arr[id] == 0) {
					// 未改变
					if (nextEmpty != (nextEmpty = (long)(fileId << chunkBits) | id)) {
						updateIndex();
					}
					return;
				}
				id++;
			}
			fileId++;
			id = 0;
		}
	}

	private void updateIndex() throws IOException {
		if (index.length() != 18) {
			index.setLength(18);
			index.seek(0);
			index.write(VERSION);
			index.writeInt(chunkBits);
			index.writeInt(smallChunkSize);
			index.write(flags);
		} else {
			index.seek(10);
		}

		index.writeLong(nextEmpty);
	}

	protected File storage(int i) {
		return new File(base, Integer.toString(i, 36) + ".mcr");
	}

	static final class DataFile extends MyRegionFile {
		final BufferPool pool;
		long timestamp;
		int id;

		DataFile prev, next;

		final ReentrantLock lock = new ReentrantLock(true);
		final IntMap<PieceLock> pieceLock = new IntMap<>();
		final List<PieceLock> freePieceLock = new SimpleList<>();
		static ThreadLocal<PieceLock> tmp = new ThreadLocal<>();

		public DataFile(File file, int chunkSize, int fileCap, BufferPool pool, int flags) throws IOException {
			super(new FileSource(file), chunkSize, fileCap, flags);
			this.pool = pool;
			load();
		}

		private PieceLock convertToPiece(int id) throws IOException {
			PieceLock pLock;
			try {
				pLock = pieceLock.get(id);
				if (pLock == null) {
					pLock = freePieceLock.isEmpty() ? new PieceLock(raf.threadSafeCopy()) : freePieceLock.remove(freePieceLock.size()-1);
					pieceLock.put(id, pLock);
					pLock._id = id;
				}
			} finally {
				lock.unlock();
			}

			pLock.lock();
			return pLock;
		}

		@Override
		protected InputStream wrapDecoder(SourceInputStream in) throws IOException {
			in.doClose = false;
			int type = in.read();
			switch (type) {
				case GZIP: return new GZIPInputStream(in);
				case DEFLATE: return tmp.get().iin.reset(in);
				case PLAIN: return in;
				default: raf.close(); throw new IOException("Unknown data type " + type);
			}
		}

		private static final class MyInflaterInputStream extends InflaterInputStream {
			MyInflaterInputStream(InputStream in) {
				super(in, new Inflater(), 1024);
			}

			public MyInflaterInputStream reset(InputStream in) {
				myClosed = false;
				this.in = in;
				this.inf.reset();
				return this;
			}

			@Override
			public int available() {
				return myClosed?0:1;
			}

			@Override
			public int read(byte[] b, int off, int len) throws IOException {
				len = super.read(b, off, len);
				if (len < 0) close();
				return len;
			}

			boolean myClosed;

			@Override
			public void close() throws IOException {
				if (!myClosed) {
					myClosed = true;
					in.close();
				}
			}

			Inflater inf() {
				return inf;
			}
		}
		private static final class MyDeflaterOutputStream extends DeflaterOutputStream {
			MyDeflaterOutputStream(OutputStream out) {
				super(out, new Deflater(), 1024);
			}

			public MyDeflaterOutputStream reset(OutputStream out) {
				myClosed = false;
				this.out = out;
				this.def.reset();
				return this;
			}

			public void close() throws IOException {
				if (!myClosed) {
					finish();
					out.close();
					myClosed = true;
				}
			}

			boolean myClosed;

			Deflater def() {
				return def;
			}
		}
		private final class PieceLock extends AbstractQueuedSynchronizer {
			int _id;
			final Source file;
			MyInflaterInputStream iin = new MyInflaterInputStream(new LimitInputStream(null,0));
			MyDeflaterOutputStream din = new MyDeflaterOutputStream(DummyOutputStream.INSTANCE);

			PieceLock(Source source) {
				file = source;
			}

			final void lock() {
				acquire(1);
				tmp.set(this);
			}

			final void unlock() {
				tmp.remove();
				if (!hasQueuedThreads()) {
					boolean b = false;
					try {
						b = lock.tryLock(1, TimeUnit.MILLISECONDS);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}

					if (b) {
						pieceLock.remove(_id);
						if (freePieceLock.size() < offsets.length) freePieceLock.add(this);
						lock.unlock();
					} else {
						System.out.println("unlock failed");
						/*try {
							file.close();
							iin.inf().end();
							din.def().end();
						} catch (IOException e) {
							e.printStackTrace();
						}*/
					}
				}
				release(1);
			}

			protected final boolean tryAcquire(int arg) {
				Thread t = Thread.currentThread();
				int heldCount = getState();
				if (heldCount == 0) {
					if (compareAndSetState(0, arg)) {
						setExclusiveOwnerThread(t);
						return true;
					}
				} else if (t == getExclusiveOwnerThread()) {
					// reentrant
					heldCount += arg;
					if (heldCount < 0) throw new Error("Maximum lock count exceeded");
					setState(heldCount);
					return true;
				}
				return false;
			}

			protected final boolean tryRelease(int arg) {
				int heldCount = getState()-arg;
				if (Thread.currentThread() != getExclusiveOwnerThread())
					throw new IllegalMonitorStateException();
				boolean free = false;
				if (heldCount == 0) {
					free = true;
					setExclusiveOwnerThread(null);
				}
				setState(heldCount);
				return free;
			}

			protected final boolean isHeldExclusively() {
				// While we must in general read state before owner,
				// we don't need to do so to check if current thread is owner
				return getExclusiveOwnerThread() == Thread.currentThread();
			}
		}
	}
}
