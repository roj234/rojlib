package roj.archive.xz;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.IntMap;
import roj.concurrent.Task;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.MBOutputStream;
import roj.optimizer.FastVarHandle;
import roj.reflect.Telescope;
import roj.reflect.Unsafe;
import roj.util.*;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.invoke.VarHandle;
import java.util.concurrent.CancellationException;
import java.util.function.IntConsumer;

import static roj.archive.xz.LZMA2Options.ASYNC_BLOCK_SIZE_MAX;
import static roj.archive.xz.LZMA2Options.ASYNC_BLOCK_SIZE_MIN;
import static roj.reflect.Unsafe.U;

/**
 * @author Roj234
 * @since 2024/1/19 2:51
 */
public final class LZMA2ParallelEncoder {
	public final LZMA2Options options;

	public final boolean noContext;
	public final int taskAffinity, dictSize;
	public final int bufPos, bufLen, asyncBufOff, asyncBufLen;

	private final HashSet<Encoder> encoders = new HashSet<>();
	private final ArrayList<Compressor> compressors = new ArrayList<>();

	private int taskFree;

	private long memoryLimit;
	private IntConsumer progressListener;

	public LZMA2ParallelEncoder(LZMA2Options options,
								int blockSize,
								boolean noContext,
								@Range(from = 1, to = 255) int affinity) {
		if (blockSize != 0 && blockSize < ASYNC_BLOCK_SIZE_MIN || blockSize > ASYNC_BLOCK_SIZE_MAX) throw new IllegalArgumentException("无效的分块大小 "+blockSize);
		if (affinity < 1) throw new IllegalArgumentException("无效的并行任务数量 "+affinity);

		int dictSize = this.dictSize = options.getDictSize();
		if (blockSize == 0) {
			blockSize = dictSize << 2;

			if (blockSize < ASYNC_BLOCK_SIZE_MIN) blockSize = ASYNC_BLOCK_SIZE_MIN;
			else if (blockSize > ASYNC_BLOCK_SIZE_MAX) blockSize = ASYNC_BLOCK_SIZE_MAX;
			else if (blockSize < dictSize) blockSize = dictSize;

			blockSize += (ASYNC_BLOCK_SIZE_MIN-1);
			blockSize &= -ASYNC_BLOCK_SIZE_MIN;
		}

		this.noContext = noContext;
		if (noContext) dictSize = 0;

		this.options = options;
		this.taskFree = this.taskAffinity = affinity;

		this.bufPos = dictSize; // 给上一个块的字典预留
		this.bufLen = dictSize + blockSize;

		// 1 / 10000 是 6 / 65536 的近似值
		// in的前缀预留这些字节，确保输入输出使用同一个buffer时永远不会覆盖
		asyncBufLen = (int) Math.ceil(blockSize * 1.0001);
		asyncBufOff = asyncBufLen - blockSize;
	}

	public void setProgressListener(IntConsumer progressListener) {this.progressListener = progressListener;}

	public void setMemoryLimit(long memoryLimit) {this.memoryLimit = memoryLimit;}

	final NativeMemory add(Encoder encoder) throws IOException {
		if (memoryLimit == 0) throw new IllegalStateException("Specify memory limit first");
		NativeMemory buf = new NativeMemory(bufLen);
		synchronized (encoders) {encoders.add(encoder);}
		return buf;
	}

	final void remove(Encoder encoder, NativeMemory mem) {
		mem.free();
		synchronized (encoders) {
			encoders.remove(encoder);
			if (encoders.isEmpty()) {
				for (Compressor compressor : compressors) {
					compressor.free();
					memoryLimit += asyncBufLen;
				}
				compressors.clear();
				taskFree = taskAffinity;
			}
		}
	}

