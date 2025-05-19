package roj.archive.qz.xz;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.concurrent.Task;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.buf.BufferPool;
import roj.reflect.Unaligned;
import roj.util.ArrayUtil;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.AsynchronousCloseException;

import static roj.archive.qz.xz.LZMA2Options.*;
import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2024/1/19 2:51
 */
public final class LZMA2Parallel {
	public final LZMA2Options options;

	public final byte dictMode;
	public final int taskAffinity, dictSize;
	public final int bufPos, bufLen, asyncBufOff, asyncBufLen;

	private volatile int refCnt;

	private final SimpleList<Compressor> tasksFree = new SimpleList<>();
	private int taskFree;

	public LZMA2Parallel(LZMA2Options options,
						 int blockSize,
						 @MagicConstant(intValues = {ASYNC_DICT_NONE, ASYNC_DICT_SET, ASYNC_DICT_ASYNCSET}) int dictMode,
						 @Range(from = 1, to = 255) int affinity) {
		if (blockSize != 0 && blockSize < ASYNC_BLOCK_SIZE_MIN || blockSize > ASYNC_BLOCK_SIZE_MAX) throw new IllegalArgumentException("无效的分块大小 "+blockSize);
		if (affinity < 1) throw new IllegalArgumentException("无效的并行任务数量 "+affinity);
		if (dictMode < 0 || dictMode > ASYNC_DICT_ASYNCSET) throw new IllegalArgumentException("无效的词典处理模式 "+dictMode);

		int dictSize = this.dictSize = options.getDictSize();
		if (blockSize == 0) {
			final int kMinSize = 1 << 20, kMaxSize = 1 << 28;
			blockSize = dictSize << 2;

			if (blockSize < kMinSize) blockSize = kMinSize;
			else if (blockSize > kMaxSize) blockSize = kMaxSize;
			else if (blockSize < dictSize) blockSize = dictSize;

			blockSize += (kMinSize-1);
			blockSize &= -kMinSize;
		}

		this.dictMode = (byte) dictMode;
		if (dictMode == ASYNC_DICT_NONE) dictSize = 0;

		this.options = options;
		this.taskFree = this.taskAffinity = affinity;

		this.bufPos = dictSize; // 前部留空
		this.bufLen = dictSize + blockSize;

		int mySize = bufLen;
		if (dictMode == ASYNC_DICT_SET) mySize -= options.getDictSize();
		// 6 / 65536 < 1 / 10000
		asyncBufLen = (int) Math.ceil(mySize * 1.0001d);
		asyncBufOff = asyncBufLen - bufLen;
	}

	final DynByteBuf add(Writer caller) throws IOException {
		DynByteBuf buf = acquireBuffer(caller, bufLen);
		synchronized (this) {refCnt++;}
		return buf;
	}

	final void remove(Writer caller, DynByteBuf buffer) {
		BufferPool.reserve(buffer);
		synchronized (this) {
			if (--refCnt == 0) {
				for (Compressor encoder : tasksFree) encoder.release();
				tasksFree.clear();
				taskFree = taskAffinity;
			}
		}
	}

