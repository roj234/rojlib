package roj.archive.zip;

import roj.archive.ArchiveEntry;
import roj.collect.RSegmentTree;
import roj.crypt.CRC32s;
import roj.text.DateParser;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.nio.file.attribute.FileTime;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;

import static roj.archive.zip.ZipFile.*;

/**
 * @author Roj234
 * @since 2023/3/14 0:43
 */
public class ZEntry implements RSegmentTree.Range, ArchiveEntry, Cloneable {
	//00: no compression
	//08: deflated
	//14: LZMA
	char method;

	int modTime;
	long pModTime, pAccTime, pCreTime;

	int crc32;
	long cSize, uSize;

	//Bit 0: apparent ASCII/text file
	public char internalAttr;
	//vendor specific
	public int externalAttr;

	char flags;

	String name;
	byte[] nameBytes;

	byte mzFlag;
	static final byte
		MZ_BACKWARD = 1,
		MZ_AES = 2,
		MZ_NoCrc = 4,
		MZ_PrTime = 8,
		MZ_UniPath = 16,
		MZ_ERROR = 32;

	/** 数据起始 */
	long offset;
	char extraLenOfLOC;
	byte EXTLenOfLOC;

	private ZEntry next;

	public ZEntry(String name) {
		this.name = name;
		this.method = 8;
	}

	ZEntry() {}

	public String getName() { return name; }
	public boolean isDirectory() { return name.endsWith("/"); }

	public long getOffset() { return offset; }
	@Override
	public final long startPos() { return offset - 30 - nameBytes.length - extraLenOfLOC; }
	@Override
	public final long endPos() { return offset + cSize + EXTLenOfLOC; }
	final void setEndPos(long pos) {
		EXTLenOfLOC = (byte) (pos - cSize - offset);
	}

	// 给加密用的
	final int getMethodFW() { return (mzFlag & MZ_AES) != 0 ? 99 : method; }
	final int getVersionFW() {
		if ((mzFlag & MZ_AES) != 0) return ZIP_AES;
		if (uSize > U32_MAX || cSize > U32_MAX || offset > U32_MAX) return ZIP_64;
		return method == ZipEntry.DEFLATED ? ZIP_DEFLATED : ZIP_STORED;
	}
	final int getCRC32FW() { return (mzFlag & MZ_NoCrc) != 0 ? 0 : crc32; }
	// 给加密用的 end

	public final long getSize() { return uSize; }
	public final long getCompressedSize() { return cSize; }

	public final long getAccessTime() { return winTime2JavaTime(pAccTime); }
	public final long getCreationTime() { return winTime2JavaTime(pCreTime); }
	public final long getModificationTime() { return pModTime == 0 ? dos2JavaTime(modTime) : winTime2JavaTime(pModTime); }

	public final FileTime getPrecisionAccessTime() { return pAccTime == 0 ? null : winTime2FileTime(pAccTime); }
	public final FileTime getPrecisionCreationTime() { return pCreTime == 0 ? null : winTime2FileTime(pCreTime); }
	public final FileTime getPrecisionModificationTime() { return pModTime == 0 ? winTime2FileTime(java2WinTime(dos2JavaTime(modTime))) : winTime2FileTime(pModTime); }

	public final boolean hasAccessTime() { return pAccTime != 0; }
	public final boolean hasCreationTime() { return pCreTime != 0; }
	public final boolean hasModificationTime() { return modTime != 0 || pModTime != 0; }

	public final void setAccessTime(long t) { pAccTime = t == 0 ? 0 : java2WinTime(t); }
	public final void setCreationTime(long t) { pCreTime = t == 0 ? 0 : java2WinTime(t); }
	public final void setModificationTime(long t) {
		if (t == 0) {
			modTime = 0;
			pModTime = 0;
		} else {
			modTime = java2DosTime(t);
			pModTime = t;
		}
	}
	public final void setPrecisionAccessTime(FileTime t) { pAccTime = t == null ? 0 : fileTime2WinTime(t); mzFlag |= MZ_PrTime; }
	public final void setPrecisionCreationTime(FileTime t) { pCreTime = t == null ? 0 : fileTime2WinTime(t); mzFlag |= MZ_PrTime; }
	public final void setPrecisionModificationTime(FileTime t) { pModTime = t == null ? 0 : fileTime2WinTime(t); modTime = t == null ? 0 : java2DosTime(t.toMillis()); mzFlag |= MZ_PrTime; }

