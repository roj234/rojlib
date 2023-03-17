package roj.archive.qz;

import roj.collect.IntMap;
import roj.text.TextUtil;
import roj.util.ArrayCache;

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

	long size() {
		long s = size;
		for (int i = 0; i < extraSizes.length; i++)
			s += extraSizes[i];
		return s;
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

	private void linearCoder(StringBuilder sb, Object[] coders) {
		Object[] k = new Object[((coders.length+1)<<1)+1];
		k[0] = "Raw";
		k[coders.length+1] = IntMap.UNDEFINED;
		k[coders.length+2] = Long.toString(size());
		k[k.length-1] = Long.toString(uSize);
		for (int i = 0; i < coders.length;i++) {
			String s = coders[i].toString();
			if (i > 0) k[coders.length+2+i] = Long.toString(outSizes[outSizes.length-i]);
			k[i+1] = s;
		}

		TextUtil.prettyTable(sb, "    ", " => ", "  ", k);
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
			}
		}

		private void inc(int len) { outSizes[id] += len; }
	}

	public final class CRC extends FilterOutputStream {
		public CRC(OutputStream out) { super(out); }

		@Override
		public void write(int b) throws IOException {
			out.write(b);
			size++;
		}
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (len > 0) {
				out.write(b, off, len);
				size += len;
			}
		}

		@Override
		public void close() throws IOException {
			// do not dispatch
		}
	}
}

