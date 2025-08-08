package roj.archive.qz.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import roj.archive.qz.QZArchive;
import roj.archive.qz.QZFileWriter;
import roj.archive.xz.LZMA2Writer;
import roj.asmx.injector.Copy;
import roj.asmx.injector.Redirect;
import roj.asmx.injector.Shadow;
import roj.asmx.injector.Weave;
import roj.asmx.launcher.Autoload;
import roj.util.OperationDone;
import roj.crypt.CRC32;
import roj.io.IOUtil;
import roj.io.source.CompositeSource;
import roj.io.source.Source;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.regex.Matcher;

import static roj.archive.ArchiveUtils.SPLIT_ARCHIVE_PATTERN;

/**
 * 提供创建支持增量更新的分卷压缩文件的功能，支持双头（DualHeader）和单头（SingleHeader）两种格式。
 * <p>
 * 头部和数据分开存放，如果仅仅在压缩文件末尾追加了数据，那么只需要更新头部和新的数据块，而不需要改动其它数据块。
 * <p>
 * 双头包含两个头部，分别是.001和最后一个卷<br>
 * 单头把它们都存在.001中，使用更加方便，但是其他软件的兼容性受限
 *
 * @author Roj234
 * @since 2024/4/21 1:23
 */
public class QIncrementPak {
	@NotNull
	private static CompositeSource getSource(File baseFile) throws IOException {
		Matcher m = SPLIT_ARCHIVE_PATTERN.matcher(baseFile.getName());
		if (!m.find()) throw new IllegalArgumentException("文件不指向第一卷");

		String path = baseFile.getAbsolutePath();
		return CompositeSource.dynamic(new File(path.substring(0, path.length() + m.start() - m.end())), true);
	}

