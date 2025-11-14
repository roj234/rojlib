package roj.asmx.launcher;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.zip.InflateInputStream;
import roj.io.IOUtil;
import roj.io.LimitInputStream;
import roj.util.ByteList;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipException;

final class TinyArchive implements ArchiveFile<ArchiveEntry> {
	static int nextPowerOfTwo(int cap) {
		int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
		return (n < 0) ? 1 : (n >= 1073741824) ? 1073741824 : n + 1;
	}

	final File file;
	DataIn r;

	// 启动早期无法使用FastVarHandle
	private final AtomicInteger parallelRead = new AtomicInteger();

	Entry[] files;
	int count;

	static {InflateInputStream.class.getClass();}

	TinyArchive(File file) throws IOException {
		this.file = file;
		this.r = new DataIn(file);
		r.ignoreClose = true;
	}

	@Override public void close() throws IOException {r.in.close();}
	@Override public void reload() throws IOException {
		if (file.getName().endsWith(".roar")) readRoar();
		else readZip();
	}
	@Override public @Nullable ArchiveEntry getEntry(String name) {return find(name);}
	@Override public @UnmodifiableView Collection<? extends ArchiveEntry> entries() {return new Entries();}
	@Override public InputStream getInputStream(ArchiveEntry entry, byte[] password) throws IOException {return getInputStream(entry.getName());}

	static final class Entry implements ArchiveEntry {
		byte flag;
		int pos, len;
		String name;

		Entry next;

		@Override public String getName() { return name; }
		@Override public boolean isDirectory() {return false;}
		@Override public long getSize() {return len;}
		@Override public long getCompressedSize() {return len;}
	}

	private Entry find(String name) {
		int mask = files.length - 1;
		Entry entry = files[name.hashCode() & mask];
		while (entry != null) {
			if (name.equals(entry.name)) return entry;
			entry = entry.next;
		}
		return null;
	}

	public boolean get(String name, ByteList buf) throws IOException {
		var entry = find(name);
		if (buf == null || entry == null)
			return entry != null;

		var fin = r;
		try {
			if (parallelRead.getAndIncrement() > 0) {
				fin = new DataIn(file);
			}

			fin.seek(entry.pos);

			if (entry.flag != 0) {
				if (entry.flag != 8) throw new IOException("flag error:".concat(name));
				try (var in = InflateInputStream.getInstance(fin)) {
					buf.readStream(in, entry.len);
				}
			} else {
				buf.readStream(fin, entry.len);
			}
			return true;
		} finally {
			parallelRead.getAndDecrement();
			if (fin != r) fin.close();
		}
	}

	public InputStream getInputStream(String name) throws IOException {
		var entry = find(name);
		if (entry == null) return null;

		var in = new FileInputStream(file);
		in.skip(entry.pos);

		if (entry.flag != 0) {
			if (entry.flag != 8) throw new IOException("flag error:".concat(name));
			return InflateInputStream.getInstance(in);
		}

		return new LimitInputStream(in, entry.len, true);
	}

