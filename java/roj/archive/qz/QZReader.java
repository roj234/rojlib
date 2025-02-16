package roj.archive.qz;

import roj.archive.CRC32InputStream;
import roj.io.LimitInputStream;
import roj.io.source.Source;
import roj.io.source.SourceInputStream;
import roj.reflect.ReflectionUtils;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj233
 * @since 2024/1/8 23:37
 */
public abstract class QZReader implements Closeable {
	Source r;
	byte flag;

	Source fpRead;
	static final long FPREAD_OFFSET = ReflectionUtils.fieldOffset(QZReader.class, "fpRead");

	private QZEntry activeEntry;
	private InputStream blockInput;
	private LimitInputStream activeIn;

	QZReader() {}

	public final InputStream getInput(QZEntry file) throws IOException { return getInput(file, null); }
	/**
	 * 带有对顺序访问的优化，但是不支持并发访问，{@link #getInputUncached(QZEntry, byte[])}
	 */
	public final InputStream getInput(QZEntry file, byte[] pass) throws IOException {
		if (file.uSize == 0) return new SourceInputStream(null, 0);

		// 顺序访问的处理
		if (activeEntry != null && activeEntry.block == file.block) {
			long size = 0;
			QZEntry e = activeEntry.next;
			while (e != null) {
				if (e == file) {
					activeEntry = file;
					// noinspection all
					activeIn.skip(activeIn.remain);
					activeIn.remain = e.uSize;
					// noinspection all
					blockInput.skip(size);
					return activeIn;
				}
				size += e.uSize;
				e = e.next;
			}
		}

		closeSolidStream();

		InputStream in = blockInput = getSolidStream(file.block, pass, true);
		if (in.skip(file.offset) < file.offset) {
			in.close();
			throw new EOFException("数据流过早终止");
		}

		LimitInputStream fin = new LimitInputStream(in, file.uSize);
		if (file.next != null) {
			activeEntry = file;
			activeIn = fin;
		}

		if ((flag&QZArchive.FLAG_RECOVERY) != 0 && (file.flag&QZEntry.CRC) != 0) return new CRC32InputStream(fin, file.crc32);
		return fin;
	}

	public final InputStream getInputUncached(QZEntry file) throws IOException {return getInputUncached(file, null);}
	/**
	 * 适用于少量的并发访问，如果大量并发访问，可能会打开太多的文件，应该使用{@link QZArchive#parallel()}
	 */
	public final InputStream getInputUncached(QZEntry file, byte[] pass) throws IOException {
		if (file.uSize == 0) return new SourceInputStream(null, 0);

		InputStream in = getSolidStream(file.block, pass, (file.flag&QZEntry.CRC) == 0);
		if (in.skip(file.offset) < file.offset) {
			in.close();
			throw new EOFException("数据流过早终止");
		}

		LimitInputStream fin = new LimitInputStream(in, file.uSize);
		return (file.flag & QZEntry.CRC) != 0 ? new CRC32InputStream(fin, file.crc32) : fin;
	}

	final void closeSolidStream() throws IOException {
		if (blockInput != null) {
			blockInput.close();
			blockInput = null;
		}
		activeIn = null;
		activeEntry = null;
	}

	final InputStream getSolidStream(WordBlock b, byte[] pass, boolean verify) throws IOException {
		Source src = (Source) U.getAndSetObject(this, FPREAD_OFFSET, null);
		if (src == null) src = r.threadSafeCopy();
		return getSolidStream1(b, pass, src, this, verify);
	}
	abstract InputStream getSolidStream1(WordBlock b, byte[] pass, Source src, QZReader that, boolean verify) throws IOException;
}