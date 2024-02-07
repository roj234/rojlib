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

	byte hasCrc;
    int crc;

    int fileCount;
	QZEntry firstEntry;

	Object tmp;

	public long size() {
		long s = size;
		for (int i = 0; i < extraSizes.length; i++)
			s += extraSizes[i];
		return s;
	}

	public long getOffset() { return offset; }
	public int getFileCount() { return fileCount; }
	public QZEntry getFirstEntry() { return firstEntry; }
	public long getuSize() { return uSize; }

	public boolean hasProcessor(Class<? extends QZCoder> type) {
		for (QZCoder qzCoder : coder != null ? coder : (QZCoder[]) tmp) {
			if (type.isInstance(qzCoder)) return true;
		}
		return false;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("{\n")
		  .append("  偏移").append(offset).append(",长度").append(size()).append('\n');
		if (coder != null) linearCoder(sb, coder);
		else linearCoder(sb, (Object[]) tmp);
		sb.append("\n  包含").append(fileCount).append("个文件");

		if ((hasCrc&1) != 0)
			sb.append("\n  UCRC=").append(Integer.toHexString(crc)).append('/');
		return sb.append("\n}").toString();
	}

	// ENHANCE: A graph view for complex coder
	private void linearCoder(StringBuilder sb, Object[] coders) {
		Object[] k = new Object[((coders.length+1)<<1)+1];
		k[0] = "Raw";
		k[coders.length+1] = IntMap.UNDEFINED;
		for (int i = 0; i < coders.length;i++) {
			String s = coders[i].toString();
			k[coders.length+2+i] = Long.toString(i == 0 ? size : outSizes[i-1]);
			k[i+1] = s;
		}
		k[k.length-1] = Long.toString(uSize);

		TextUtil.prettyTable(sb, "  ", k, "    ", " => ");
	}

	public final class Counter extends FilterOutputStream {
		private final int id;

		public Counter(OutputStream out, int id) {
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
}