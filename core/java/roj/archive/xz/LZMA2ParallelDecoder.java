package roj.archive.xz;

import org.jetbrains.annotations.NotNull;
import roj.archive.rangecoder.RangeDecoder;
import roj.archive.xz.lz.LZDecoder;
import roj.archive.xz.lzma.LZMADecoder;
import roj.collect.ArrayList;
import roj.concurrent.Task;
import roj.concurrent.TaskGroup;
import roj.io.CorruptedInputException;
import roj.io.IOUtil;
import roj.io.MBInputStream;
import roj.util.ByteList;
import roj.util.NativeMemory;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.AsynchronousCloseException;

import static roj.archive.xz.LZMA2Encoder.COMPRESSED_SIZE_MAX;
import static roj.archive.xz.LZMAInputStream.getDictSize;

public class LZMA2ParallelDecoder extends MBInputStream {
	private volatile boolean eof, hasError;

	private final int dictSize;
	private final byte[] presetDict;
	private TaskGroup executor;

	private int taskRunning;

	private final ArrayList<Uncompressor> freeUncompressors = new ArrayList<>();
	private int taskFree, taskId;

	private LZMA2InputStream lzma2InputStream;

	public interface AsyncConsumer {
		void chunkBegin(int chunkId, long offset);
		void chunkData(int chunkId, Object address1, long address2, int length);
		void chunkEnd(int chunkId, boolean hasError);
	}

	final class Uncompressor implements Task {
		private static final int OUT_BUFFER = 131072;

		int id;
		long offset;

		final ByteList input;
		private final NativeMemory ob;

		private final LZDecoder lz;
		private final RangeDecoder rc;
		private LZMADecoder lzma;

		private int uncompressedSize;
		private byte state;

		Uncompressor(boolean isFirst) {
			input = new ByteList();
			ob = new NativeMemory(OUT_BUFFER);
			this.rc = new RangeDecoder(COMPRESSED_SIZE_MAX);
			this.lz = new LZDecoder(getDictSize(dictSize), isFirst?presetDict:null);
			state = LZMA2Encoder.DICT_RESET;
		}

		public final void run() throws Exception {
			consumer.chunkBegin(id, offset);

			long address = ob.address();
			try {
				while (true) {
					if (uncompressedSize <= 0) {
						while (!input.isReadable()) {
							synchronized (input) {
								if (!input.isReadable())
									input.clear();

								input.wait();
							}
							if (eof) break;
						}
						nextChunk();

						if (state == -1) break;
						assert uncompressedSize > 0;
					}

					int copySizeMax = Math.min(uncompressedSize, OUT_BUFFER);

					if (state == LZMA2Encoder.STATE_LZMA) {
						lz.setLimit(copySizeMax);
						lzma.decode();
					} else {
						lz.copyUncompressed(input, copySizeMax);
					}

					int copiedSize = lz.flush0(null, address);
					consumer.chunkData(id, null, address, copiedSize);
					uncompressedSize -= copiedSize;

					if (uncompressedSize == 0)
						if (!rc.isFinished() || lz.hasPending()) throw new CorruptedInputException("trailing compressed data");
				}
			} catch (Throwable e) {
				eof = true;
				hasError = true;
				throw e;
			} finally {
				consumer.chunkEnd(id, hasError);
				taskFinished(this);
			}
		}

		final void free() {
			lz.free();
			rc.finish();
			input.release();
			ob.free();
		}

		final void reset() {
			if (lzma != null) lzma.reset();
			lz.reset();
			state = LZMA2Encoder.DICT_RESET;
		}

		@SuppressWarnings("fallthrough")
		private void nextChunk() throws IOException {
			int control = input.readUnsignedByte();
			if (control <= 0x7F) {
				switch (control) {
					default: throw new CorruptedInputException("invalid control byte");
					case 0: state = -1; return; // End of data
					case 1: lz.reset(); break; // uncompressed with dict reset
					case 2: // uncompressed
						if (state > LZMA2Encoder.STATE_RESET) throw new CorruptedInputException("excepting dict reset");
				}

				state = LZMA2Encoder.STATE_RESET;
				uncompressedSize = input.readUnsignedShort()+1;
				return;
			}

			uncompressedSize = ((control & 0x1F) << 16) + input.readUnsignedShort() + 1;
			int cSize = input.readUnsignedShort()+1;

			switch (control >>> 5) {
				// LZMA, dict reset
				case 7: lz.reset(); readProps(); break;
				// LZMA, prop reset
				case 6:
					if (state == LZMA2Encoder.DICT_RESET) throw new CorruptedInputException("excepting dict reset");
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

			state = LZMA2Encoder.STATE_LZMA;
			rc.manualFill(input, cSize);
		}

		private void readProps() throws IOException {
			int props = input.readUnsignedByte();

			if (props > (4 * 5 + 4) * 9 + 8) throw new CorruptedInputException("Invalid LZMA properties byte");

			int pb = props / (9 * 5);
			props -= pb * 9 * 5;
			int lp = props / 9;
			int lc = props - lp * 9;

			if (lc + lp > 4) throw new CorruptedInputException("Invalid LZMA properties byte");

			lzma = new LZMADecoder(lz, rc, lc, lp, pb);
		}
	}