	final void submitTask(Encoder encoder) throws IOException {
		Compressor compressor = null;
		if (!compressors.isEmpty()) {
			synchronized (compressors) {
				compressor = compressors.pop();
			}
		}

		if (compressor == null) {
			if (taskFree > 0 && memoryLimit > asyncBufLen) {
				synchronized (this) {
					if (taskFree > 0 && memoryLimit > asyncBufLen) {
						memoryLimit -= asyncBufLen;
						taskFree--;

						DynByteBuf in = DynByteBuf.allocateDirect(asyncBufLen, asyncBufLen);

						compressor = new Compressor(this);
						compressor.out = compressor.in = in;
					}
				}
			}

			if (compressor == null) {
				while (true) {
					synchronized (compressors) {
						if ((encoder.closed & 2) != 0) throw new CancellationException();

						compressor = compressors.pop();
						if (compressor != null) break;

						try {
							compressors.wait();
						} catch (InterruptedException e) {
							throw IOUtil.rethrowAsIOException(e);
						}
					}
				}
			}
		}

		try {
			compressor.owner = encoder;

			Task task = encoder.prepareTask(compressor);
			options.getAsyncExecutor().executeUnsafe(task);
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}

	final void releaseCompressor(Compressor compressor) {
		if (encoders.isEmpty()) {
			synchronized (this) {
				if (encoders.isEmpty()) {
					compressor.free();

					memoryLimit += asyncBufLen;
					taskFree++;
					return;
				}
			}
		}

		synchronized (compressors) {
			compressors.add(compressor);
			compressors.notify();
		}
	}

	public final OutputStream createEncoder(OutputStream out) { return new Encoder(out, this); }

	public void cancel() throws IOException {
		synchronized (this) {
			for (Encoder encoder : new ArrayList<>(encoders)) {
				encoder.cancel();
			}
		}
	}

	@FastVarHandle
	static final class Encoder extends MBOutputStream implements Finishable {
		private static final VarHandle CLOSED = Telescope.lookup().findVarHandle(Encoder.class, "closed", byte.class);
		private static final VarHandle TASK_RUNNING = Telescope.lookup().findVarHandle(Encoder.class, "taskRunning", int.class);

		volatile byte closed;

		private OutputStream out;
		private final LZMA2ParallelEncoder man;

		private final NativeMemory mem;
		private final long buf;
		private int bufPos;

		private final IntMap<Compressor> tasksDone = new IntMap<>();
		private int taskId, taskRunning, doneId;

		Encoder(OutputStream out, LZMA2ParallelEncoder man) {
			if (out == null) throw new NullPointerException("out");

			this.out = out;
			this.man = man;

			this.bufPos = man.bufPos; // 前部留空
			try {
				this.mem = man.add(this);
			} catch (IOException e) {
				throw new IllegalStateException("acquireBuffer() failed due to interrupt", e);
			}
			this.buf = this.mem.address();
		}

		public void write(@NotNull byte[] buf, int off, int len) throws IOException {
			ArrayUtil.checkRange(buf, off, len);

			while (len > 0) {
				if (closed != 0) throw new IOException("Stream finished or closed");

				int copyLen = Math.min(man.bufLen - bufPos, len);
				U.copyMemory(buf, Unsafe.ARRAY_BYTE_BASE_OFFSET+off, null, this.buf+bufPos, copyLen);

				bufPos += copyLen;
				off += copyLen;
				len -= copyLen;

				if (bufPos == man.bufLen)
					man.submitTask(this);
			}
		}

		final Task prepareTask(Compressor task) {
			task.id = taskId++;
			task.in.clear();

			int dictSize;
			if (man.noContext) dictSize = 0;
			else {
				dictSize = man.dictSize;
				if (task.id > 0) task.lzma.setPresetDict0(dictSize, null, buf, dictSize);
			}

			U.copyMemory(buf+dictSize, task.in.address()+man.asyncBufOff, task.pendingSize = bufPos - dictSize);

			TASK_RUNNING.getAndAdd(this, 1);

			// move dictionary to head
			U.copyMemory(buf+bufPos-dictSize, buf, dictSize);
			bufPos = dictSize;

			return task;
		}

		final void taskFailure() throws IOException {
			synchronized (tasksDone) { taskFinished(); }
			cancel();
		}
		final void taskSuccess(Compressor task) throws IOException {
			synchronized (tasksDone) {
				if (doneId == task.id) {
					do {
						task.in.writeToStream(out);
						man.releaseCompressor(task);
					} while ((task = tasksDone.remove(++doneId)) != null);
				} else {
					tasksDone.put(task.id, task);
				}

				taskFinished();
			}
		}
		private void taskFinished() {
			if ((int) TASK_RUNNING.getAndAdd(this, -1) == 1)
				tasksDone.notifyAll();
		}

		final void cancel() throws IOException {
			CLOSED.getAndBitwiseOr(this, (byte) 2);
			synchronized (man.compressors) {
				man.compressors.notifyAll();
			}
			close();
		}

		/**
		 * Finishes the stream but not closes the underlying OutputStream.
		 */
		public void finish() throws IOException {
			var closed = (byte) CLOSED.getAndBitwiseOr(this, (byte) 1);
			// FINISHED
			if ((closed & 1) != 0) return;

			try {
				if (bufPos > man.bufPos) man.submitTask(this);
			} catch (Throwable ignored) {}

			man.remove(this, mem);

			while (taskRunning > 0) {
				synchronized (tasksDone) {
					try {
						tasksDone.wait();
					} catch (InterruptedException e) {
						break;
					}
				}
			}

			CLOSED.getAndBitwiseOr(this, (byte) 2);

			var out = this.out;
			if (out != null) out.write(0x00);
			if (out instanceof Finishable f) f.finish();
		}

		/**
		 * Finishes the stream and closes the underlying OutputStream.
		 */
		public void close() throws IOException {
			try {
				finish();
			} finally {
				IOUtil.closeSilently(out);
				out = null;
			}
		}
	}
	static final class Compressor extends LZMA2Encoder implements Task {
		final LZMA2ParallelEncoder man;
		DynByteBuf in;
		int pendingSize;

		Encoder owner;
		int id;

		Compressor(LZMA2ParallelEncoder man) {
			super(man.options);
			this.man = man;
		}

		@SuppressWarnings("fallthrough")
		public final void run() throws Exception {
			if (id == 0) {
				byte[] presetDict = man.options.getPresetDict();
				if (presetDict != null && presetDict.length > 0) {
					lzma.setPresetDict(man.dictSize, presetDict);
					state = PROP_RESET;
				} else {
					state = DICT_RESET;
				}
			} else {
				if (man.noContext) {
					state = DICT_RESET;
				} else {
					state = STATE_RESET;
					lzma.getLzEncoder().skip(man.dictSize);
				}
			}

			int off = 0;
			int len = pendingSize;
			long doff = off+in.address()+man.asyncBufOff;

			Encoder lw = owner;
			try {
				while (len > 0) {
					if ((lw.closed&2) != 0) throw new FastFailException("Cancelled by other threads");

					int filled = lzma.fillWindow(doff, len);
					doff += filled;
					len -= filled;
					if (man.progressListener != null) man.progressListener.accept(filled);

					if (lzma.encodeForLZMA2()) writeChunk(false);
				}

				lzma.setFinishing();

				while (true) {
					lzma.encodeForLZMA2();
					if (lzma.getUncompressedSize() == 0) break;
					writeChunk(false);
				}

				lzma.getLzEncoder().reset();
				lzma.reset();

				lw.taskSuccess(this);
			} catch (Throwable e) {
				lw.taskFailure();
				man.releaseCompressor(this);
				throw e;
			}
		}

		final void free() {
			lzma.free();
			rc.free();
			in.release();
		}
	}
}