package roj.archive.qz;

import org.jetbrains.annotations.NotNull;
import roj.archive.qz.xz.rangecoder.RangeDecoderFromStream;
import roj.archive.qz.xz.rangecoder.RangeEncoderToStream;
import roj.io.CorruptedInputException;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.Integer.MIN_VALUE;
import static roj.archive.qz.xz.rangecoder.RangeCoder.initProbs;
/**
 * @author Roj234
 * @since 2023/5/29 12:26
 */
public final class BCJ2 extends QZComplexCoder {
	private final int segmentSize;

	public BCJ2() { segmentSize = 64<<20; }
	public BCJ2(int size) { segmentSize = size; }

	private static final byte[] id = {3,3,1,27};
	public byte[] id() {return id;}

	public int useCount() {return 4;}
	public int provideCount() {return 1;}

	public OutputStream[] complexEncode(OutputStream[] out) {return new OutputStream[] { new Encoder(out[0], out[1], out[2], out[3]) };}

	public InputStream[] complexDecode(InputStream[] in, long[] uncompressedSize, int sizeBegin, AtomicInteger memoryLimit) throws IOException {
		useMemory(memoryLimit, 10);
		return new InputStream[] { new Decoder(in[0], in[1], in[2], in[3]) };
	}

	static final class Encoder extends OutputStream implements Finishable {
		private final OutputStream main, call, jump;
		private final RangeEncoderToStream rc;
		Encoder(OutputStream main, OutputStream call, OutputStream jump, OutputStream rc) {
			this.main = main;
			this.call = call;
			this.jump = jump;
			this.rc = new RangeEncoderToStream(rc);

			initProbs(probs);
		}

		private static final int
			BCJ2_RELAT_LIMIT_NUM_BITS = 26,
			BCJ2_RELAT_LIMIT = 1 << BCJ2_RELAT_LIMIT_NUM_BITS;

		private int fileSize; /* (fileSize <= ((UInt32)1 << 31)), 0 means no_limit */
		private int relatLimit = BCJ2_RELAT_LIMIT; /* (relatLimit <= ((UInt32)1 << 31)), 0 means desable_conversion */

		public void setFileSize(int fileSize) { this.fileSize = fileSize; }
		public void setRelatLimit(int relatLimit) { this.relatLimit = relatLimit; }

		private final ByteList ob = new ByteList();
		private int offset = 0, b = -1;
		private final short[] probs = new short[2 + 256];

		@Override
		public void write(int b) throws IOException {
			ob.put(b);

			if (ob.wIndex() >= 128) {
				tryEncode(ob);
				ob.compact();
			}
		}

		@Override
		public void write(@NotNull byte[] b, int off, int len) throws IOException {
			if (len == 0) return;

			while (ob.isReadable()) {
				ob.put(b[off++]);
				tryEncode(ob);
				if (--len == 0) return;
			}

			ByteList ob = IOUtil.SharedBuf.get().wrap(b, off, len);
			tryEncode(ob);

			if (ob.isReadable()) this.ob.put(ob);
		}

