package roj.archive.qz.xz;

import roj.archive.qz.xz.lz.LZDecoder;
import roj.archive.qz.xz.lzma.LZMADecoder;
import roj.archive.qz.xz.rangecoder.RangeDecoder;
import roj.collect.SimpleList;
import roj.concurrent.TaskHandler;
import roj.concurrent.TaskPool;
import roj.concurrent.task.ITask;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.io.MBInputStream;
import roj.reflect.ReflectionUtils;
import roj.util.ArrayUtil;
import roj.util.ByteList;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousCloseException;

import static roj.archive.qz.xz.LZMA2Out.COMPRESSED_SIZE_MAX;
import static roj.archive.qz.xz.LZMAInputStream.getDictSize;

public class LZMA2ParallelReader extends MBInputStream {
	private volatile boolean noMoreInput;

	private final int dictSize;
	private final byte[] presetDict;
	private final TaskHandler taskExecutor;

	private int taskRunning, doneId;

	private final SimpleList<SubDecoder> tasksFree = new SimpleList<>();
	private int taskFree, taskId;

	final class SubDecoder implements ITask {
		int id;
		final ByteList in;
		private ByteList tmpOut;

		boolean remoteNoMore;

		private final LZDecoder lz;
		private final RangeDecoder rc;
		private LZMADecoder lzma;

		private int uncompressedSize;
		private byte state;

		SubDecoder(boolean isFirst) {
			in = new ByteList();
			this.rc = new RangeDecoder(COMPRESSED_SIZE_MAX);
			this.lz = new LZDecoder(getDictSize(dictSize), isFirst?presetDict:null);
			state = LZMA2Out.DICT_RESET;
		}

		public final void execute() throws Exception {
			try {
				execute1();
			} catch (Throwable e) {
				close();
				throw e;
			}
		}
		public final void execute1() throws Exception {
			ByteList ob;
			boolean isGlobalOut;
			synchronized (tasksFree) {
				if (doneId == id) {
					isGlobalOut = true;
					ob = LZMA2ParallelReader.this.out;
				} else {
					isGlobalOut = false;
					ob = tmpOut;
					if (ob == null) ob = tmpOut = new ByteList(1048576);
				}
			}

			while (true) {
				if (uncompressedSize <= 0) {
					synchronized (in) {
						while (!in.isReadable()) {
							in.wait();
							if (noMoreInput || remoteNoMore) break;
						}
						nextChunk();
					}
					if (state == -1) break;
					assert uncompressedSize > 0;
				}

				int copySizeMax = uncompressedSize;

				if (state == LZMA2Out.STATE_LZMA) {
					lz.setLimit(copySizeMax);
					lzma.decode();
				} else {
					lz.copyUncompressed(in, copySizeMax);
				}

				int copiedSize;
				ob.ensureWritable(copySizeMax);
				copiedSize = lz.flush(ob.list, ob.wIndex());
				ob.wIndex(ob.wIndex()+copiedSize);

				if (isGlobalOut) synchronized (ob) { ob.notifyAll(); }

				uncompressedSize -= copiedSize;

				if (uncompressedSize == 0) if (!rc.isFinished() || lz.hasPending()) throw new CorruptedInputException("trailing compressed data");
			}

			if (!isGlobalOut) {
				synchronized (tasksFree) {
					while (doneId != id) tasksFree.wait();
				}

				synchronized (out) {
					//     out         in -> taskFree
					// read -> execute -> submitTask
					// limit extra memory usage

					while (out.readableBytes() > 1048576) out.wait();

					out.compact().put(ob);
					ob.clear();
					out.notifyAll();
				}
			}

			assert remoteNoMore;
			remoteNoMore = false;
			state = LZMA2Out.DICT_RESET;

			if (lzma != null) lzma.reset();
			lz.reset();
			taskFinished(this);
		}

		final void release() {
			lz.putArraysToCache();
			rc.putArraysToCache();
			in._free();
			if (tmpOut != null) tmpOut._free();
		}

		@SuppressWarnings("fallthrough")
		private void nextChunk() throws IOException {
			int control = in.readUnsignedByte();
			if (control <= 0x7F) {
				switch (control) {
					default: throw new CorruptedInputException("invalid control byte");
					case 0: state = -1; return; // End of data
					case 1: lz.reset(); break; // uncompressed with dict reset
					case 2: // uncompressed
						if (state > LZMA2Out.STATE_RESET) throw new CorruptedInputException("excepting dict reset");
				}

				state = LZMA2Out.STATE_RESET;
				uncompressedSize = in.readUnsignedShort()+1;
				return;
			}

			uncompressedSize = ((control & 0x1F) << 16) + in.readUnsignedShort() + 1;
			int cSize = in.readUnsignedShort()+1;

			switch (control >>> 5) {
				// LZMA, dict reset
				case 7: lz.reset(); readProps(); break;
				// LZMA, prop reset
				case 6:
					if (state == LZMA2Out.DICT_RESET) throw new CorruptedInputException("excepting dict reset");
					readProps();
					break;
				// LZMA, state reset
				case 5:
					if (lzma == null) throw new CorruptedInputException("unexpected state reset");
					lzma.reset();
					break;
				// LZMA
				case 4:
					if (lzma == null) throw new CorruptedInputException("unexpected LZMA state");
					break;
			}

			state = LZMA2Out.STATE_LZMA;
			rc.lzma2_manualFill(in, cSize);
		}

