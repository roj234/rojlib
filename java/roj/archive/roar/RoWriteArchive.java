package roj.archive.roar;

import roj.archive.ArchiveUtils;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.collect.IntervalPartition;
import roj.collect.IntervalPartition.Range;
import roj.concurrent.ExceptionalSupplier;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.source.Source;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.function.ObjLongConsumer;
import java.util.zip.Deflater;

/**
 * @author Roj234
 * @since 2025/6/30 9:53
 */
public final class RoWriteArchive extends RoArchive {
	private static final Comparator<RoarEntry> CEN_SORTER = (o1, o2) -> Long.compare(o1.offset, o2.offset);

	private final HashSet<EntryMod> modified = new HashSet<>();

	public RoWriteArchive(File file) throws IOException {
		super(ArchiveUtils.tryOpenSplitArchive(file, false));
		if (r.length() > 0) reload();
		else entries = new ArrayList<>();
	}
	public RoWriteArchive(Source source) { super(source); }

	/** 如果 data 为 null 那么删除 */
	public EntryMod put(String entry, ByteList data) {
		EntryMod mod = createMod(entry);
		mod.compress = data != null && data.readableBytes() > 100;
		mod.data = data;
		return mod;
	}
	public EntryMod put(String entry, ExceptionalSupplier<ByteList, IOException> getData, boolean compress) {
		EntryMod mod = createMod(entry);
		mod.compress = compress;
		mod.data = getData;
		return mod;
	}
	public EntryMod putStream(String entry, ExceptionalSupplier<InputStream, IOException> in, boolean compress) {
		EntryMod mod = createMod(entry);
		mod.compress = compress;
		mod.data = in;
		return mod;
	}
	public EntryMod createMod(String entry) {
		EntryMod file = new EntryMod();
		file.name = entry;
		if (file == (file = modified.find(file))) {
			file.entry = getEntry(entry);
			modified.add(file);
		}
		return file;
	}

	public HashSet<EntryMod> getModified() { return modified; }

	@Override
	public void close() throws IOException {
		modified.clear();
		super.close();
	}