	void taskFinished(Uncompressor task) {
		synchronized (freeUncompressors) {
			--taskRunning;

			if (eof) task.free();
			else {
				task.reset();
				freeUncompressors.add(task);
			}

			freeUncompressors.notifyAll();
		}
	}

	private DataInputStream in;

	public LZMA2ParallelDecoder(InputStream in, int dictSize) { this(in, dictSize, null); }
	public LZMA2ParallelDecoder(InputStream in, int dictSize, byte[] presetDict) {
		// Check for null because otherwise null isn't detect
		// in this constructor.
		if (in == null) throw new NullPointerException();

		this.in = new DataInputStream(in);
		this.dictSize = dictSize;
		this.presetDict = presetDict;
	}

	@Override
	public int read(@NotNull byte[] b, int off, int len) throws IOException {
		if (lzma2InputStream == null)
			lzma2InputStream = new LZMA2InputStream(in, dictSize, presetDict);
		return lzma2InputStream.read(b, off, len);
	}

	private AsyncConsumer consumer;

	public void runAsync(TaskGroup executor, int affinity, AsyncConsumer consumer) throws IOException {
		this.executor = executor;
		this.taskFree = affinity;
		this.consumer = consumer;

		while (!eof) nextChunk();
	}

	private Uncompressor uncompressor;
	private long offset;

	private void nextChunk() throws IOException {
		int control = in.readUnsignedByte();
		if (control <= 0x7F) {
			switch (control) {
				default: throw new CorruptedInputException("invalid control byte");
				case 0: finishDecode(); eof = true; return;
				case 2: case 1:
					int uncompressedSize = in.readUnsignedShort()+1;
					offset += uncompressedSize;

					ByteList inputBuf = uncompressor.input;
					synchronized (inputBuf) {
						compactOnDemand(inputBuf);
						inputBuf.put(control).putShort(uncompressedSize-1);
						inputBuf.readStream(in, uncompressedSize);
						inputBuf.notify();
					}
				return;
			}
		}

		int flag = (control >>> 6) & 1;
		if (flag != 0) nextDecoder();

		int rawUSize = in.readUnsignedShort();

		int uncompressedSize = ((control & 0x1F) << 16) + rawUSize + 1;
		offset += uncompressedSize;
		int rawCSize = in.readUnsignedShort();

		ByteList inputBuf = uncompressor.input;
		synchronized (inputBuf) {
			compactOnDemand(inputBuf);
			inputBuf.put(control).putShort(rawUSize).putShort(rawCSize);
			inputBuf.readStream(in, rawCSize+1+flag);
			inputBuf.notify();
		}
	}

	private void compactOnDemand(ByteList buf) {
		if (buf.rIndex > buf.wIndex()>>1) buf.compact();
	}

	private void nextDecoder() throws IOException {
		finishDecode();

		Uncompressor task = null;

		if (freeUncompressors.isEmpty()) {
			if (taskFree > 0) {
				taskFree--;
				task = new Uncompressor(uncompressor == null);
			} else {
				while (freeUncompressors.isEmpty()) {
					if (eof) throw new AsynchronousCloseException();
					IOUtil.ioWait(this, freeUncompressors);
				}
			}
		}

		if (task == null) {
			synchronized (freeUncompressors) {
				task = freeUncompressors.pop();
			}
		}
		task.id = taskId++;
		task.offset = offset;

		executor.executeUnsafe(task);
		synchronized (freeUncompressors) { taskRunning++; }

		uncompressor = task;
	}

	private void finishDecode() {
		if (uncompressor != null) {
			// previous decoder EOF
			ByteList buf = uncompressor.input;
			synchronized (buf) {
				compactOnDemand(buf);
				buf.put(0);
				buf.notify();
			}
		}
	}

	public void close() throws IOException {
		eof = true;
		finishDecode();

		synchronized (freeUncompressors) {
			while (taskRunning > 0) {
				try {
					freeUncompressors.wait();
				} catch (InterruptedException e) {
					break;
				}
			}
		}

		for (var uncompressor : freeUncompressors)
			uncompressor.free();
		freeUncompressors.clear();

		IOUtil.closeSilently(lzma2InputStream);
		IOUtil.closeSilently(in);
		in = null;
	}
}