	public final int getMethod() { return method; }
	public final void setMethod(int m) { this.method = (char) m; }

	public final int getCrc32() { return crc32; }

	public final boolean isEncrypted() { return (flags & GP_ENCRYPTED) != 0; }
	public final int getEncryptType() {
		if ((flags & GP_ENCRYPTED) == 0) return CRYPT_NONE;
		if ((flags & GP_STRONG_ENC) != 0) return /*CRYPT_UNKNOWN*/-1;
		if ((mzFlag & MZ_AES) != 0) return (mzFlag & MZ_NoCrc) != 0 ? CRYPT_AES2 : CRYPT_AES;
		return CRYPT_ZIP2;
	}

	public final int getGeneralPurposeFlag() { return flags; }
	public final void setGeneralPurposeFlag(int flag) { flags = (char) flag; }

	@SuppressWarnings("fallthrough")
	final void prepareWrite(int crypt) {
		crc32 = 0;
		flags &= GP_UTF;
		mzFlag = 0;

		if (crypt != CRYPT_NONE) {
			flags |= GP_ENCRYPTED;
			switch (crypt) {
				case CRYPT_ZIP2: break;
				case CRYPT_AES2: mzFlag |= MZ_NoCrc;
				case CRYPT_AES: mzFlag |= MZ_AES; break;
				default: throw new IllegalArgumentException("未知的加密方式: "+crypt);
			}
		}
	}

	final void readLOCExtra(ZipFile o, ByteList buf) {
		while (buf.readableBytes() > 4) {
			int id = buf.readUShortLE();
			int len = buf.readUShortLE();
			int end = buf.rIndex + len;
			switch (id) {
				case 0x5455: // high precision timestamp
					read5455ExtTime(buf, len);
					break;
				case 0x0001:
					// LOC extra zip64 entry MUST include BOTH original
					// and compressed file size fields.
					// If invalid zip64 extra fields, simply skip. Even
					// it's rare, it's possible the entry size happens to
					// be the magic value and it "accidently" has some
					// bytes in extra match the id.
					if (len >= 16) {
						uSize = buf.readLongLE();
						cSize = buf.readLongLE();
					}
					break;
				case 0x7075: // Info-ZIP Unicode Path
					readUnicodePath(buf, len, o);
					break;
				case 0x9901: // AE-x encryption structure
					readAES(buf, len);
					break;
			}
			buf.rIndex = end;
		}
	}
	final void writeLOCExtra(ByteList buf, int extOff, int extLenOff) {
		if (cSize >= U32_MAX || uSize >= U32_MAX) {
			if (uSize > U32_MAX) buf.putIntLE(extLenOff-6, (int) U32_MAX);
			if (cSize > U32_MAX) buf.putIntLE(extLenOff-10, (int) U32_MAX);

			buf.putShortLE(0x0001).putShortLE(16).putLongLE(uSize).putLongLE(cSize);
		}

		if ((mzFlag & MZ_AES) != 0) {
			writeAES(buf);
			if ((mzFlag & MZ_NoCrc) != 0) buf.putIntLE(extLenOff-14, 0);
		}

		if ((flags & GP_UTF) == 0 && (mzFlag & MZ_UniPath) != 0) {
			writeUnicodePath(buf);
		}

		buf.putShortLE(extLenOff, extraLenOfLOC = (char) (buf.wIndex() - extOff));
	}