		private void readProps() throws IOException {
			int props = in.readUnsignedByte();

			if (props > (4 * 5 + 4) * 9 + 8) throw new CorruptedInputException("Invalid LZMA properties byte");

			int pb = props / (9 * 5);
			props -= pb * 9 * 5;
			int lp = props / 9;
			int lc = props - lp * 9;

			if (lc + lp > 4) throw new CorruptedInputException("Invalid LZMA properties byte");

			lzma = new LZMADecoder(lz, rc, lc, lp, pb);
		}
	}

	void taskFinished(SubDecoder task) {
		int i;
		synchronized (tasksFree) {
			i = --taskRunning;

			if (noMoreInput) task.release();
			else tasksFree.add(task);

			assert doneId == task.id;
			doneId++;
			tasksFree.notifyAll();
		}

		if (i == 0) {
			synchronized (out) { out.notifyAll(); }
		}
	}

	private DataInputStream in;
	private final ByteList out = new ByteList();

	public LZMA2ParallelReader(InputStream in, int dictSize) { this(in, dictSize, null, TaskPool.Common(), 10); }
	public LZMA2ParallelReader(InputStream in, int dictSize, byte[] presetDict, TaskPool taskExecutor, int affinity) {
		// Check for null because otherwise null isn't detect
		// in this constructor.
		if (in == null) throw new NullPointerException();

		this.in = new DataInputStream(in);
		this.dictSize = dictSize;
		this.presetDict = presetDict;
		this.taskExecutor = taskExecutor;
		this.taskFree = affinity;
		ReflectionUtils.u.storeFence();
		taskExecutor.submit(() -> { while (!noMoreInput) nextChunk(); });
	}

	public int read(byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		if (len == 0) return 0;

		if (in == null) throw new IOException("Stream closed");

		int initialLen = len;

		loop:
		while (true) {
			synchronized (out) {
				while (!out.isReadable()) {
					out.clear();

					if (noMoreInput) {
						synchronized (tasksFree) {
							if (taskRunning == 0) break loop;
						}
					}

					IOUtil.ioWait(this, out);
				}

				int copyLen = Math.min(out.readableBytes(), len);
				out.readFully(buf, off, copyLen);
				if (out.readableBytes() < 1048576) out.notify();

				off += copyLen;
				len -= copyLen;
			}

			if (len == 0) break;
		}

		return len == initialLen ? -1 : initialLen - len;
	}

	private SubDecoder decoder;

	private void nextChunk() throws IOException {
		int control = in.readUnsignedByte();
		if (control <= 0x7F) {
			switch (control) {
				default: throw new CorruptedInputException("invalid control byte");
				case 0: nextDecoder(); noMoreInput = true; return;
				case 2: case 1:
					ByteList decBuf = getDecoder();
					synchronized (decBuf) {
						decBuf.put(control).readStream(in, 2);
						int cSize = decBuf.readUnsignedShort(decBuf.wIndex()-2)+1;
						decBuf.readStream(in, cSize);
						decBuf.notify();
					}
				return;
			}
		}

		int myDelta = (control >>> 6) & 1;
		if (myDelta != 0) nextDecoder();

		ByteList decBuf = getDecoder();
		synchronized (decBuf) {
			decBuf.put(control).readStream(in, 4+myDelta);
			int cSize = decBuf.readUnsignedShort(decBuf.wIndex()-2-myDelta)+1;
			decBuf.readStream(in, cSize);
			decBuf.notify();
		}
	}

	private ByteList getDecoder() throws IOException {
		if (decoder == null || decoder.remoteNoMore) {
			SubDecoder task = null;

			if (!tasksFree.isEmpty()) {
				synchronized (tasksFree) {
					if (!tasksFree.isEmpty()) {
						task = tasksFree.pop();
					}
				}
			}

			if (task == null) {
				if (taskFree > 0) {
					taskFree--;
					task = new SubDecoder(decoder == null);
				} else {
					synchronized (tasksFree) {
						while (tasksFree.isEmpty()) {
							if (noMoreInput) throw new AsynchronousCloseException();

							IOUtil.ioWait(this, tasksFree);
						}
						task = tasksFree.pop();
					}
				}
			}

			task.id = taskId++;

			taskExecutor.submit(task);
			synchronized (tasksFree) { taskRunning++; }

			decoder = task;
		}
		return decoder.in;
	}
	private void nextDecoder() {
		if (decoder == null) return;
		decoder.remoteNoMore = true;
		ByteList in1 = decoder.in;
		synchronized (in1) {
			in1.compact();
			in1.put(0);
			in1.notify();
		}
	}

	public int available() throws IOException {
		if (in == null) throw new IOException("Stream closed");
		return out.readableBytes();
	}

	public void close() throws IOException {
		noMoreInput = true;

		synchronized (tasksFree) {
			while (taskRunning > 0) {
				try {
					tasksFree.wait();
				} catch (InterruptedException e) {
					break;
				}
			}
		}

		synchronized (out) { out.notifyAll(); }

		for (SubDecoder decoder : tasksFree) decoder.release();
		tasksFree.clear();

		if (in != null) {
			try {
				in.close();
			} finally {
				in = null;
			}
		}
	}
}