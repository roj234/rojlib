package roj.archive.qz;

import roj.archive.ChecksumInputStream;
import roj.io.LimitInputStream;
import roj.io.SourceInputStream;
import roj.io.source.BufferedSource;
import roj.io.source.Source;
import roj.reflect.ReflectionUtils;

import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj233
 * @since 2024/1/8 23:37
 */
public abstract class QZReader implements Closeable {
	Source r;
	boolean recovery;

	Source fpRead;
	static final long FPREAD_OFFSET = ReflectionUtils.fieldOffset(QZReader.class, "fpRead");

	private QZEntry activeEntry;
	private InputStream blockInput;
	private LimitInputStream activeIn;

	QZReader() {}

	public final InputStream getInput(QZEntry file) throws IOException { return getInput(file, null); }
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

		InputStream in = blockInput = getSolidStream(file.block, pass);
		if (in.skip(file.offset) < file.offset) {
			in.close();
			throw new EOFException("数据流过早终止");
		}

		LimitInputStream fin = new LimitInputStream(in, file.uSize);
		if (file.next != null) {
			activeEntry = file;
			activeIn = fin;
		}

		if (!recovery && (file.flag&QZEntry.CRC) != 0) return new ChecksumInputStream(fin, new CRC32(), file.crc32&0xFFFFFFFFL);
		return fin;
	}

	final void closeSolidStream() throws IOException {
		if (blockInput != null) {
			blockInput.close();
			blockInput = null;
		}
		activeIn = null;
		activeEntry = null;
	}

	final InputStream getSolidStream(WordBlock b, byte[] pass) throws IOException {
		Source src;
		do {
			src = fpRead;
			if (src == null) {
				src = r.threadSafeCopy();
				src = src.isBuffered()?src:BufferedSource.autoClose(src);
				break;
			}
		} while (!u.compareAndSwapObject(this, FPREAD_OFFSET, src, null));

		return getSolidStream1(b, pass, src, this);
	}
	abstract InputStream getSolidStream1(WordBlock b, byte[] pass, Source src, QZReader that) throws IOException;
}