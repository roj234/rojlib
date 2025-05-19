package roj.compiler.jpp.machine.windows;

import roj.compiler.jpp.machine.NativeExecutable;
import roj.io.IOUtil;
import roj.io.source.FileSource;
import roj.io.source.Source;
import roj.util.ByteList;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Roj233
 * @since 2021/10/15 13:18
 */
public class PEFile extends NativeExecutable {
	long headerOff, headerLikeEndOffset;
	DOSHeader dosHeader;
	PEHeader peHeader;
	long[] dataIndexes;
	List<Section> sections;

	public PEFile(Source src) throws IOException {
		super(src);
		this.dosHeader = new DOSHeader();
		this.peHeader = new PEHeader();
		this.dataIndexes = new long[Table.VALUES.length + 1];
		this.sections = new ArrayList<>();
		if (src.length() > 0) {
			read();
		} else {
			this.headerOff = -1;
		}
	}

	public void read() throws IOException {
		ByteList rb = this.rb;
		read(0, 2);
		if (rb.readChar() != 0x4D5A) {
			throw new IOException("MZ header not found");
		}
		read(60, 4);
		read(headerOff = rb.readUIntLE(), 4);
		if (rb.readInt() != 0x50450000) {
			throw new IOException("PE signature not found at 0x" + Long.toHexString(headerOff));
		}
		read(headerOff + 4, 136);
		peHeader.fromByteArray(this, rb);
		int size = peHeader.dataIndexSize;
		readPlus(-1, size << 3);
		for (int i = 0; i < size; i++) {
			//                  virtual address    |    length
			dataIndexes[i] = rb.readUIntLE() << 32 | rb.readUIntLE();
		}
		while (size < dataIndexes.length) {
			dataIndexes[size++] = 0;
		}
		size = peHeader.sectionCount;
		while (size-- > 0) {
			read(-1, 40);
			Section sec = new Section();
			sec.fromByteArray(this, rb);
			sections.add(sec);
		}
	}

	static final long UNSIGNED_MAX = 4294967296L;

	public int computeChecksum() throws IOException {
		ByteList rb = this.rb;
		rb.clear();
		if (rb.list.length < 4096) rb.ensureCapacity(4096);
		byte[] b = rb.list;
		int cap = b.length;
		cap -= cap & 3;

		src.seek(headerOff + 88);

		long sum = 0L;
		int r;
		while ((r = src.read(rb.list, 0, cap)) > 0) {
			for (int i = 0; i < r; i += 4) {
				long u4 = b[i] & 0xFF | (b[i + 1] & 0xFF) << 8 | (b[i + 2] & 0xFF) << 16 | (b[i + 3] & 0xFFL) << 24;
				sum += u4;
				if (sum > UNSIGNED_MAX) {
					sum = (sum & 0xFFFFFFFFL) + (sum >> 32);
				}
			}
		}
		sum = (sum >> 16) + (sum & 0xFFFFL);
		sum += sum >> 16;
		return (int) ((sum & 0xFFFF) + src.length());
	}

	public void updateChecksum() throws IOException {
		ByteList rb = this.rb;
		rb.clear();
		rb.putIntLE(computeChecksum());
		src.seek(headerOff + 88);
		src.write(rb.list, 0, 4);
	}

	public long getTableIndex(Table type) {
		if (type.ordinal() >= peHeader.dataIndexSize) {
			return 0;
		}
		return dataIndexes[type.ordinal()];
	}

	public void setTable(Table type, ByteList data, boolean doMove) throws IOException {
		long idx = dataIndexes[type.ordinal()];
		long oldLen = idx & 0xFFFFFFFFL;
		int newLen = data.wIndex() - data.arrayOffset();
		if (idx == 0) {
			long pos = this.src.length();
			src.seek(pos);
			src.write(data.list, data.arrayOffset(), newLen);

			PEHeader h = peHeader;
			if (h.dataIndexSize <= type.ordinal()) {
				for (int i = h.dataIndexSize; i < type.ordinal(); i++) {
					dataIndexes[i] = 0;
				}
				h.dataIndexSize = type.ordinal() + 1;
			}
			dataIndexes[type.ordinal()] = (pos << 32) | newLen;
		} else if (newLen == oldLen || (doMove && !isLastTable(idx) && type != Table.CERTIFICATE_TABLE)) {
			if (newLen > oldLen) {
				long from = (idx >>> 32) + oldLen;
				IOUtil.transferFileSelf(src.channel(), from, (idx >>> 32) + newLen, src.length() - from);
			}
			src.seek(idx >>> 32);
			src.write(data.list, data.arrayOffset(), newLen);
			if (newLen < oldLen) {
				long from = (idx >>> 32) + oldLen;
				IOUtil.transferFileSelf(src.channel(), from, (idx >>> 32) + newLen, src.length() - from);
			}
			dataIndexes[type.ordinal()] = (idx & 0xFFFFFFFF00000000L) | newLen;
		} else if (isLastTable(idx)) {
			src.seek(idx >>> 32);
			src.write(data.list, data.arrayOffset(), newLen);
			src.setLength((idx >>> 32) + newLen);
			dataIndexes[type.ordinal()] = (idx & 0xFFFFFFFF00000000L) | newLen;
		} else {
			if (type == Table.CERTIFICATE_TABLE) {
				throw new IOException("数字签名不能被移动");
			}

			byte[] buf = rb.list;
			// noinspection all
			for (int i = 0; i < buf.length; i++) {
				buf[i] = 0;
			}
			src.seek(idx >>> 32);
			while (oldLen > 0) {
				int w = Math.min(buf.length, 0x7FFFFFFF & (int) oldLen);
				src.write(buf, 0, w);
				oldLen -= w;
			}

			long pos = this.src.length();
			src.seek(pos);
			src.write(data.list, data.arrayOffset(), newLen);
			dataIndexes[type.ordinal()] = (pos << 32) | newLen;
		}
	}

	private boolean isLastTable(long idx) throws IOException {
		return src.length() == (idx >>> 32) + (idx & 0xFFFFFFFFL);
	}

	public TableEntry getTable(Table type) {
		throw new UnsupportedOperationException("Not implement yet");
	}

	public List<Section> getSections() {
		return sections;
	}

	public static void main(String[] args) throws IOException {
		PEFile pe = new PEFile(new FileSource(args[0]));
		System.out.println(pe);
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(dosHeader).append('\n').append(peHeader).append('\n');

		sb.append("\n数据表\n  类型      偏移      长度\n");
		for (int i = 0; i < peHeader.dataIndexSize; i++) {
			long table = dataIndexes[i];
			if (table != 0) {
				sb.append("  ").append(Table.VALUES[i].name()).append("    0x").append(Long.toHexString(table >>> 32)).append("    ").append(table & 0xFFFFFFFFL).append('\n');
			}
		}
		sb.append("\n区段\n   名称      虚拟大小   虚拟地址    数据大小    数据偏移    标志位\n");
		List<Section> sections = this.sections;
		for (int i = 0; i < sections.size(); ++i) {
			Section src = sections.get(i);
			sb.append("#")
			  .append(i + 1)
			  .append("   ")
			  .append(src.name)
			  .append("    ")
			  .append(src.getVirtualSize())
			  .append("    0x")
			  .append(Integer.toHexString(src.virtualAddress))
			  .append("    ")
			  .append(src.getRawDataSize())
			  .append("    0x")
			  .append(Integer.toHexString(src.rawDataOffset))
			  .append("    ")
			  .append(src.getCharacteristics())
			  .append('\n');
		}
		return sb.toString();
	}
}