	public void readZip() throws IOException {
		final int MAX_EOCD_SEARCH = 65535 + 22;

		DataIn r = this.r;
		var tmp = new byte[512];

		long fileSize = r.in.length();
		long pos = fileSize - 18;
		long lim = Math.max(0, fileSize - MAX_EOCD_SEARCH);

		long cenOffset = 0;
		long cenLength = 0;

		foundEOCD:
		while (pos > lim+3) {
			int toRead = (int) Math.min(pos - lim, 512);
			long off = pos - toRead;

			r.seek(off);
			IOUtil.readFully(r, tmp, 0, toRead);

			for (int i = toRead - 4; i >= 0; i--) {
				if (tmp[i] != 'P'
						|| tmp[i+1] != 'K'
						|| tmp[i+2] != 0x05
						|| tmp[i+3] != 0x06
				) continue;

				long endPos = off + i;
				long zip64Pos = endPos - 20;

				if (zip64Pos >= 0) {
					r.seek(zip64Pos);

					// 提前检查zip64，减少IO次数
					if (r.readInt() == 0x504b0607) {
						IOUtil.skipFully(r, 4);
						endPos = zip64Pos = r.readLongLE();
						IOUtil.skipFully(r, 8);
					} else {
						zip64Pos = -1;
						IOUtil.skipFully(r, 20);
					}
				} else {
					r.seek(endPos + 4);
				}

				boolean endNeedZip64;
				try {
					IOUtil.skipFully(r, 6);
					count = r.readUnsignedShortLE();
					cenLength = r.readIntLE() & 0xFFFFFFFFL;
					cenOffset = r.readIntLE() & 0xFFFFFFFFL;
					int commentLen = r.readUnsignedShortLE();
					IOUtil.skipFully(r, commentLen);
					endNeedZip64 = cenLength == 0xFFFFFFFFL || cenOffset == 0xFFFFFFFFL || count == 0xFFFF;
				} catch (IOException ignored) {
					continue;
				}

				// Verify EOCD
				if (r.position() != r.in.length()) continue;

				if (endNeedZip64 && zip64Pos >= 0) {
					r.seek(zip64Pos);

					IOUtil.skipFully(r, 28);

					count = Math.toIntExact(r.readLongLE());
					cenLength = r.readLongLE();
					cenOffset = r.readLongLE();
				}

				if (cenOffset + cenLength > endPos) throw new ZipException("invalid END header (bad central directory size)");
				if (endPos - cenLength < 0) throw new ZipException("invalid END header (bad central directory offset)");

				pos = -1;
				break foundEOCD;
			}

			pos = off + 3;
		}

		files = new Entry[nextPowerOfTwo(count)];

		if (count == 0) {
			if (pos >= 0) throw new ZipException("zip END header not found");
			return;
		}

		r.seek(cenOffset);

		long endPos = cenOffset + cenLength;
		int size = 0;
		while (true) {
			if (r.readInt() != 0x504b0102) throw new ZipException("invalid CEN header (bad signature)");

			size++;
			readEntry(r);

			long remain = endPos - r.position();
			if (remain <= 0) {
				if (remain != 0)
					throw new ZipException("invalid END header (bad central directory size)");
				break;
			}
		}

		if (size != count) throw new ZipException("invalid END header (bad central directory count)");

		r.close();
	}
	private void readEntry(DataIn r) throws IOException {
		Entry v = new Entry();

		r.skip(4);

		int flags = r.readUnsignedShortLE();
		int method = r.readUnsignedShortLE();

		r.skip(8);
		v.len = r.readIntLE(); //cSize
		int uSize = r.readIntLE();
		int nameLen = r.readUnsignedShortLE();
		int toSkip = r.readUnsignedShortLE()+r.readUnsignedShortLE();
		r.skip(8);
		long locPos = r.readIntLE()&0xFFFFFFFFL;

		v.name = r.readUTF(nameLen);
		r.skip(toSkip);

		long off = r.in.getFilePointer();

		r.in.seek(locPos + 28);
		toSkip = r.in.read() | (r.in.read() << 8);
		assert toSkip >= 0;

		v.pos = (int) (locPos + 30 + nameLen + toSkip);

		r.in.seek(off);

		if ((((flags&1)|method) & ~8) != 0) {
			v.flag = 1;
		} else {
			v.flag = (byte) method;
			if (method == 8)
				v.len = uSize;
		}

		if (!v.name.endsWith("/")) {
			int mask = files.length-1;
			int i = v.name.hashCode()&mask;
			v.next = files[i];
			files[i] = v;
		}
	}

	private static final int
			HEADER_END               = 0x726f6172,
			ENTRY_SIZE               = 4 + 4 + 4 + 2 + 2;

	public void readRoar() throws IOException {
		var meta = r;

		long off = meta.in.length() -12;
		meta.seek(off);

		var data = new DataIn(file);

		int entry_count = meta.readInt();
		int data_len = meta.readInt();
		int magic = meta.readInt();
		if (magic != HEADER_END) throw new IllegalStateException("Invalid magic");

		count = entry_count;
		off = meta.in.length() - 12 - (long) ENTRY_SIZE * entry_count;

		int mask = nextPowerOfTwo(entry_count / 2);
		files = new Entry[mask];
		mask--;

		int dataStart = (int) (off - data_len);
		meta.seek(off);
		for (int i = 0; i < entry_count; i++) {
			Entry entry = new Entry();

			meta.readInt();
			entry.len = meta.readInt();
			int hash = meta.readInt();
			int nameLen = meta.readUnsignedShort();
			entry.flag = (byte) meta.readUnsignedShort();

			String name = data.readUTF(nameLen);
			entry.name = name;

			dataStart += nameLen;
			entry.pos = dataStart;
			dataStart += entry.len;

			data.skip(entry.len);

			var prev = files[name.hashCode() & mask];
			files[name.hashCode() & mask] = entry;
			entry.next = prev;
		}
	}

	private class Entries extends AbstractCollection<ArchiveEntry> implements Iterator<ArchiveEntry> {
		Entry current;
		int remain;
		int index;

		@Override public int size() {return count;}
		@Override public Iterator<ArchiveEntry> iterator() {remain = count;return this;}
		@Override public boolean hasNext() {return remain > 0;}
		@Override public ArchiveEntry next() {
			if (remain == 0) throw new NoSuchElementException();

			if (current != null) {
				current = current.next;
			}
			while (current == null) {
				current = files[index++];
			}

			--remain;
			return current;
		}
	}
}
