package roj.archive.qpak;

import roj.archive.qz.QZArchive;
import roj.archive.qz.QZFileWriter;
import roj.archive.qz.xz.LZMA2Writer;
import roj.asmx.launcher.Autoload;
import roj.asmx.nixim.Copy;
import roj.asmx.nixim.InvokeRedirect;
import roj.asmx.nixim.Nixim;
import roj.asmx.nixim.Shadow;
import roj.concurrent.OperationDone;
import roj.crypt.CRC32s;
import roj.io.IOUtil;
import roj.io.source.CompositeSource;
import roj.io.source.Source;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Roj234
 * @since 2024/4/21 0021 1:23
 */
public class QIncrementPak {
	// 文件结构
	// .001 32byte QZHeader
	// .002 - .xxx Block data
	// .last QZTailHeader
	public static QZFileWriter openIncremental(File baseFile) throws IOException {
		var source = CompositeSource.dynamic(baseFile, true);
		QZFileWriter out;
		if (source.length() > 0) {
			out = new QZArchive(source).append();
			source.setLength(source.position());
		} else {
			out = new QZFileWriter(source);
			source.next();
		}
		return out;
	}

	// 请在调用之前关闭所有的ParallelWriter
	public static void closeIncremental(QZFileWriter qfw) throws IOException {
		qfw.closeWordBlock();
		((CompositeSource) qfw.s).next();
		qfw.close();
	}

	/**
	 * Notice: V2 method was banned by 7-zip v21+, WinRar 7.0+
	 * 但是不知道为什么
	 * <p>
	 * The file format is
	 * .001 32byte QZHeader + QZTailHeader
	 * .002 - .xxx Block data
	 */
	public static QZFileWriter openIncrementalV2(File baseFile) throws IOException {
		var source = CompositeSource.dynamic(baseFile, true);
		QZFileWriter out = source.length() > 0 ? new QZArchive(source).append() : new QZFileWriter(source);
		source.next();
		return out;
	}

	public static void closeIncrementalV2(QZFileWriter qzfw) throws IOException {
		qzfw.closeWordBlock();
		qzfw.setCodec(roj.archive.qz.Copy.INSTANCE);

		CompositeSource s = (CompositeSource) qzfw.s;

		s.setSourceId(0);
		Source meta = s.getSource();

		meta.setLength(32);
		meta.seek(32);

		qzfw.flag = 0;
		AOP aop = (AOP) qzfw;
		aop.aopSetEnabled(true);

		boolean noCompression = false;
		try {
			qzfw.finish();
		} catch (OperationDone e) {
			noCompression = true;
		}

		int hstart = (int) aop.aopGetStartPos();
		int hend = (int) (aop.aopGetEndPos()-32);

		meta.seek(hstart);
		meta.writeInt(Integer.reverseBytes(hend));

		if (noCompression) {
			meta.seek(32);
			ByteList buf = IOUtil.getSharedByteBuf();

			int myCrc = CRC32s.INIT_CRC;
			int read = hend;

			while (read > 0) {
				int r = meta.read(buf.list, 0, Math.min(read, buf.list.length));
				if (r < 0) throw new IllegalStateException();

				myCrc = CRC32s.update(myCrc, buf.list, 0, r);
				read -= r;
			}

			s.seek(0);
			buf.putLong(QZArchive.QZ_HEADER)
			   .putIntLE(0)
			   .putLongLE(0)
			   .putLongLE(hend)
			   .putIntLE(CRC32s.retVal(myCrc));

			buf.putIntLE(8, CRC32s.once(buf.list, 12, 20));

			s.write(buf);
		}

		qzfw.close();
	}

	public interface AOP {long aopGetStartPos();long aopGetEndPos();void aopSetEnabled(boolean enabled);}

	@Autoload(Autoload.Target.NIXIM)
	@Nixim("roj.archive.qz.QZFileWriter")
	private static final class AOP_Inject implements AOP {
		@Shadow
		private ByteList buf;
		@Shadow(owner = "roj.archive.qz.QZWriter")
		private OutputStream out;
		@Shadow(owner = "roj.archive.qz.QZWriter")
		private Source s;
		@Shadow(owner = "roj.archive.qz.QZWriter")
		private int[] flagSum;

		@Copy
		private long aopStartPos, aopEndPos;
		@Copy
		private int aopEnabled;

		@InvokeRedirect(value = "writeStreamInfo", injectDesc = "(J)V", matcher = "putVULong(J)Lroj/util/DynByteBuf;", occurrences = 0)
		private static DynByteBuf aopInc_writeOffset(ByteList ob, long offset, AOP_Inject self) throws IOException {
			if (self.aopEnabled == 0) return ob.putVULong(offset);

			if (self.aopEnabled == 1) {
				ob.put(0xF0).flush();

				if (self.out instanceof LZMA2Writer w) {
					w.setCompressionDisabled(true);
				}

				self.aopStartPos = self.s.position();
				ob.putInt(0);

				if (self.out instanceof LZMA2Writer w) {
					ob.flush();
					w.setCompressionDisabled(false);
					self.aopStartPos = self.s.position() - 4;
					self.flagSum[8] = -1; // INDEX_BLOCK_CRC32
					self.aopEnabled = 0;
				}
			}
			return ob;
		}

		@InvokeRedirect(value = "finish", injectDesc = "()V", matcher = "position()J", occurrences = 2)
		private static long aopInc_setLength(Source s, AOP_Inject self) throws IOException {
			long pos = self.aopEndPos = s.position();
			if (self.aopEnabled == 1) throw OperationDone.INSTANCE;
			return pos;
		}

		@Copy
		@Override
		public long aopGetStartPos() {return aopStartPos;}
		@Copy
		@Override
		public long aopGetEndPos() {return aopEndPos;}
		@Copy
		@Override
		public void aopSetEnabled(boolean enabled) {aopEnabled = 1;}
	}
}