package roj.archive.zip;

import org.jetbrains.annotations.NotNull;
import roj.archive.ArchiveUtils;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.crypt.CRC32;
import roj.io.IOUtil;
import roj.io.source.Source;
import roj.optimizer.FastVarHandle;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.FastFailException;
import roj.util.function.ExceptionalSupplier;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.Deflater;

import static roj.archive.zip.ZipEntryWriter.writeCEN;
import static roj.archive.zip.ZipEntryWriter.writeEND;

/**
 * 支持分卷压缩文件
 * 支持AES、ZipCrypto加密的读写
 * 支持任意编码，支持InfoZip的UTF文件名
 * 支持ZIP64
 * <p>
 * 禁止使用{@link #FLAG_ReadCENOnly}的限制已移除，但对性能的影响有待测试.
 * TODO 支持重命名
 *
 * @author Roj233
 * @since 2021/7/10
 * @revised 2025/12/28
 * @version 4.0
 */
@FastVarHandle
public final class ZipEditor extends ZipFile {
	private final HashSet<ZipUpdate> modified = new HashSet<>();

	public ZipEditor(String name) throws IOException { this(new File(name)); }
	public ZipEditor(File file) throws IOException { this(file, FLAG_RemoveEXT | FLAG_Verify); }
	public ZipEditor(File file, int flags) throws IOException { this(file, flags, StandardCharsets.UTF_8); }
	public ZipEditor(File file, int flags, Charset charset) throws IOException {
		this(ArchiveUtils.tryOpenSplitArchive(file, false), flags, charset);
		if (r.length() > 0) reload();
		else namedEntries = ENTRY_TEMPLATE.create();
		this.file = file;
	}
	public ZipEditor(Source source, int flags, Charset cs) {
		super(source, flags, cs);

		if (cs == StandardCharsets.UTF_8) {
			this.flags |= FLAG_SaveInUTF;
		}
	}

	public void setComment(String str) {
		comment = str == null || str.isEmpty() ? ArrayCache.BYTES : IOUtil.encodeUTF8(str);
		if (comment.length > 65535) {
			comment = Arrays.copyOf(comment, 65535);
			throw new IllegalArgumentException("Comment too long");
		}
	}

	public void setComment(byte[] str) { comment = str == null ? ArrayCache.BYTES : str; }

