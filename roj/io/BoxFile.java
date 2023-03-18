package roj.io;

import roj.RequireUpgrade;
import roj.collect.LinkedMyHashMap;
import roj.collect.RSegmentTree.Range;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;
import java.io.*;
import java.util.Iterator;
import java.util.Set;

/**
 * Add, Remove, Modify 'Box' File
 *
 * @author Roj234
 * @version 1.0
 * @since 2021/4/5 18:14
 */
@RequireUpgrade
public class BoxFile implements Closeable {
	public final class RandomAccessOutputStream extends OutputStream {
		boolean closed;
		final String name;

		public RandomAccessOutputStream(String name) {
			this.name = name;
		}

		@Override
		public void write(int b) throws IOException {
			if (closed) throw new IOException("Stream closed");
			rf.write(b);
		}

		@Override
		public void write(@Nonnull byte[] b, int off, int len) throws IOException {
			if (closed) throw new IOException("Stream closed");
			rf.write(b, off, len);
		}

		@Override
		public void close() throws IOException {
			if (!closed) endStream();
			closed = true;
		}
	}

	private final UTFCoder uc = new UTFCoder();
	private final LinkedMyHashMap<String, F> infoMap = new LinkedMyHashMap<>();
	private final RandomAccessFile rf;
	private long freeBytes;

	static final int MAGIC = 0x59594453;// ByteList.FOURCC("YYDS");

	static final class F implements Range {
		long offset;
		int length;

		public F(long offset, int length) {
			this.offset = offset;
			this.length = length;
		}

		@Override
		public long startPos() {
			return offset;
		}

		@Override
		public long endPos() {
			return offset + length;
		}

		@Override
		public String toString() {
			return "F{" + "off=" + offset + ", len=" + length + '}';
		}
	}

	public BoxFile(File file) throws IOException {
		rf = new RandomAccessFile(file, "rw");
		if (rf.length() >= 12) {
			load();
		} else {
			rf.setLength(12);
			rf.seek(0);
			rf.writeInt(MAGIC);
			rf.writeLong(0);
		}
	}

	public BoxFile(File file, int initialFreeBytes) throws IOException {
		rf = new RandomAccessFile(file, "rw");
		if (rf.length() < initialFreeBytes) {
			rf.setLength(initialFreeBytes);
			rf.seek(0);
			rf.writeInt(MAGIC);
			rf.writeLong(freeBytes = initialFreeBytes - 4 - 8);
		} else {
			load();
		}
	}

	public void load() throws IOException {
		infoMap.clear();
		rf.seek(0);
		if (MAGIC != rf.readInt()) {
			throw new IOException("Wrong magic number");
		}
		long end = rf.length() - (freeBytes = rf.readLong());
		long pos = 12;

		ByteList bl = uc.byteBuf;
		while (pos < end) {
			int slen = rf.readChar();
			bl.ensureCapacity(slen);
			if (rf.read(bl.list, 0, slen) < slen) {
				throw new EOFException("Truncated data #" + pos + " prev " + infoMap.lastEntry() + ", sl " + slen);
			}
			bl.wIndex(slen);

			pos += 2 + slen;
			slen = rf.readInt();
			F fe = new F(pos + 4, slen);
			infoMap.put(uc.decode(), fe);

			pos += 4 + slen;
			rf.seek(pos);
		}
	}

	public boolean contains(String name) {
		return infoMap.containsKey(name);
	}

	public ByteList get(String name, ByteList list) throws IOException {
		F arr = infoMap.get(name);
		if (arr == null) return null;
		rf.seek(arr.offset);
		list.ensureCapacity(arr.length);
		rf.readFully(list.list, 0, arr.length);
		list.wIndex(arr.length);
		return list;
	}

	public byte[] getBytes(String name) throws IOException {
		uc.byteBuf.clear();
		ByteList bl = get(name, uc.byteBuf);
		if (bl == null) return null;
		return bl.toByteArray();
	}

	public String getUTF(String name) throws IOException {
		uc.byteBuf.clear();
		ByteList bl = get(name, uc.byteBuf);
		if (bl == null) return null;
		return uc.decode(bl);
	}

	public long getOffset(String key) {
		F arr = infoMap.get(key);
		if (arr == null) return -1;
		return arr.offset;
	}

