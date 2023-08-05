package roj.archive.qz.xz;

import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.concurrent.TaskHandler;
import roj.concurrent.task.ITask;
import roj.io.Finishable;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;

import static roj.archive.qz.xz.LZMA2Options.*;

class LZMA2ParallelWriter extends OutputStream implements Finishable {
	private volatile byte closed;

	private OutputStream out;
	private final LZMA2Options options;

	private final byte[] buf;
	private int bufPos;
	private final int bufLen;

	private final TaskHandler taskExecutor;

	private final IntMap<SubEncoder> tasksDone = new IntMap<>();
	private int taskRunning, doneId;

	private final SimpleList<SubEncoder> tasksFree = new SimpleList<>();
	private int taskFree, taskId;

	private final Object taskLock = new Object();
	private final byte dictionaryMode;

	final class SubEncoder extends LZMA2Out implements ITask {
		int id;
		byte[] in;

		SubEncoder() {
			super(options);

			// 6 / 65536 ~= 1 / 10000
			int mySize = bufLen;
			if (dictionaryMode == ASYNC_DICT_SET) mySize -= options.getDictSize();
			in = ArrayCache.getByteArray(Math.max(Math.round(mySize * 1.0002f), mySize), false);
			out = ByteList.wrapWrite(in, 0, in.length);

		}

		@SuppressWarnings("fallthrough")
		public final void execute() throws Exception {
			int off, len;
			if (dictionaryMode == ASYNC_DICT_ASYNCSET) {
				off = options.getDictSize();
				len = pendingSize -= off;
			} else {
				off = 0;
				len = pendingSize;
			}

			if (id == 0) {
				byte[] presetDict = options.getPresetDict();
				if (presetDict != null && presetDict.length > 0) {
					lz.setPresetDict(options.getDictSize(), presetDict);
					state = PROP_RESET;
				} else {
					state = DICT_RESET;
				}
			} else {
				switch (dictionaryMode) {
					case ASYNC_DICT_NONE: state = DICT_RESET; break;
					case ASYNC_DICT_ASYNCSET: lz.setPresetDict(off, in, 0, off);
					case ASYNC_DICT_SET: state = STATE_RESET; break;
				}
			}

			try {
				while (len > 0) {
					int used = lz.fillWindow(in, off, len);
					off += used;
					len -= used;

					if (lzma.encodeForLZMA2()) writeChunk();
				}

				lz.setFinishing();
				while (pendingSize > 0) {
					lzma.encodeForLZMA2();
					writeChunk();
				}

				lz.reuse();
				lzma.reset();

				taskFinished(this);
			} catch (Throwable e) {
				try {
					close();
				} catch (Throwable ignored) {}
				throw e;
			}
		}

		final void release() {
			lzma.putArraysToCache();
			rc.putArraysToCache();
			ArrayCache.putArray(in);
		}

		final void setDictionary(byte[] buf, int i, int len) { lz.setPresetDict(options.getDictSize(), buf, i, len); }
		final void write() throws IOException {
			ByteList b = (ByteList) out;
			b.writeToStream(LZMA2ParallelWriter.this.out);
			b.clear();
		}
	}

	LZMA2ParallelWriter(OutputStream out, LZMA2Options options) {
		if (out == null) throw new NullPointerException();

		this.out = out;
		int dictSize = options.getDictSize();

		int blockSize = options.getAsyncBlockSize();
		if (blockSize == 0) {
			final int kMinSize = 1 << 20, kMaxSize = 1 << 28;
			blockSize = dictSize << 2;

			if (blockSize < kMinSize) blockSize = kMinSize;
			else if (blockSize > kMaxSize) blockSize = kMaxSize;
			else if (blockSize < dictSize) blockSize = dictSize;

			blockSize += (kMinSize-1);
			blockSize &= -kMinSize;
		}

		int dictionaryMode = options.getAsyncDictionaryMode();
		if (dictionaryMode == -1) dictionaryMode = options.getMatchFinder() != LZMA2Options.MF_HC4 ? dictSize > 67108864 ? ASYNC_DICT_NONE : ASYNC_DICT_ASYNCSET : ASYNC_DICT_SET;
		if (dictionaryMode == ASYNC_DICT_NONE) dictSize = 0;

		this.options = options;
		this.dictionaryMode = (byte) dictionaryMode;

		this.bufPos = dictSize; // 前部留空
		this.bufLen = dictSize + blockSize;
		this.buf = ArrayCache.getByteArray(bufLen, false);
		this.taskExecutor = options.getAsyncExecutor();
		this.taskFree = options.getAsyncAffinity();
	}

