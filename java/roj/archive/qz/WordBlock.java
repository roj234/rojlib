package roj.archive.qz;

import roj.collect.IntMap;
import roj.text.TextUtil;
import roj.util.ArrayCache;
import roj.util.ArrayUtil;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Roj233
 * @since 2022/3/15 10:32
 */
public final class WordBlock {
	QZCoder[] coder;
	CoderInfo complexCoder;

	long offset, size;
	long[] extraSizes = ArrayCache.LONGS;

	long uSize;
    long[] outSizes = ArrayCache.LONGS;

	/**
	 * bit 1: crc32 of uncompressed data
	 * bit 2: crc32 of compressed data (Deprecated)
	 * new bit2: is complex coder
	 *
	 * bit 3-8: outSize index [if complexCode == null else] complexCoderIndex
	 */
	byte hasCrc;
    int crc;

    int fileCount;
	QZEntry firstEntry;

	public long size() {
		long s = size;
		for (long extraSize : extraSizes) s += extraSize;
		return s;
	}

	public long getOffset() { return offset; }
	public int getFileCount() { return fileCount; }
	public QZEntry getFirstEntry() { return firstEntry; }
	public long getuSize() { return uSize; }

	public boolean hasProcessor(Class<? extends QZCoder> type) {
		if (complexCoder != null) return complexCoder.hasProcessor(type);
		for (QZCoder qzCoder : coder) {
			if (type.isInstance(qzCoder)) return true;
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n")
		  .append("  偏移").append(offset).append(",长度").append(size()).append('\n');
		if (complexCoder == null) linearCoder(sb, coder);
		// ENHANCE: A graph view for complex coder
		else linearCoder(sb, (Object[]) coder);
		sb.append("\n  包含").append(fileCount).append("个文件");

		if ((hasCrc&1) != 0)
			sb.append("\n  UCRC=").append(Integer.toHexString(crc)).append('/');
		return sb.append("\n}").toString();
	}

	private void linearCoder(StringBuilder sb, Object[] coders) {
		int width = coders.length*2 + 1;
		Object[] k = new Object[width*2+1];
		k[0] = "";
		k[width] = IntMap.UNDEFINED;
		for (int i = 0; i < coders.length;i++) {
			int offset = i*2 + 1;
			k[offset] = coders[i];
			k[offset+1] = "";

			k[offset+width] = Long.toString(i == 0 ? size : outSizes[i-1]);
			k[offset+width+1] = "=>";
		}
		k[k.length-2] = "=>";
		k[k.length-1] = Long.toString(uSize);

		TextUtil.prettyTable(sb, "  ", k, " ");
	}

	final class Counter extends FilterOutputStream {
		private final int id;

		Counter(OutputStream out, int id) {
			super(out);
			this.id = id;
		}

		@Override
		public void write(int b) throws IOException {
			out.write(b);
			inc(1);
		}
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (len > 0) {
				out.write(b, off, len);
				inc(len);
			} else if (len < 0) ArrayUtil.checkRange(b, off, len);
		}

		private void inc(int len) { if (id >= 0) outSizes[id] += len; else size += len; }

		@Override
		public void close() throws IOException {
			if (id >= 0) out.close();
		}
	}

	CoderInfo complexCoder() {return complexCoder;}
}