	final long readCENExtra(ZipFile o, ByteList buf, long header) {
		while (buf.readableBytes() > 4) {
			int id = buf.readUShortLE();
			int len = buf.readUShortLE();
			int end = buf.rIndex + len;
			switch (id) {
				case 0x5455: // Extended timestamp
					read5455ExtTime(buf, len);
					break;
				case 0x000A: // NTFS timestamp
					while (buf.rIndex < end) {
						int k = buf.readUShortLE();
						DynByteBuf v = buf.slice(buf.readUShortLE());
						if (k == 1) {
							if (v.readableBytes() >= 8) pModTime = checkAndSet0(v.readLongLE());
							if (v.readableBytes() >= 8) pAccTime = checkAndSet0(v.readLongLE());
							if (v.readableBytes() >= 8) pCreTime = checkAndSet0(v.readLongLE());
							mzFlag |= MZ_PrTime;
						}
					}
					break;
				case 0x0001: // Zip64 extended information
					if (uSize == U32_MAX && len >= 8) {
						len -= 8;
						uSize = buf.readLongLE();
					}
					if (cSize == U32_MAX && len >= 8) {
						len -= 8;
						cSize = buf.readLongLE();
					}
					if (header == U32_MAX && len >= 8) {
						header = buf.readLongLE();
					}
					break;
				case 0x7075:
					readUnicodePath(buf, len, o);
					break;
				case 0x9901:
					readAES(buf, len);
					break;
			}
			if (buf.rIndex > end) throw new IllegalStateException("0x" + Integer.toHexString(id));
			buf.rIndex = end;
		}
		return header;
	}
	final void writeCENExtra(ByteList buf, int extLenOff) {
		// u2LE(0x01) u2LE(0x00)
		buf.putInt(0x01000000);
		int pos = buf.wIndex();

		int z64 = 0;
		if (uSize >= U32_MAX) {
			buf.putIntLE(extLenOff-6, (int) U32_MAX)
			   .putLongLE(uSize);
			z64++;
		}
		if (cSize >= U32_MAX) {
			buf.putIntLE(extLenOff-10, (int) U32_MAX)
			   .putLongLE(cSize);
			z64++;
		}
		if (startPos() >= U32_MAX) {
			buf.putIntLE(extLenOff+14, (int) U32_MAX)
			   .putLongLE(startPos());
			z64++;
		}

		if (z64 > 0) {
			buf.putShortLE(pos-2, z64<<3);
		} else {
			buf.wIndex(buf.wIndex()-4);
		}

		prTime:
		if ((mzFlag & MZ_PrTime) != 0) {
			int len = 0;
			if (pModTime != 0) len += 8;
			if (pAccTime != 0) len += 8;
			if (pCreTime != 0) len += 8;
			if (len == 0) break prTime;

			buf.putShortLE(0x000A).putShortLE(len+4).putShortLE(1).putShortLE(len);

			if (pModTime != 0) buf.putLongLE(java2WinTime(pModTime));
			if (pAccTime != 0) buf.putLongLE(java2WinTime(pAccTime));
			if (pCreTime != 0) buf.putLongLE(java2WinTime(pCreTime));
		}

		if ((flags & GP_UTF) == 0 && (mzFlag & MZ_UniPath) != 0) {
			writeUnicodePath(buf);
		}

		if ((mzFlag & MZ_AES) != 0) {
			writeAES(buf);
			if ((mzFlag & MZ_NoCrc) != 0) buf.putIntLE(extLenOff-14, 0);
		}
	}

	private void readUnicodePath(ByteList buf, int len, ZipFile o) {
		if(len >= 5) {
			int crc = CRC32s.once(nameBytes, 0, nameBytes.length);

			buf.skipBytes(1);
			int expectedCrc = buf.readIntLE();
			len -= 5;

			if (crc == expectedCrc) {
				String name1 = buf.readUTF(len);
				if ((o.flags & (FLAG_VERIFY|FLAG_BACKWARD_READ)) == FLAG_VERIFY && !name.equals(name1)) {
					throw new IllegalArgumentException("Extra7075: 字符集错误(以只读模式打开,或关闭验证)");
				}
				name = name1;
				mzFlag |= MZ_UniPath;
			} else {
				if ((o.flags & FLAG_VERIFY) != 0) {
					throw new IllegalArgumentException("Extra7075: 文件名CRC校验错误: hex(name)=["+TextUtil.bytes2hex(nameBytes) +"], crc="+Integer.toHexString(expectedCrc));
				}
			}
		}
	}
	private void writeUnicodePath(ByteList buf) {
		int pos = buf.wIndex();
		buf.wIndex(pos+2);

		int crc = CRC32s.once(nameBytes, 0, nameBytes.length);
		buf.put(0)
		   .putIntLE(crc)
		   .putUTFData(name)
		   .putShortLE(pos, buf.wIndex()-pos-2);
	}

	private void readAES(ByteList buf, int len) {
		if(len >= 7) {
			// 1 => AE-1, CRC presented, 2 => CRC set to 0
			int type = buf.readUShortLE();
			if (type == 2) mzFlag |= MZ_NoCrc;
			else if (type != 1) mzFlag |= MZ_ERROR;

			int vendor = buf.readUShortLE();
			if (vendor != 0x4541) mzFlag |= MZ_ERROR;

			int algorithm = buf.readUnsignedByte();
			mzFlag |= algorithm != 0x3 ? MZ_ERROR : MZ_AES;

			method = buf.readChar();
		}
	}
	private void writeAES(ByteList buf) {
		buf.putShortLE(0x9901).putShortLE(7)
		   .putShortLE((mzFlag & MZ_NoCrc) != 0 ? 0x2 : 0x1)
		   .putShortLE(0x4541) // vendor id, ASCII"AE"
		   .put(0x3) // encryption strength: AES-256
		   .putShortLE(method);
	}