	private byte[] b0;
	public void write(int b) throws IOException {
		if (b0 == null) b0 = new byte[1];
		b0[0] = (byte) b;
		write(b0, 0, 1);
	}

	public void write(@Nonnull byte[] buf, int off, int len) throws IOException {
		ArrayUtil.checkRange(buf, off, len);
		if (closed != 0) throw new IOException("Stream finished or closed");

		while (len > 0) {
			int copyLen = Math.min(bufLen - bufPos, len);
			System.arraycopy(buf, off, this.buf, bufPos, copyLen);

			bufPos += copyLen;
			off += copyLen;
			len -= copyLen;

			if (bufPos == bufLen) submitTask();
		}
	}

	private void submitTask() throws IOException {
		SubEncoder task = null;

		if (!tasksFree.isEmpty()) {
			synchronized (taskLock) {
				if (!tasksFree.isEmpty()) {
					task = tasksFree.pop();
				}
			}
		}

		if (task == null) {
			if (taskFree > 0) {
				taskFree--;
				task = new SubEncoder();
			} else {
				synchronized (taskLock) {
					while (tasksFree.isEmpty()) {
						if ((closed&2) != 0) throw new AsynchronousCloseException();

						try {
							taskLock.wait();
						} catch (InterruptedException e) {
							try {
								close();
							} catch (Throwable ignored) {}

							throw new ClosedByInterruptException();
						}
					}
					task = tasksFree.pop();
				}
			}
		}

		int dictSize = options.getDictSize();

		task.id = taskId++;
		if (dictionaryMode == ASYNC_DICT_ASYNCSET) {
			System.arraycopy(buf, 0, task.in, 0, task.pendingSize = bufPos);
		} else {
			if (dictionaryMode == ASYNC_DICT_NONE) dictSize = 0;
			else if (task.id > 0) task.setDictionary(buf, 0, dictSize);

			System.arraycopy(buf, dictSize, task.in, 0, task.pendingSize = bufPos - dictSize);
		}

		taskExecutor.pushTask(task);
		synchronized (taskLock) { taskRunning++; }

		// move dictionary to head
		System.arraycopy(buf, bufPos - dictSize, buf, 0, dictSize);
		bufPos = dictSize;
	}

	void taskFinished(SubEncoder task) throws IOException {
		synchronized (taskLock) {
			taskRunning--;

			if (doneId == task.id) {
				try {
					task.write();
					if ((closed&2) != 0) task.release();
					else tasksFree.add(task);

					while ((task = tasksDone.remove(++doneId)) != null) {
						task.write();
						if ((closed&2) != 0) task.release();
						else tasksFree.add(task);
					}
				} finally {
					taskLock.notifyAll();
				}
			} else {
				tasksDone.putInt(task.id, task);
			}
		}
	}

	/**
	 * Finishes the stream but not closes the underlying OutputStream.
	 */
	public void finish() throws IOException {
		synchronized (this) {
			if ((closed &3) != 0) return;
			closed |= 1;
		}

		if (bufPos > (dictionaryMode == ASYNC_DICT_NONE ? 0 : options.getDictSize())) submitTask();

		synchronized (this) { closed |= 2; }

		ArrayCache.putArray(buf);

		synchronized (taskLock) {
			while (taskRunning > 0) {
				try {
					taskLock.wait();
				} catch (InterruptedException e) {
					break;
				}
			}
		}

		for (SubEncoder encoder : tasksFree) encoder.release();
		tasksFree.clear();

		out.write(0x00);

		try {
			if (out instanceof Finishable)
				((Finishable) out).finish();
		} catch (Throwable e) {
			try {
				close();
			} catch (Throwable ignored) {}
			throw e;
		}
	}

	/**
	 * Finishes the stream and closes the underlying OutputStream.
	 */
	public void close() throws IOException {
		synchronized (this) {
			if (closed >= 4) return;
			closed |= 4;
		}

		try {
			finish();
		} finally {
			out.close();
			out = null;
		}
	}
}