	public void save() throws IOException { save(Deflater.DEFAULT_COMPRESSION); }
	@SuppressWarnings("unchecked")
	public void save(int level) throws IOException {
		if (modified.isEmpty()) return;

		RoarEntry beginEntry = null;

		IntervalPartition<RoarEntry> keepingEntries = new IntervalPartition<>((int) Math.log(entries.size()), false, modified.size());

		for (Iterator<EntryMod> itr = modified.iterator(); itr.hasNext(); ) {
			EntryMod mod = itr.next();

			RoarEntry entry = getEntry(mod.getName());
			if (entry != null) {
				mod.entry = entry;

				if (beginEntry == null || beginEntry.offset > entry.offset) {
					beginEntry = entry;
				}

				if (mod.data == null) {
					entry.nameLen = -1;
					// 删除【删除】类型的EntryMod
					itr.remove();
				} else keepingEntries.add(entry);
			} else if (mod.data == null) {
				// not found, 多半是entries被外部修改了
				itr.remove();
			}
		}

		// at least one file modified
		if (beginEntry != null) {
			for (RoarEntry entry : entries) {
				if (entry.offset >= beginEntry.offset && !keepingEntries.add(entry)) {
					// xor, keep unchanged entries
					keepingEntries.remove(entry);
				}
			}

			r.seek(beginEntry.startPos());

			RoarEntry finalMinFile = beginEntry;
			keepingEntries.mergeConnected(new ObjLongConsumer<>() {
				long delta, prevEnd = finalMinFile.startPos();

				@Override
				public void accept(List<? extends Range> files, long length) {
					RoarEntry file1 = (RoarEntry) files.get(0);
					long begin = file1.startPos();

					// delta一定小于0
					delta += prevEnd - begin;
					prevEnd = begin + length;

					for (int i = 0; i < files.size(); i++) {
						file1 = (RoarEntry) files.get(i);
						file1.offset += delta;
					}

					try {
						r.moveSelf(begin, r.position(), length);
					} catch (IOException e) {
						Helpers.athrow(e);
					}
				}
			});
		} else {
			r.seek(headerOffset);
		}

		OutputStream out = r;

		ByteList bw = new ByteList(2048);
		Deflater def = new Deflater(level, true);

		for (Iterator<EntryMod> itr = modified.iterator(); itr.hasNext(); ) {
			EntryMod mod = itr.next();
			if (mod.data == null) { itr.remove(); continue; }

			RoarEntry entry = mod.entry;
			if (entry == null) {
				entry = mod.entry = new RoarEntry();
				entry.name = IOUtil.encodeUTF8(mod.name);
				entry.nameLen = entry.name.length;
				entry.hash = getHash(entry);
				entries.add(entry);
			}

			out.write(entry.name);

			entry.offset = r.position();
			entry.flags = (char) (mod.compress ? 8 : 0);

			// prepare
			InputStream in;
			Object data = mod.data;
			if (data instanceof ExceptionalSupplier) {
				try {
					data = ((ExceptionalSupplier<?,IOException>) data).get();
				} catch (Exception ex) {
					Logger.FALLBACK.error("无法保存{}的数据", ex, entry.name);
					continue;
				}
			}

			if (data instanceof DynByteBuf b) {
				in = b.isDirect() ? b.asInputStream() : null;
				entry.size = b.readableBytes();
			} else {
				in = (InputStream) data;
			}

			byte[] buf = bw.list;
			if (in == null) {
				ByteList buf1 = (ByteList) data;
				if (entry.flags != 0) {
					def.setInput(buf1.array(), buf1.relativeArrayOffset(), buf1.readableBytes());
					def.finish();
					int w = 0;
					while (!def.finished()) {
						int len = def.deflate(buf);
						w += len;
						out.write(buf, 0, len);
					}
					def.reset();
					entry.compressedSize = w;
				} else {
					out.write(buf1.array(), buf1.relativeArrayOffset(), buf1.readableBytes());
					entry.compressedSize = entry.size;
				}
			} else try {
				if (entry.flags != 0) {
					final int d2 = buf.length / 2;

					int r;
					do {
						r = in.read(buf, 0, d2);
						if (r < 0) break;
						def.setInput(buf, 0, r);
						while (!def.needsInput()) {
							int len = def.deflate(buf, d2, buf.length - d2);
							out.write(buf, d2, len);
						}
					} while (true);
					def.finish();

					while (!def.finished()) {
						int len = def.deflate(buf, 0, buf.length);
						out.write(buf, 0, len);
					}

					entry.size = def.getTotalIn();
					entry.compressedSize = def.getTotalOut();
					def.reset();
				} else {
					IOUtil.copyStream(in, out);
				}
			} catch (Throwable ex) {
				def.end();
				Logger.FALLBACK.error("无法保存{}的数据", ex, entry.name);
			} finally {
				in.close();
			}
			if (out instanceof Finishable f) f.finish();
		}

		bw.clear();

		long beginOffset = 0;

		headerOffset = r.position();
		dataLength = headerOffset - beginOffset;

		// 排序
		int v = bw.list.length - 16;
		Object[] list = entries.toArray();
		Arrays.sort(list, Helpers.cast(CEN_SORTER));
		for (int i = 0; i < list.length; i++) {
			RoarEntry entry = (RoarEntry) list[i];
			bw.putInt(entry.size).putInt(entry.compressedSize).putInt(entry.hash).putShort(entry.nameLen).putShort(entry.flags);

			if (bw.wIndex() > v) {
				bw.writeToStream(out);
				bw.clear();
			}
		}
		modified.clear();

		bw.putInt(entries.size()).putInt((int) dataLength).putInt(HEADER_END);
		bw.writeToStream(out);
		bw._free();

		r.setLength(r.position());

		CacheNode node = OpenedCache.get(r);
		if (node != null) {
			node.map = map;
			node.entries = entries;
		}
	}
}