		private void tryEncode(ByteList ob) throws IOException {
			int prev, b = this.b;
			for (;;) {
				foundJump: {
					int srcPos = ob.rIndex, srcLim = ob.wIndex();

					while (srcPos < srcLim) {
						prev = b;
						b = ob.getU(srcPos++);

						if ((b & 0xFE) == 0xE8 ||
							prev == 0x0F && (b & 0xF0) == 0x80) {
							if (srcLim - srcPos < 4) {
								writeToMain(ob, srcPos-1);
								this.b = prev;
								return;
							}

							this.b = b;
							writeToMain(ob, srcPos);
							break foundJump;
						}
					}

					this.b = b;
					writeToMain(ob, srcPos);
					return;
				}

				assert prev >= 0;

				// 草了，为了省点跳转至于么？
				// const unsigned c = ((v + 0x17) >> 6) & 1;
				// CBcj2Prob *prob = p->probs + (unsigned)
				//	(((0 - c) & (Byte) (v >> NUM_SHIFT_BITS)) + c + ((v >> 5) & 1));
				int idx = (b == 0xE8 ? 2 + prev : (b == 0xE9 ? 1 : 0));

				// Relative offset (relat)
				int v = ob.readIntLE(ob.rIndex);

				// 多线程实现方式不同，似乎没有v23.01提到的corner case
				if ((fileSize == 0 || (offset+4+v)+MIN_VALUE < fileSize+MIN_VALUE)
					&& ((v+relatLimit) >>> 1)+MIN_VALUE < relatLimit+MIN_VALUE) {
					rc.encodeBit(probs, idx, 1);

					// 吐槽同上
					// const unsigned cj = (((v + 0x57) >> 6) & 1) + BCJ2_STREAM_CALL;
					OutputStream out = (b == 0xE8) ? call : jump;

					b = v >>> 24;
					ob.rIndex += 4;

					offset += 4;
					// Absolute offset (absol)
					v += offset;

					out.write((v >>> 24));
					out.write((v >>> 16));
					out.write((v >>>  8));
					out.write((v       ));
				} else {
					rc.encodeBit(probs, idx, 0);
				}
			}
		}
		private void writeToMain(ByteList ob, int srcPos) throws IOException {
			int len = srcPos - ob.rIndex;
			main.write(ob.list, ob.arrayOffset()+ ob.rIndex, len);
			ob.rIndex = srcPos;
			offset += len;
		}

		@Override
		public synchronized void finish() throws IOException {
			if (b != -2) {
				tryEncode(ob);
				ob.writeToStream(main);
				ob._free();
				rc.finish();
				rc.release();
				if (main instanceof Finishable f) f.finish();
				if (call instanceof Finishable f) f.finish();
				if (jump instanceof Finishable f) f.finish();
				if (rc.out instanceof Finishable f) f.finish();
				b = -2;
			}
		}

		@Override
		public synchronized void close() throws IOException {
			try {
				finish();
			} finally {
				main.close();
				call.close();
				jump.close();
				rc.out.close();
			}
		}
	}
	static final class Decoder extends InputStream {
		private final InputStream main, call, jump;
		private final RangeDecoderFromStream rc;
		Decoder(InputStream main, InputStream call, InputStream jump, InputStream rc) throws IOException {
			this.main = main;
			this.call = call;
			this.jump = jump;
			this.rc = new RangeDecoderFromStream(rc);

			ob = new ByteList();

			offset = 0;
			b = -1;
			initProbs(probs);
		}

		private final ByteList ob;
		private int offset, b;
		private final short[] probs = new short[2 + 256];

		@Override
		public int read() throws IOException {
			if (!ob.isReadable()) decode(offset+512);
			return ob.isReadable() ? ob.readUnsignedByte() : -1;
		}

		@Override
		public int read(@NotNull byte[] b, int off, int len) throws IOException {
			if (len == 0) return 0;
			if (!ob.isReadable()) decode(offset+512);
			if (!ob.isReadable()) return -1;
			ob.readFully(b, off, len = Math.min(ob.readableBytes(), len));
			return len;
		}

		private int decode(int mxMax) throws IOException {
			ob.clear();

			int prev, b = this.b;
			for (;;) {
				for (;;) {
					prev = b;
					if ((b = main.read()) < 0) return 0;
					ob.write(b);
					offset++;

					if ((b & 0xFE) == 0xE8) break;
					if (prev == 0x0F && (b & 0xF0) == 0x80) break;

					if (offset >= mxMax) {
						this.b = b;
						return 1;
					}
				}

				this.b = b;

				assert prev >= 0;
				int idx = (b == 0xE8 ? 2 + prev : (b == 0xE9 ? 1 : 0));

				if (rc.decodeBit(probs, idx) != 0) {
					InputStream in = (b == 0xE8) ? call : jump;
					int i = ob.readStream(in, 4);
					if (i < 4) throw new CorruptedInputException("地址流结束过早");
					int val = ob.readInt(ob.wIndex()-4);

					offset += 4;
					val -= offset;
					ob.putIntLE(ob.wIndex()-4, val);
					b = val >>> 24;
				}
			}
		}

		@Override
		public void close() throws IOException {
			main.close();
			call.close();
			jump.close();
			rc.in.close();
		}
	}
}