package roj.archive.qz;

import roj.archive.qz.xz.CorruptedInputException;
import roj.archive.qz.xz.rangecoder.RangeDecoderFromStream;
import roj.archive.qz.xz.rangecoder.RangeEncoderToStream;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.util.ByteList;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static java.lang.Integer.MIN_VALUE;
import static roj.archive.qz.xz.rangecoder.RangeCoder.initProbs;
/**
 * @author Roj234
 * @since 2023/5/29 0029 12:26
 */
public final class BCJ2 extends QZComplexCoder {
	private final int segmentSize;

	public BCJ2() {segmentSize = 64<<20;}
	public BCJ2(int size) {segmentSize = size;}

	QZCoder factory() { return this; }

	private static final byte[] id = {3,3,1,27};
	byte[] id() { return id; }

	public int useCount() { return 4; }
	public int provideCount() { return 1; }

	public OutputStream[] complexEncode(OutputStream[] out) throws IOException {
		return new OutputStream[] { new Encoder(out[0], out[1], out[2], out[3]) };
	}

	public InputStream[] complexDecode(InputStream[] in, long[] uncompressedSize, int sizeBegin) throws IOException {
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
				tryEncode(ob, false);
				ob.compact();
			}
		}

		@Override
		public void write(@Nonnull byte[] b, int off, int len) throws IOException {
			if (len == 0) return;

			if (ob.isReadable()) {
				ob.compact();
				while (ob.isReadable()) {
					ob.put(b[off++]);
					tryEncode(ob, false);
					if (--len == 0) return;
				}
			}

			ByteList ob = IOUtil.SharedCoder.get().wrap(b, off, len);
			tryEncode(ob, false);

			if (ob.isReadable()) this.ob.put(ob);
		}

		private void tryEncode(ByteList ob, boolean finish) throws IOException {
			int prev, b = this.b;
			for (;;) {
				for (;;) {
					if (!ob.isReadable()) {
						this.b = b;
						return;
					}

					prev = b;
					b = ob.readUnsignedByte();
					offset++;

					if ((b & 0xFE) == 0xE8 ||
						prev == 0x0F && (b & 0xF0) == 0x80) {
						if (ob.readableBytes() < 4) {
							ob.rIndex--;
							offset--;
							this.b = prev;
							return;
						}

						main.write(b);
						break;
					}
					main.write(b);
				}

				this.b = b;

				assert prev >= 0;
				int idx = (b == 0xE8 ? 2 + prev : (b == 0xE9 ? 1 : 0));

				int v = ob.readIntLE(ob.rIndex);
				if ((fileSize == 0 || (offset+4+v)+MIN_VALUE < fileSize+MIN_VALUE)
					&& ((v+relatLimit) >>> 1)+MIN_VALUE < relatLimit+MIN_VALUE) {
					rc.encodeBit(probs, idx, 1);

					OutputStream out = (b == 0xE8) ? call : jump;

					b = v >>> 24;
					ob.rIndex += 4;

					offset += 4;
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

		@Override
		public void finish() throws IOException {
			if (b != -2) {
				tryEncode(ob, true);
				ob.writeToStream(main);
				ob._free();
				rc.finish();
				if (main instanceof Finishable) ((Finishable) main).finish();
				if (call instanceof Finishable) ((Finishable) call).finish();
				if (jump instanceof Finishable) ((Finishable) jump).finish();
				if (rc.out instanceof Finishable) ((Finishable) rc.out).finish();
				b = -2;
			}
		}

		@Override
		public void close() throws IOException {
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
		public int read(@Nonnull byte[] b, int off, int len) throws IOException {
			if (len == 0) return 0;
			if (!ob.isReadable()) decode(offset+512);
			if (!ob.isReadable()) return -1;
			ob.read(b, off, len = Math.min(ob.readableBytes(), len));
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
			rc.inData.close();
		}
	}
}
