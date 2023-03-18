package roj.archive.qz;

import roj.collect.IntMap;
import roj.crypt.CRCAny;
import roj.text.TextUtil;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Roj233
 * @since 2022/3/15 10:32
 */
class WordBlock4W {
	QZCoder[] sortedCoders;

	long uSize;
    long[] outSizes;

	long size;

	byte hasCrc;
    int crc, cCrc;

    int fileCount;

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("字块M{\n");
		myToString(sb);
		sb.append("\n  包含").append(fileCount).append("个文件");

		if ((hasCrc&3) != 0)
			sb.append("\n  CRC=").append((hasCrc&1)==0?"~":Integer.toHexString(crc)).append('/').append((hasCrc&2)==0?"~":Integer.toHexString(cCrc));
		return sb.append("\n}").toString();
	}
	protected void myToString(StringBuilder sb) {
		Object[] k = new Object[((sortedCoders.length+1)<<1)+1];
		k[0] = "Raw";
		k[sortedCoders.length+1] = IntMap.UNDEFINED;
		k[sortedCoders.length+2] = Long.toString(size);
		k[k.length-1] = Long.toString(uSize);
		for (int i = 0; i < sortedCoders.length;i++) {
			String s = sortedCoders[i].toString();
			if (i > 0) k[sortedCoders.length+2+i] = Long.toString(outSizes[outSizes.length-i]);
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

		private void inc(int len) {
			outSizes[id] += len;
		}
	}

	public final class CRC extends FilterOutputStream {
		public CRC(OutputStream out) {
			super(out);
			cCrc = CRCAny.CRC_32.defVal();
		}

		@Override
		public void write(int b) throws IOException {
			out.write(b);
			cCrc = CRCAny.CRC_32.update(cCrc, b);
			size++;
		}
		@Override
		public void write(byte[] b, int off, int len) throws IOException {
			if (len > 0) {
				out.write(b, off, len);
				cCrc = CRCAny.CRC_32.update(cCrc, b, off, len);
				size += len;
			}
		}

		@Override
		public void close() throws IOException {
			// do not dispatch
		}
	}
}

