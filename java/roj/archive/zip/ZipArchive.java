package roj.archive.zip;

import roj.archive.ArchiveUtils;
import roj.collect.MyHashSet;
import roj.collect.RSegmentTree;
import roj.collect.RSegmentTree.Range;
import roj.concurrent.ExceptionalSupplier;
import roj.crypt.CRC32;
import roj.crypt.CipherOutputStream;
import roj.io.Finishable;
import roj.io.IOUtil;
import roj.io.source.Source;
import roj.text.logging.Logger;
import roj.util.ArrayCache;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ObjLongConsumer;
import java.util.zip.Deflater;
import java.util.zip.ZipException;

import static roj.archive.zip.ZEntry.MZ_NoCrc;

/**
 * 支持分卷压缩文件
 * 支持AES、ZipCrypto加密的读写
 * 支持任意编码，支持InfoZip的UTF文件名
 * 支持ZIP64
 * <p>
 * 注意事项：ZipArchive禁止使用FLAG_BACKWARD_READ标记，查看{@link ZEntry#setEndPos(long)}和{@link ZipFile#initDataOffset(Source, ZEntry)}以获取详细信息
 *
 * @author Roj233
 * @since 2021/7/10 17:09
 */
public final class ZipArchive extends ZipFile {
	private static final Comparator<ZEntry> CEN_SORTER = (o1, o2) -> Long.compare(o1.offset, o2.offset);

	private final MyHashSet<EntryMod> modified = new MyHashSet<>();

	public ZipArchive(String name) throws IOException { this(new File(name)); }
	public ZipArchive(File file) throws IOException { this(file, FLAG_KILL_EXT|FLAG_VERIFY); }
	public ZipArchive(File file, int flag) throws IOException { this(file, flag, StandardCharsets.UTF_8); }
	public ZipArchive(File file, int flag, Charset charset) throws IOException {
		super(ArchiveUtils.tryOpenSplitArchive(file, false), flag & ~FLAG_BACKWARD_READ, charset);
		if (r.length() > 0) reload();
		this.file = file;
	}
	public ZipArchive(Source source, int flag, Charset cs) { super(source, flag & ~FLAG_BACKWARD_READ, cs); }

	public void setComment(String str) {
		comment = str == null || str.isEmpty() ? ArrayCache.BYTES : IOUtil.encodeUTF8(str);
		if (comment.length > 65535) {
			comment = Arrays.copyOf(comment, 65535);
			throw new IllegalArgumentException("Comment too long");
		}
	}

	public void setComment(byte[] str) { comment = str == null ? ArrayCache.BYTES : str; }

	/** 如果 data 为 null 那么删除 */
	public EntryMod put(String entry, ByteList data) {
		EntryMod mod = createMod(entry);
		mod.flag = (byte) (data != null && data.readableBytes() > 100 ? EntryMod.COMPRESS : 0);
		mod.data = data;
		return mod;
	}
	public EntryMod put(String entry, ExceptionalSupplier<ByteList, IOException> getData, boolean compress) {
		EntryMod mod = createMod(entry);
		mod.flag = (byte) (compress ? EntryMod.COMPRESS : 0);
		mod.data = getData;
		return mod;
	}
	public EntryMod putStream(String entry, InputStream in, boolean compress) {
		EntryMod mod = createMod(entry);
		mod.flag = (byte) (compress ? EntryMod.COMPRESS : 0);
		mod.data = in;
		return mod;
	}
	public EntryMod putStream(String entry, ExceptionalSupplier<InputStream, IOException> in, boolean compress) {
		EntryMod mod = createMod(entry);
		mod.flag = (byte) (compress ? EntryMod.COMPRESS : 0);
		mod.data = in;
		return mod;
	}
	public EntryMod createMod(String entry) {
		EntryMod file = new EntryMod();
		file.name = entry;
		if (file == (file = modified.find(file))) {
			if ((flags & FLAG_HAS_ERROR) != 0) throw new IllegalStateException("该压缩文件不符合规范，因此无法写入");

			file.entry = getEntry(entry);
			modified.add(file);
		}
		return file;
	}

	public MyHashSet<EntryMod> getModified() { return modified; }

	@Override
	public void close() throws IOException {
		modified.clear();
		super.close();
	}

