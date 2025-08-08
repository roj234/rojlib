package roj.archive.roar;

import roj.archive.ArchiveEntry;
import roj.archive.ArchiveFile;
import roj.archive.ArchiveUtils;
import roj.archive.zip.ZipFile;
import roj.collect.ArrayList;
import roj.collect.WeakCache;
import roj.collect.XashMap;
import roj.util.FastFailException;
import roj.io.IOUtil;
import roj.io.source.Source;
import roj.io.source.SourceInputStream;
import roj.math.MathUtils;
import roj.reflect.Unaligned;
import roj.util.ByteList;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;

import static roj.reflect.Unaligned.U;

/**
 * 鸣谢： <a href="https://phoboslab.org/log/2024/09/qop">A Simple Archive Format for Self-Contained Executables</a><br>
 * 这个文件格式参考了很多qop格式
 * @author Roj234
 * @since 2025/7/7 0:42
 */
public class RoArchive implements ArchiveFile {
	Source r, cache;
	private static final long CACHE = Unaligned.fieldOffset(RoArchive.class, "cache");

	static final XashMap<Source, CacheNode> OPENED = WeakCache.shape(CacheNode.class).create();
	static final class CacheNode extends WeakCache<Source> {
		public CacheNode(Source key, XashMap<Source, CacheNode> owner) {super(key, owner);}
		RoarEntry[] map;
		List<RoarEntry> entries;
	}

	RoarEntry[] map;
	List<RoarEntry> entries;
	long headerOffset, dataLength;

	static final int
		// roar
		HEADER_END               = 0x726f6172,
		// compressed_size(4) uncompressed_size(4) hash(4) name_len(2) flags(2)
		ENTRY_SIZE               = 4 + 4 + 4 + 2 + 2;

	public RoArchive(String name) throws IOException { this(new File(name)); }
	public RoArchive(File file) throws IOException {
		r = ArchiveUtils.tryOpenSplitArchive(file, true);
		var node = OPENED.get(r);
		if (node == null) reload();
		else {
			map = node.map;
			entries = node.entries;
		}
	}

	public RoArchive(Source source) {r = source;}

	public final Source source() { return r; }
	public final boolean isClosed() { return r == null; }

	@Override
	public void close() throws IOException {
		Source r1 = r;
		if (r1 == null) return;
		r1.close();
		r = null;

		Source s = (Source) U.getAndSetReference(this, CACHE, null);
		IOUtil.closeSilently(s);
	}

	public final void reload() throws IOException {
		dataLength = headerOffset = 0;

		var buf = new ByteList(ENTRY_SIZE * 128);
		try {
			long off = r.length()-12;
			r.seek(off);
			r.readFully(buf.list, 0, 12);
			buf.wIndex(12);

			int magic = buf.readInt(8);
			if (magic != HEADER_END) throw new IllegalStateException("Invalid magic");

			int entry_count = buf.readInt(0);
			int data_len = buf.readInt(4);

			off = r.length() - 12 - (long) ENTRY_SIZE * entry_count;
			headerOffset = off;
			dataLength = data_len;

			int mask = MathUtils.getMin2PowerOf(entry_count / 2);
			map = new RoarEntry[mask];
			entries = new ArrayList<>(entry_count);
			mask--;

			long dataStart = off - data_len;
			r.seek(off);
			for (int i = 0; i < entry_count; i += 128) {
				int remainCount = ENTRY_SIZE * Math.min(128, entry_count - i);

				buf.clear();
				r.readFully(buf.list, 0, remainCount);
				buf.wIndex(remainCount);

				for (int j = 0; j < remainCount; j += ENTRY_SIZE) {
					RoarEntry entry = new RoarEntry();

					entry.size = buf.readInt();
					entry.compressedSize = buf.readInt();
					entry.hash = buf.readInt();
					entry.nameLen = buf.readUnsignedShort();
					entry.flags = buf.readChar();

					dataStart += entry.nameLen;
					entry.offset = dataStart;
					dataStart += entry.compressedSize;

					var prev = map[entry.hash & mask];
					map[entry.hash & mask] = entry;
					entry.next = prev;

					entries.add(entry);
				}
			}
		} catch (IOException e) {
			IOUtil.closeSilently(this);
			Helpers.athrow(e);
		} finally {
			buf.release();
		}

		var node = new CacheNode(r, OPENED);
		node.map = map;
		node.entries = entries;

		synchronized (OPENED) {
			OPENED.put(r, node);
		}

		map = node.map;
		entries = node.entries;
	}

	protected static int getHash(RoarEntry mod) {return Arrays.hashCode(mod.name);}
	@Override
	public final RoarEntry getEntry(String name) {
		ByteList data = IOUtil.getSharedByteBuf().putUTFData(name);
		int hash = data.hashCode();

		if (map == null) return null;

		int mask = map.length - 1;
		RoarEntry entry = map[hash & mask];
		while (entry != null) {
			noMatch:
			if (entry.hash == hash) {
				byte[] entryName;
				try {
					entryName = readName(entry);
				} catch (IOException e) {
					throw new FastFailException("failed to read name", e);
				}

				if (entryName.length == data.wIndex()) {
					for (int i = 0; i < entryName.length; i++) {
						if (entryName[i] != data.list[i]) break noMatch;
					}
				}
				return entry;
			}
			entry = entry.next;
		}

		return null;
	}
	@Override
	public final Collection<RoarEntry> entries() { return Collections.unmodifiableCollection(entries); }

	private byte[] readName(RoarEntry entry) throws IOException {
		if (entry.name == null) {
			entry.name = new byte[entry.nameLen];
			r.seek(entry.startPos());
			r.readFully(entry.name);
		}
		return entry.name;
	}

	public final InputStream getStream(String name) throws IOException {
		RoarEntry entry = getEntry(name);
		if (entry == null) return null;
		return getStream(entry);
	}
	public final InputStream getStream(ArchiveEntry entry, byte[] pw) throws IOException { return getStream((RoarEntry) entry); }
	public InputStream getStream(RoarEntry entry) throws IOException {
		Source src = (Source) U.getAndSetReference(this, CACHE, null);
		if (src == null) src = r.copy();
		src.seek(entry.offset);

		InputStream in = new SourceInputStream.Shared(src, entry.compressedSize, this, CACHE);
		if ((entry.flags&0xF) == ZipEntry.DEFLATED) in = ZipFile.getCachedInflater(in);
		return in;
	}
}