	final void submitTask(Writer caller) throws IOException {
		Compressor task;
		getTask:
		synchronized (tasksFree) {
			if ((task = tasksFree.pop()) != null) break getTask;

			if (taskFree > 0) {
				DynByteBuf in = acquireBuffer(caller, asyncBufLen);

				task = new Compressor(this);
				task.out = task.in = in;

				taskFree--;
			} else {
				while (tasksFree.isEmpty()) {
					if ((caller.closed & 4) != 0) throw new AsynchronousCloseException();
					IOUtil.ioWait(caller, tasksFree);
				}

				task = tasksFree.pop();
			}
		}

		try {
			task.owner = caller;
			var javac傻逼 = task;
			options.getAsyncExecutor().pushTask(() -> caller.prepareTask(javac傻逼));
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}

	final void reserveTask(Compressor task) {
		if (refCnt == 0) {
			synchronized (this) {
				if (refCnt == 0) {
					task.release();
					return;
				}
			}
		}

		synchronized (tasksFree) {
			tasksFree.add(task);
			tasksFree.notify();
		}
	}

	private DynByteBuf acquireBuffer(Writer caller, int len) throws IOException {
		DynByteBuf buf = options.getAsyncBufferPool().allocate(true, len, 0);
		if (buf == null) throw new MemoryLimitException(len / 1024, (int) (options.getAsyncBufferPool().getDirectMax() / 1024));
		return buf;
	}

	public final long getExtraMemoryUsageBytes(boolean mixedMode) { return (long) (asyncBufLen + (mixedMode ? bufLen : 1)) * taskAffinity; }
	public final OutputStream createEncoder(OutputStream out) { return new Writer(out, this); }

	static final class Writer extends OutputStream implements Finishable {
		volatile byte closed;

		private OutputStream out;
		private final LZMA2Parallel man;

		private final long buf;
		private final DynByteBuf bufMem;
		private int bufPos;

		private final IntMap<Compressor> tasksDone = new IntMap<>();
		private int taskId, taskRunning, doneId;

		Writer(OutputStream out, LZMA2Parallel man) {
			if (out == null) throw new NullPointerException("out");

			this.out = out;
			this.man = man;

			this.bufPos = man.bufPos; // 前部留空
			try {
				this.bufMem = man.add(this);
			} catch (IOException e) {
				throw new IllegalStateException("acquireBuffer() failed due to interrupt", e);
			}
			this.buf = this.bufMem.address();
		}

		private byte[] b0;
		public void write(int b) throws IOException {
			if (b0 == null) b0 = new byte[1];
			b0[0] = (byte) b;
			write(b0, 0, 1);
		}

		public void write(@NotNull byte[] buf, int off, int len) throws IOException {
			ArrayUtil.checkRange(buf, off, len);
			if (closed != 0) throw new IOException("Stream finished or closed");

			while (len > 0) {
				if (closed != 0) throw new AsynchronousCloseException();

				int copyLen = Math.min(man.bufLen - bufPos, len);
				U.copyMemory(buf, Unaligned.ARRAY_BYTE_BASE_OFFSET+off, null, this.buf+bufPos, copyLen);

				bufPos += copyLen;
				off += copyLen;
				len -= copyLen;

				if (bufPos == man.bufLen) man.submitTask(this);
			}
		}

		final Task prepareTask(Compressor task) {
			byte dictMode = man.dictMode;
			int dictSize = man.dictSize;

			task.id = taskId++;
			task.in.clear();
			if (dictMode == ASYNC_DICT_ASYNCSET) {
				U.copyMemory(buf, task.in.address()+man.asyncBufOff, task.pendingSize = bufPos);
			} else {
				if (dictMode == ASYNC_DICT_NONE) dictSize = 0;
				else if (task.id > 0) task.lzma.lzPresetDict0(dictSize, null, buf, dictSize);

				U.copyMemory(buf+dictSize, task.in.address()+man.asyncBufOff, task.pendingSize = bufPos - dictSize);
			}

			synchronized (tasksDone) { taskRunning++; }

			// move dictionary to head
			U.copyMemory(null, buf+bufPos-dictSize, null, buf, dictSize);
			bufPos = dictSize;

			return task;
		}

		final void taskFailed() throws IOException {
			synchronized (tasksDone) { taskRunning--; tasksDone.notifyAll(); }
			synchronized (this) { closed |= 4; close(); }
		}
		final void taskFinished(Compressor task) throws IOException {
			synchronized (tasksDone) {
				taskRunning--;

				if (doneId == task.id) {
					try {
						task.in.writeToStream(out);
						man.reserveTask(task);

						while ((task = tasksDone.remove(++doneId)) != null) {
							task.in.writeToStream(out);
							man.reserveTask(task);
						}
					} finally {
						tasksDone.notifyAll();
					}
				} else {
					tasksDone.put(task.id, task);
				}
			}
		}

		/**
		 * Finishes the stream but not closes the underlying OutputStream.
		 */
		public void finish() throws IOException {
			if ((closed&1) != 0) return;
			try {
				if (bufPos > man.bufPos) man.submitTask(this);
			} catch (Throwable ignored) {}
			synchronized (this) { closed |= 1; }

			man.remove(this, bufMem);

			synchronized (tasksDone) {
				while (taskRunning > 0) {
					try {
						tasksDone.wait();
					} catch (InterruptedException e) {
						break;
					}
				}
			}

			synchronized (this) { closed |= 4; }

			out.write(0x00);

			try {
				if (out instanceof Finishable)
					((Finishable) out).finish();
			} catch (Throwable e) {
				try {
					close();
				} catch (Throwable ex) {
					e.addSuppressed(ex);
				}
				throw e;
			}
		}

		/**
		 * Finishes the stream and closes the underlying OutputStream.
		 */
		public void close() throws IOException {
			synchronized (this) {
				if ((closed&2) != 0) return;
				closed |= 2;
			}

			try {
				finish();
			} finally {
				out.close();
				out = null;
			}
		}
	}
	static final class Compressor extends LZMA2Out implements Task {
		final LZMA2Parallel man;
		DynByteBuf in;
		int pendingSize;

		Writer owner;
		int id;

		Compressor(LZMA2Parallel _owner) {
			super(_owner.options);
			this.man = _owner;
		}

		@SuppressWarnings("fallthrough")
		public final void execute() throws Exception {
			int off, len;
			if (man.dictMode == ASYNC_DICT_ASYNCSET) {
				off = man.dictSize;
				len = pendingSize -= off;
			} else {
				off = 0;
				len = pendingSize;
			}

			if (id == 0) {
				byte[] presetDict = man.options.getPresetDict();
				if (presetDict != null && presetDict.length > 0) {
					lzma.lzPresetDict(man.dictSize, presetDict);
					state = PROP_RESET;
				} else {
					state = DICT_RESET;
				}
			} else {
				switch (man.dictMode) {
					case ASYNC_DICT_NONE: state = DICT_RESET; break;
					case ASYNC_DICT_ASYNCSET: lzma.lzPresetDict0(off, null, in.address()+man.asyncBufOff, off);
					case ASYNC_DICT_SET: state = STATE_RESET; break;
				}
			}
			long doff = off+in.address()+ man.asyncBufOff;

			Writer lw = owner;
			try {
				while (len > 0) {
					if ((lw.closed&4) != 0) throw new AsynchronousCloseException();

					int used = lzma.lzFill(doff, len);
					doff += used;
					len -= used;

					if (lzma.encodeForLZMA2()) writeChunk(false);
				}

				lzma.lzFinish();

				while (true) {
					lzma.encodeForLZMA2();
					if (lzma.getUncompressedSize() == 0) break;
					writeChunk(false);
				}

				lzma.lzReset();
				lzma.reset();

				lw.taskFinished(this);
			} catch (Throwable e) {
				man.reserveTask(this);
				lw.taskFailed();
				throw e;
			}
		}

		final void release() {
			lzma.release();
			rc.release();
			if (in != null) {
				BufferPool.reserve(in);
				in = null;
			}
		}
	}
}