	public boolean remove(String name) throws IOException {
		if (stream != null) endStream();
		F fe = infoMap.get(name);
		if (fe == null) return false;
		int nameBytes = DynByteBuf.byteCountUTF8(name);

		int off = fe.length + nameBytes + 6;
		if (infoMap.lastEntry().v != fe) {
			long dataEnd = fe.offset + fe.length;
			IOUtil.transferFileSelf(rf.getChannel(), dataEnd, fe.offset - nameBytes - 6, rf.length() - freeBytes - dataEnd);

			Iterator<F> itr = infoMap.values().iterator();
			while (itr.hasNext()) {
				if (itr.next() == fe) {
					itr.remove();
					break;
				}
			}
			while (itr.hasNext()) {
				itr.next().offset -= off;
			}
		} else {
			infoMap.remove(name);
		}

		freeBytes += off;
		rf.seek(4);
		rf.writeLong(freeBytes);
		return true;
	}

	public boolean put(String name, ByteList list) throws IOException {
		if (name.length() > 65535) {
			throw new IOException("String length exceed limit 65535");
		}
		if (stream != null) endStream();
		F fe = infoMap.get(name);
		if (fe == null) { // append
			long off = rf.length() - freeBytes;
			long dataLen = list.wIndex() + DynByteBuf.byteCountUTF8(name) + 6;
			if (dataLen > freeBytes) {
				if (freeBytes != 0) {
					rf.seek(4);
					rf.writeLong(freeBytes = 0);
				}
			} else {
				freeBytes -= dataLen;
				rf.seek(4);
				rf.writeLong(freeBytes);
			}

			infoMap.put(name, new F(off + dataLen - list.wIndex(), list.wIndex()));
			rf.seek(off);

			ByteList li = uc.encodeR(name);
			rf.writeShort(li.wIndex());
			rf.write(li.list, 0, li.wIndex());
			rf.writeInt(list.wIndex());
		} else { // replace
			int delta = list.wIndex() - fe.length;
			if (infoMap.lastEntry().v != fe) {
				long prevEnd = fe.offset + fe.length;

				IOUtil.transferFileSelf(rf.getChannel(), prevEnd, fe.offset + list.wIndex(), rf.length() - freeBytes - prevEnd);

				if (delta != 0) {
					Iterator<F> itr = infoMap.values().iterator();
					while (itr.hasNext()) {
						if (itr.next() == fe) break;
					}
					while (itr.hasNext()) {
						itr.next().offset += delta;
					}
				}
			}
			rf.seek(4);
			if (freeBytes < delta) {
				if (freeBytes != 0) {
					rf.writeLong(freeBytes = 0);
				}
			} else {
				freeBytes -= delta;
				rf.writeLong(freeBytes);
			}

			rf.seek(fe.offset - 4);
			rf.writeInt(fe.length = list.wIndex());
		}

		rf.write(list.list, list.arrayOffset(), list.wIndex());
		return fe == null;
	}

	private RandomAccessOutputStream stream;

	public OutputStream streamAppend(String name) throws IOException {
		if (name.length() > 65535) {
			throw new IOException("String length exceed limit 65535");
		}
		if (stream != null) endStream();
		F fe = infoMap.get(name);
		if (fe == null) { // append
			long off = rf.length() - freeBytes;
			rf.seek(off);

			ByteList li = uc.encodeR(name);
			rf.writeShort(li.wIndex());
			rf.write(li.list, 0, li.wIndex());
			rf.writeInt(0);

			int dataLen = li.wIndex() + 6;
			infoMap.put(name, new F(off + dataLen, dataLen));

			return stream = new RandomAccessOutputStream(name);
		} else { // replace
			throw new IOException("Streamize append can only support fresh entry");
		}
	}

	public void endStream() throws IOException {
		if (stream == null) return;
		F fe = infoMap.get(stream.name);
		long ptr = rf.getFilePointer();
		rf.seek(fe.offset - 4);
		if (ptr - fe.offset > Integer.MAX_VALUE) throw new IOException("Data size too large");
		rf.writeInt((int) (ptr - fe.offset));
		stream.closed = true;
		stream = null;
	}

	public Set<String> keys() {
		return infoMap.keySet();
	}

	public void clear() throws IOException {
		rf.setLength(12);
		rf.seek(0);
		rf.writeInt(MAGIC);
		rf.writeLong(0);
		infoMap.clear();
	}

	public void reset() throws IOException {
		rf.seek(0);
		rf.writeInt(MAGIC);
		rf.writeLong(freeBytes = rf.length() - 12);
		infoMap.clear();
	}

	@Override
	public void close() throws IOException {
		if (stream != null) endStream();
		rf.close();
	}
}