	/**
	 * 以写入模式打开支持增量更新的双头分卷压缩文件。
	 * <p>
	 * 文件格式：
	 * <ul>
	 *   <li>首卷（.001）包含32字节的QZHeader</li>
	 *   <li>中间卷（.002至.xxx）存储块数据</li>
	 *   <li>末卷（.xxx + 1）存储QZTailHeader</li>
	 * </ul>
	 *
	 * @param baseFile 基础文件对象，指向分卷序列的首卷（如"archive.001"）
	 * @return 用于写入的QZFileWriter实例
	 * @throws IOException 当文件操作失败时抛出
	 */
	public static QZFileWriter openDualHeader(File baseFile) throws IOException {
		var source = getSource(baseFile);

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
	/**
	 * 关闭双头分卷压缩文件的写入器。
	 * <p>
	 * <b>重要提示：</b> 调用前必须关闭所有通过{@link QZFileWriter#newParallelWriter()}创建的并行写入器，
	 * 否则文件结构可能与预期不符（但文件仍可被读取）。
	 *
	 * @param qzfw 要关闭的QZFileWriter实例
	 * @throws IOException 当刷新或关闭操作失败时抛出
	 */
	public static void closeDualHeader(QZFileWriter qzfw) throws IOException {
		qzfw.flush();
		((CompositeSource) qzfw.source()).next();
		qzfw.close();
	}

	/**
	 * 以写入模式打开支持增量更新的单头分卷压缩文件。
	 * <p>
	 * 文件格式规范：
	 * <ul>
	 *   <li>首卷（.001）包含32字节QZHeader和QZTailHeader</li>
	 *   <li>后续卷（.002至.xxx）存储块数据</li>
	 * </ul>
	 *
	 * @param baseFile 基础文件对象，指向分卷序列的首卷（如"archive.001"）
	 * @return 用于写入的QZFileWriter实例
	 * @throws IOException 当文件操作失败时抛出
	 * @apiNote 该格式仅兼容本项目QZArchive、7-zip&lt;v22或WinRar&lt;7.0，高版本压缩软件可能将此格式视为安全漏洞
	 */
	public static QZFileWriter openSingleHeader(File baseFile) throws IOException {
		var source = getSource(baseFile);
		QZFileWriter out = source.length() > 0 ? new QZArchive(source).append() : new QZFileWriter(source);
		source.next();
		return out;
	}

	/**
	 * 关闭单头分卷压缩文件的写入器。
	 * <p>
	 * <b>重要提示：</b> 调用前必须关闭所有通过{@link QZFileWriter#newParallelWriter()}创建的并行写入器，
	 * 否则可能导致文件损坏。
	 *
	 * @param qzfw 要关闭的QZFileWriter实例
	 * @throws IOException 当刷新或关闭操作失败时抛出
	 */
	public static void closeSingleHeader(QZFileWriter qzfw) throws IOException {
		qzfw.flush();
		qzfw.setCodec(roj.archive.qz.Copy.INSTANCE);

		CompositeSource s = (CompositeSource) qzfw.source();

		s.setSourceId(0);
		Source meta = s.getSource();

		meta.setLength(32);
		meta.seek(32);

		qzfw.flag = 0;
		QZSH sh = (QZSH) qzfw;
		sh.setUseSingleHeader(true);

		boolean noCompression = false;
		try {
			qzfw.finish();
		} catch (OperationDone e) {
			noCompression = true;
		}

		int hstart = (int) sh.getHeaderBegin();
		int hend = (int) (sh.getHeaderEnd()-32);

		meta.seek(hstart);
		meta.writeInt(Integer.reverseBytes(hend));

		if (noCompression) {
			meta.seek(32);
			ByteList buf = IOUtil.getSharedByteBuf();

			// 重新计算头部CRC
			int myCrc = CRC32.initial;
			int read = hend;

			while (read > 0) {
				int r = meta.read(buf.list, 0, Math.min(read, buf.list.length));
				if (r < 0) throw new IllegalStateException();

				myCrc = CRC32.update(myCrc, buf.list, 0, r);
				read -= r;
			}

			s.seek(0);
			buf.putLong(QZArchive.QZ_HEADER)
			   .putIntLE(0)
			   .putLongLE(0)
			   .putLongLE(hend)
			   .putIntLE(CRC32.finish(myCrc));

			buf.putIntLE(8, CRC32.crc32(buf.list, 12, 20));

			s.write(buf);
		}

		qzfw.close();
	}

	@ApiStatus.Internal
	public interface QZSH {long getHeaderBegin();long getHeaderEnd();void setUseSingleHeader(boolean enabled);}

	@Autoload(Autoload.Target.NIXIM)
	@Weave(target = QZFileWriter.class)
	private static final class QZSHImpl implements QZSH {
		@Shadow(owner = "roj.archive.qz.QZWriter")
		private OutputStream out;
		@Shadow(owner = "roj.archive.qz.QZWriter")
		private Source s;
		@Shadow(owner = "roj.archive.qz.QZWriter")
		private int[] flagSum;

		@Copy
		private long headerBegin, headerEnd;
		@Copy
		private boolean useSingleHeader;

		@Redirect(value = "writeStreamInfo", injectDesc = "(J)V", matcher = "putVULong(J)Lroj/util/DynByteBuf;", occurrences = 0)
		private static DynByteBuf aopWriteOffset(ByteList ob, long offset, QZSHImpl self) throws IOException {
			if (!self.useSingleHeader) return ob.putVULong(offset);

			ob.put(0xF0).flush();

			if (self.out instanceof LZMA2Writer w) {
				// 强制flush并禁止接下来块的压缩，以便我能确定性的得到一个int的offset
				w.setCompressionDisabled(true);
			}

			self.headerBegin = self.s.position();
			ob.putInt(0);

			if (self.out instanceof LZMA2Writer w) {
				ob.flush();
				w.setCompressionDisabled(false);
				self.headerBegin = self.s.position() - 4;
				self.flagSum[8] = -1; // INDEX_BLOCK_CRC32
				self.useSingleHeader = false;
			}
			return ob;
		}

		@Redirect(value = "finish", injectDesc = "()V", matcher = "position()J", occurrences = 2)
		private static long aopGetPosition(Source s, QZSHImpl self) throws IOException {
			long pos = self.headerEnd = s.position();
			if (self.useSingleHeader) throw OperationDone.INSTANCE;
			return pos;
		}

		@Copy
		@Override
		public long getHeaderBegin() {return headerBegin;}
		@Copy
		@Override
		public long getHeaderEnd() {return headerEnd;}
		@Copy
		@Override
		public void setUseSingleHeader(boolean enabled) {useSingleHeader = enabled;}
	}
}