	/** 如果 data 为 null 那么删除 */
	public ZipUpdate put(String name, DynByteBuf data) {
		ZipUpdate mod = createMod(name);
		mod.data = data;
		mod.setMethod(data != null && data.readableBytes() > 100 ? ZipEntry.DEFLATED : ZipEntry.STORED);
		return mod;
	}
	public ZipUpdate put(String name, ExceptionalSupplier<DynByteBuf, IOException> getData, boolean compress) {
		ZipUpdate mod = createMod(name);
		mod.data = getData;
		mod.setMethod(compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
		return mod;
	}
	public ZipUpdate putStream(String name, ExceptionalSupplier<InputStream, IOException> in, boolean compress) {
		ZipUpdate mod = createMod(name);
		mod.data = in;
		mod.setMethod(compress ? ZipEntry.DEFLATED : ZipEntry.STORED);
		return mod;
	}
	public ZipUpdate createMod(String name) {
		if ((flags & FLAG_ReadOnly) != 0) throw new IllegalStateException("该压缩文件不符合规范，因此无法写入");
		ZipUpdate update = new ZipUpdate(name);
		return modified.intern(update);
	}

	public HashSet<ZipUpdate> getModified() { return modified; }

	@Override
	public void close() throws IOException {
		modified.clear();
		super.close();
	}

	public void save() throws IOException { save(Deflater.DEFAULT_COMPRESSION); }
	@SuppressWarnings("unchecked")
	public void save(int level) throws IOException {
		if (modified.isEmpty()) return;

		truncateRemovedEntriesAndMoveToLastLOCEnd();

		ByteList buf = new ByteList(2048);
		FastFailException dataExceptions = null;

		long precisionModTime = System.currentTimeMillis();
		int modTime = ZipEntry.java2DosTime(precisionModTime);

		var impl = new ZipEntryWriter(r, buf, level);

		// write LOCs
		entries.ensureCapacity(entries.size() + modified.size());
		for (ZipUpdate mod : modified) {
			ZipEntry entry = mod.entry;
			entries.add(entry);

			entry.setEncryptMethod(mod.getEncryptMethod());
			entry.method = (char) mod.getMethod();

			long overrideTime = mod.modificationTime;
			if (overrideTime != -1L) {
				if (overrideTime == 0) {
					entry.pModTime = precisionModTime;
					entry.modTime = modTime;
				} else {
					entry.pModTime = overrideTime;
					entry.modTime = ZipEntry.java2DosTime(overrideTime);
				}
			}

			Object data = mod.data;
			if (data instanceof ExceptionalSupplier) {
				try {
					data = ((ExceptionalSupplier<?, IOException>) data).get();
				} catch (Exception ex) {
					dataExceptions = SUPPRESS(dataExceptions, entry, ex);
					continue;
				}
			}

			InputStream in;
			boolean useZip64 = mod.isZip64();
			if (data instanceof DynByteBuf b) {
				useZip64 = false;
				in = null;
				entry.size = b.readableBytes();
				entry.compressedSize = 0;
				entry.crc32 = CRC32.crc32(b);
			} else {
				in = (InputStream) data;
				if (in.available() == Integer.MAX_VALUE) useZip64 = true;
				entry.size = entry.compressedSize = useZip64 ? U32_MAX : 0;
				entry.crc32 = 0;
			}

			long pos = impl.rawOut.position();
			try {
				impl.beginEntry(entry, in != null, useZip64, mod.password);
				if (in != null) {
					IOUtil.copyStream(in, impl);
				} else {
					impl.write((DynByteBuf) data, true);
				}
				impl.closeEntry();
			} catch (Throwable ex) {
				// 跳过失败的Entry
				impl.entry = null;
				impl.rawOut.seek(pos);

				dataExceptions = SUPPRESS(dataExceptions, entry, ex);
			} finally {
				IOUtil.closeSilently(in);
			}
		}

		sortCENsAndWriteEOCL(buf, r);
		buf.release();

		modified.clear();

		r.setLength(r.position());

		if (dataExceptions != null) throw dataExceptions;
	}

	private void truncateRemovedEntriesAndMoveToLastLOCEnd() throws IOException {
		if (entries.isEmpty()) {
			r.seek(cenOffset);
		} else {
			var keeping = new ArrayList<ZipEntry>();
			var IOCombiner = new Object() {
				int keepingSize = 0;
				long delta = 0;
				long prevEnd = entries.get(0).startPos();
				long position = 0;

				void combine() throws IOException {
					if (keeping.size() > keepingSize) {
						ZipEntry entry = keeping.get(keepingSize);
						long begin = entry.startPos();

						delta += prevEnd - begin;

						long length = 0;
						for (int j = keepingSize; j < keeping.size(); j++) {
							entry = keeping.get(j);

							// 计算extraLenOfLOC和EXTlenOfLOC字段
							if ((flags & FLAG_ReadCENOnly) != 0) {
								validateEntry(r, entry);
							}

							entry.offset += delta;
							length += entry.endPos() - entry.startPos();
						}

						prevEnd = begin + length;

						if (begin != position) r.moveSelf(begin, position, length);
						position += length;

						keepingSize = keeping.size();
					}
				}
			};
			r.seek(IOCombiner.prevEnd);

			ZipUpdate checker = new ZipUpdate(null);

			for (int i = 0; i < entries.size(); i++) {
				ZipEntry entry = entries.get(i);
				checker.name = entry.name;

				ZipUpdate mod = modified.find(checker);
				if (mod == checker) { // 保留
					keeping.add(entry);
				} else {
					IOCombiner.combine();

					if (mod.data == null) { // 删除
						modified.remove(mod);
					} else { // 变化
						checkEmptyDir(mod);
						mod.entry = entry;

						if ((flags & FLAG_SaveInUTF) != 0 && (entry.flags & GP_UFS) == 0) {
							entry.flags |= GP_UFS;
							entry.nameBytes = IOUtil.encodeUTF8(entry.name);
						}
						entry.flags &= GP_UFS| ZipEntry.MZ_PrecisionTime| ZipEntry.MZ_UniPath| ZipEntry.MZ_HostSystem;
					}
				}
			}

			IOCombiner.combine();
			r.seek(IOCombiner.position);

			entries = keeping;
		}

		for (var itr = modified.iterator(); itr.hasNext(); ) {
			ZipUpdate mod = itr.next();
			// 删除不存在的项目，忽略
			if (mod.data == null) itr.remove();
			else if (mod.entry == null) {
				// 创建新的项目
				checkEmptyDir(mod);

				var entry = new ZipEntry(mod.name);
				mod.entry = entry;

				if ((flags & FLAG_SaveInUTF) != 0) {
					entry.flags = GP_UFS;
					entry.nameBytes = IOUtil.encodeUTF8(mod.name);
				} else {
					entry.flags = 0;
					entry.nameBytes = mod.name.getBytes(cs);
				}
			}
		}
	}

	private static void checkEmptyDir(ZipUpdate mod) {
		String name = mod.name;
		if (name.endsWith("/")) throw new IllegalArgumentException("目录不是空的");
	}

	private void sortCENsAndWriteEOCL(ByteList buf, OutputStream out) throws IOException {
		cenOffset = r.position();

		buf.clear();
		int flushLimit = buf.list.length - 256;
		for (int i = 0; i < entries.size(); i++) {
			writeCEN(buf, entries.get(i));
			if (buf.wIndex() > flushLimit) {
				buf.writeToStream(out);
				buf.clear();
			}
		}

		cenLength = r.position()+buf.wIndex()-cenOffset;
		writeEND(buf, cenOffset, cenLength, entries.size(), comment);

		buf.writeToStream(out);
	}

	private static @NotNull FastFailException SUPPRESS(FastFailException dataExceptions, ZipEntry entry, Throwable ex) {
		if (dataExceptions == null)
			dataExceptions = new FastFailException("无法保存"+entry.name+"的数据", ex);
		else dataExceptions.addSuppressed(ex);
		return dataExceptions;
	}

	private File file;
	/**
	 * re-open internal RandomAccessFile, to continue operate
	 */
	public void reopen() throws IOException {
		if (file == null) throw new IOException("不是从文件打开");
		if (r == null) {
			r = ArchiveUtils.tryOpenSplitArchive(file, false);
			var cache = (Source) CACHE.getAndSet(this, r);
			if (cache != r) IOUtil.closeSilently(cache);
		}
	}
}