	public void save() throws IOException { save(Deflater.DEFAULT_COMPRESSION); }
	@SuppressWarnings("unchecked")
	public void save(int level) throws IOException {
		if (modified.isEmpty() || (flags&FLAG_HAS_ERROR) != 0) return;
		getEntry(null);

		Deflater def = new Deflater(level, true);

		ZEntry minFile = null;

		RSegmentTree<ZEntry> uFile = new RSegmentTree<>((int) Math.log(namedEntries.size()), false, modified.size());

		for (Iterator<EntryMod> itr = modified.iterator(); itr.hasNext(); ) {
			EntryMod file = itr.next();
			checkName(file);

			ZEntry o = namedEntries.get(file.name);
			if (o != null) {
				if (minFile == null || minFile.offset > o.offset) {
					minFile = o;
				}

				if (file.data == null) {
					namedEntries.removeKey(file.name);
					// 删除【删除】类型的EntryMod
					itr.remove();
				} else uFile.add(o);
			} else if (file.data == null) {
				// not found, 多半是entries被外部修改了
				itr.remove();
			}

			file.entry = o;
		}

		// write linear EFile header
		if (minFile != null) {
			for (ZEntry file : namedEntries) {
				if (file.offset >= minFile.offset && !uFile.add(file)) { // ^=
					uFile.remove(file);
				}
			}

			r.seek(minFile.startPos());

			ZEntry finalMinFile = minFile;
			uFile.mergeConnected(new ObjLongConsumer<>() {
				long delta, prevEnd = finalMinFile.startPos();

				@Override
				public void accept(List<? extends Range> files, long length) {
					ZEntry file1 = (ZEntry) files.get(0);
					long begin = file1.startPos();

					// delta一定小于0
					delta += prevEnd - begin;
					prevEnd = begin + length;

					for (int i = 0; i < files.size(); i++) {
						file1 = (ZEntry) files.get(i);
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
			r.seek(cDirOffset);
		}

		OutputStream out = r;

		ByteList bw = new ByteList(2048);

		long precisionModTime = System.currentTimeMillis();
		int modTime = ZEntry.java2DosTime(precisionModTime);

		// write LOCs
		for (Iterator<EntryMod> itr = modified.iterator(); itr.hasNext(); ) {
			EntryMod mf = itr.next();
			if (mf.data == null) {
				itr.remove();
				continue;
			}

			ZEntry e = mf.entry;
			if (e == null) {
				e = mf.entry = new ZEntry();
				namedEntries.put(e.name = mf.name, e);
				if ((flags & FLAG_FORCE_UTF) != 0 || (mf.flag & EntryMod.UFS) != 0) {
					e.flags = GP_UTF;
					e.nameBytes = IOUtil.encodeUTF8(mf.name);
				} else {
					e.flags = 0;
					e.nameBytes = mf.name.getBytes(cs);
				}
			} else if ((mf.flag & EntryMod.UFS) != 0 && (e.flags & GP_UTF) == 0) {
				e.flags = GP_UTF;
				e.nameBytes = IOUtil.encodeUTF8(e.name);
			} else {
				e.flags = 0; // clear
			}

			e.prepareWrite(mf.cryptType);
			e.offset = r.position() + 30 + e.nameBytes.length;
			e.method = (char) (mf.flag & EntryMod.COMPRESS);
			if ((mf.flag & EntryMod.KEEP_TIME) == 0) {
				e.pModTime = precisionModTime;
				e.modTime = modTime;
			}

			// prepare
			boolean sizeIsKnown;
			InputStream in;
			Object data = mf.data;
			if (data instanceof ExceptionalSupplier) {
				try {
					data = ((ExceptionalSupplier<?,IOException>) data).get();
				} catch (Exception ex) {
					Logger.FALLBACK.error("无法保存{}的数据", ex, e.name);
					continue;
				}
			}

			if (data instanceof DynByteBuf b) {
				mf.flag &= ~EntryMod.LARGE;
				if (b.isDirect()) {
					in = b.asInputStream();
					e.crc32 = CRC32.crc32(b.address(), b.readableBytes());
				} else {
					in = null;
					e.crc32 = CRC32.crc32(b.array(), b.relativeArrayOffset(), b.readableBytes());
				}

				sizeIsKnown = true;
				e.cSize = 0;
				e.uSize = b.readableBytes();
			} else {
				in = (InputStream) data;
				if (mf.cryptType == CRYPT_ZIP2) e.flags |= GP_HAS_EXT;
				if (in.available() == Integer.MAX_VALUE) mf.flag |= EntryMod.LARGE;
				e.cSize = e.uSize = mf.large() ? U32_MAX : 0;
				e.crc32 = 0;
				sizeIsKnown = false;
			}

			long offset = r.position() + 14;
			writeLOC(out, bw, e);

			ZipAES za = null;
			OutputStream cout = out;
			if (mf.cryptType != CRYPT_NONE) {
				if (mf.cryptType == CRYPT_ZIP2) {
					ZipCrypto c = new ZipCrypto();
					c.init(ZipCrypto.ENCRYPT_MODE, mf.pass);
					cout = new CipherOutputStream(cout, c);

					byte[] rnd = new byte[12];
					ThreadLocalRandom.current().nextBytes(rnd);
					// check byte
					rnd[11] = (byte) (sizeIsKnown ? e.crc32 >>> 24 : e.modTime >>> 8);
					cout.write(rnd);
				} else {
					za = new ZipAES();
					try {
						za.init(ZipAES.ENCRYPT_MODE, mf.pass);
					} catch (InvalidKeyException ignored) {}
					za.sendHeaders(out);

					cout = new CipherOutputStream(cout, za);
				}
			}

			byte[] buf = bw.list;
			if (in == null) {
				ByteList buf1 = (ByteList) data;
				if (e.method != 0) {
					def.setInput(buf1.array(), buf1.relativeArrayOffset(), buf1.readableBytes());
					def.finish();
					int w = 0;
					while (!def.finished()) {
						int len = def.deflate(buf);
						w += len;
						cout.write(buf, 0, len);
					}
					def.reset();
					e.cSize = w;
				} else {
					cout.write(buf1.array(), buf1.relativeArrayOffset(), buf1.readableBytes());
					e.cSize = e.uSize;
				}
			} else try {
				int crc = CRC32.initial;
				if (e.method != 0) {
					final int d2 = buf.length / 2;

					int r;
					do {
						r = in.read(buf, 0, d2);
						if (r < 0) break;
						def.setInput(buf, 0, r);
						crc = CRC32.update(crc, buf, 0, r);
						while (!def.needsInput()) {
							int len = def.deflate(buf, d2, buf.length - d2);
							cout.write(buf, d2, len);
						}
					} while (true);
					def.finish();

					while (!def.finished()) {
						int len = def.deflate(buf, 0, buf.length);
						cout.write(buf, 0, len);
					}

					e.uSize = def.getBytesRead();
					e.cSize = def.getBytesWritten();
					def.reset();
				} else {
					long sum = 0;
					int r;
					while (true) {
						r = in.read(buf);
						if (r < 0) break;
						crc = CRC32.update(crc, buf, 0, r);
						cout.write(buf, 0, r);
						sum += r;
					}
					e.uSize = e.cSize = sum;
				}
				e.crc32 = CRC32.finish(crc);
			} catch (Throwable ex) {
				def.end();
				Logger.FALLBACK.error("无法保存{}的数据", ex, e.name);
			} finally {
				in.close();
			}
			if (cout instanceof Finishable f) f.finish();

			switch (e.getEncryptType()) {
				case CRYPT_AES, CRYPT_AES2:
					// 16 + 2 + 10
					e.cSize += 28;
					za.sendTrailers(out);
				break;
				case CRYPT_ZIP2:
					e.cSize += 12;
					if (!sizeIsKnown) writeEXT(out, bw, e);
				break;
			}

			if ((Math.max(e.cSize, e.uSize) >= U32_MAX) && !mf.large())
				throw new ZipException("Zip64预测失败，对于'可能'超过4GB大小的文件,请设置它的large标志位或令in.available返回Integer.MAX_VALUE");

			long pos = r.position();
			// backward, 文件就是这点好
			// 不想再搞什么提前算然后写的差分了
			r.seek(offset);

			e.offset += e.extraLenOfLOC;
			r.writeInt(Integer.reverseBytes(e.getCRC32FW()));

			if (!mf.large()) {
				r.writeInt(Integer.reverseBytes((int) e.cSize));
				r.writeInt(Integer.reverseBytes((int) e.uSize));
			} else {
				// update ZIP64 (in extra region)
				r.seek(offset+20+e.nameBytes.length);
				r.writeLong(Long.reverseBytes(e.uSize));
				r.writeLong(Long.reverseBytes(e.cSize));
			}

			r.seek(pos);
		}

		bw.clear();

		cDirOffset = r.position();

		// 排序CEN
		int v = bw.list.length - 256;
		// 原方法更慢，离大谱
		Object[] list = namedEntries.toArray();
		Arrays.sort(list, Helpers.cast(CEN_SORTER));
		for (int i = 0; i < list.length; i++) {
			writeCEN(bw, (ZEntry) list[i]);
			if (bw.wIndex() > v) {
				bw.writeToStream(out);
				bw.clear();
			}
		}
		modified.clear();

		cDirLen = r.position()+bw.wIndex()-cDirOffset;
		cDirTotal = namedEntries.size();
		writeEND(bw, cDirOffset, cDirLen, cDirTotal, comment, r.position()+bw.wIndex());

		bw.writeToStream(out);
		bw._free();

		r.setLength(r.position());
	}

	private File file;
	/**
	 * re-open internal RandomAccessFile, to continue operate
	 */
	public void reopen() throws IOException {
		if (file == null) throw new IOException("不是从文件打开");
		if (r == null) {
			r = ArchiveUtils.tryOpenSplitArchive(file, false);
			fpRead = null;
		}
	}

	private static void checkName(EntryMod entry) {
		String name = entry.name;
		if (name.contains("\\") || name.startsWith("/")) throw new IllegalArgumentException("名称"+name+"不合法");
		if (name.endsWith("/") && entry.data != null) throw new IllegalArgumentException("目录不是空的");
	}

	static void writeLOC(OutputStream os, ByteList buf, ZEntry file) throws IOException {
		buf.clear();
		buf.putInt(HEADER_LOC)
		   .putShortLE(file.getVersionFW())
		   .putShortLE(file.flags)
		   .putShortLE(file.getMethodFW())
		   .putIntLE(file.modTime)
		   .putIntLE(file.getCRC32FW())
		   .putIntLE((int) file.cSize)
		   .putIntLE((int) file.uSize)
		   .putShortLE(file.nameBytes.length)
		   .putShortLE(0) // extra
		   .put(file.nameBytes);
		file.writeLOCExtra(buf, buf.wIndex(), buf.wIndex() - file.nameBytes.length - 2);

		buf.writeToStream(os);
	}
	private static void writeEXT(OutputStream os, ByteList buf, ZEntry file) throws IOException {
		if ((file.flags & GP_HAS_EXT) == 0) throw new ZipException("Not has ext set");
		assert (file.mzFlag & MZ_NoCrc) == 0;

		buf.clear();
		buf.putInt(HEADER_EXT).putIntLE(file.crc32);
		if (file.cSize >= U32_MAX | file.uSize >= U32_MAX) {
			buf.putLongLE(file.cSize).putLongLE(file.uSize);
			file.EXTLenOfLOC = 24;
		} else {
		   buf.putIntLE((int) file.cSize).putIntLE((int) file.uSize);
		   file.EXTLenOfLOC = 16;
		}
		buf.writeToStream(os);
	}
	static void writeCEN(ByteList buf, ZEntry file) {
		int extLenOff = buf.wIndex() + 30;
		buf.putInt(HEADER_CEN)
		   .putShortLE(VER_MZF)
		   .putShortLE(file.getVersionFW())
		   .putShortLE(file.flags)
		   .putShortLE(file.getMethodFW())
		   .putIntLE(file.modTime).putIntLE(file.getCRC32FW())
		   .putIntLE((int) file.cSize).putIntLE((int) file.uSize)
		   .putShortLE(file.nameBytes.length)
		   .putShortLE(0) // ext
		   .putShortLE(0) // comment
		   .putShortLE(0) // disk
		   .putShortLE(file.internalAttr).putIntLE(file.externalAttr)
		   .putIntLE((int) file.startPos())
		   .put(file.nameBytes);
		int extOff = buf.wIndex();
		file.writeCENExtra(buf, extLenOff);
		buf.putShortLE(extLenOff, buf.wIndex()-extOff);
	}
	static void writeEND(ByteList util, long cDirOffset, long cDirLen, int cDirTotal, byte[] comment, long position) {
		boolean zip64 = false;
		int co;
		if (cDirOffset >= U32_MAX) {
			co = (int) U32_MAX;
			zip64 = true;
		} else {
			co = (int) cDirOffset;
		}

		int cl;
		if (cDirLen >= U32_MAX) {
			cl = (int) U32_MAX;
			zip64 = true;
		} else {
			cl = (int) cDirLen;
		}

		int count = cDirTotal;
		if (count >= 0xFFFF) {
			count = 0xFFFF;
			zip64 = true;
		}

		if (zip64) {
			util.putInt(HEADER_ZIP64_END).putLongLE(44).putShortLE(45).putShortLE(45) // size, ver, ver
				.putIntLE(0).putIntLE(0) // disk id, attr begin id
				.putLongLE(count).putLongLE(count) // disk entries, total entries
				.putLongLE(cDirLen).putLongLE(cDirOffset)

				.putInt(HEADER_ZIP64_END_LOCATOR).putInt(0) // eof disk id
				.putLongLE(position).putIntLE(1); // disk in total
		}

		util.putInt(HEADER_END)
			.putShortLE(0)
			.putShortLE(0)
			.putShortLE(count)
			.putShortLE(count)
			.putIntLE(cl)
			.putIntLE(co)
			.putShortLE(comment.length)
			.put(comment);
	}
}