	private void read5455ExtTime(ByteList buf, int len) {
		int flag = buf.readUnsignedByte();

		if (flag != 0) mzFlag |= MZ_PrTime;

		if (len >= 4 && (flag&1) != 0) {
			pModTime = buf.readUIntLE()*1000;
			len -= 4;
		}
		if (len >= 4 && (flag&2) != 0) {
			pAccTime = buf.readUIntLE()*1000;
			len -= 4;
		}
		if (len >= 4 && (flag&4) != 0) {
			pCreTime = buf.readUIntLE()*1000;
		}
	}

	final boolean merge(ZEntry cen) {
		if (method != cen.method ||
			cSize != cen.cSize ||
			uSize != cen.uSize ||
			flags != cen.flags ||
			!name.equals(cen.name)) {
			return false;
		}

		if (crc32 == 0) crc32 = cen.crc32;

		internalAttr = cen.internalAttr;
		externalAttr = cen.externalAttr;

		mzFlag |= cen.mzFlag;
		mzFlag &= ~MZ_BACKWARD;
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ZEntry file = (ZEntry) o;
		return name.equals(file.name);
	}
	@Override
	public int hashCode() {return name.hashCode();}

	@Override
	public String toString() {
		return "File{" + "method=" + (int) method +
			", time=" + dos2JavaTime(modTime) +
			", crc=0x" + Integer.toHexString(crc32) +
			", cSize=" + Long.toUnsignedString(cSize) +
			", uSize=" + Long.toUnsignedString(uSize) +
			", ia=0b" + Integer.toBinaryString(internalAttr) +
			", ea=" + externalAttr +
			", gp=0b" + Integer.toBinaryString(flags) +
			", name='" + name + '\'' +
			", offset=" + (nameBytes == null ? -1 : startPos()) + '}';
	}

	public static long dos2JavaTime(int dtime) {
		long day = DateParser.daySinceUnixZero(((dtime >> 25) & 0x7f) + 1980, ((dtime >> 21) & 0x0f), (dtime >> 16) & 0x1f);
		return 86400000L * day + 3600_000L * ((dtime >> 11) & 0x1f) + 60_000L * ((dtime >> 5) & 0x3f) + 1000L * ((dtime << 1) & 0x3e);
	}
	public static int java2DosTime(long time) {
		int[] arr = DateParser.parseGMT(time + TimeZone.getDefault().getOffset(time));
		int year = arr[DateParser.YEAR] - 1980;
		if (year < 0) {
			return (1 << 21) | (1 << 16)/*ZipEntry.DOSTIME_BEFORE_1980*/;
		}
		return (year << 25) | (arr[DateParser.MONTH] << 21) | (arr[DateParser.DAY] << 16) | (arr[DateParser.HOUR] << 11) | (arr[DateParser.MINUTE] << 5) | (arr[DateParser.SECOND] >> 1);
	}

	@Override
	protected ZEntry clone() {
		try {
			return (ZEntry) super.clone();
		} catch (CloneNotSupportedException e) {
			return Helpers.nonnull();
		}
	}

	private static final long WINDOWS_TIME_NOT_AVAILABLE = Long.MIN_VALUE;
	public static long checkAndSet0(long time) { return time == WINDOWS_TIME_NOT_AVAILABLE ? 0 : time; }
	private static final long WINDOWS_EPOCH_IN_MICROSECONDS = -11644473600000000L;
	public static long winTime2JavaTime(long wtime) { return (wtime / 10 + WINDOWS_EPOCH_IN_MICROSECONDS) / 1000; }
	public static long java2WinTime(long time) { return (time*1000 - WINDOWS_EPOCH_IN_MICROSECONDS) * 10; }
	public static FileTime winTime2FileTime(long wtime) { return FileTime.from(wtime / 10 + WINDOWS_EPOCH_IN_MICROSECONDS, TimeUnit.MICROSECONDS); }
	public static long fileTime2WinTime(FileTime time) { return (time.to(TimeUnit.MICROSECONDS) - WINDOWS_EPOCH_IN_MICROSECONDS) * 10; }
}