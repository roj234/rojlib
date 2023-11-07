package roj.archive.zip;

import roj.archive.ArchiveEntry;
import roj.collect.MyHashMap;
import roj.collect.RSegmentTree;
import roj.crypt.CRCAny;
import roj.text.ACalendar;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import static roj.archive.zip.ZipArchive.*;

/**
 * @author Roj234
 * @since 2023/3/14 0014 0:43
 */
public class ZEntry implements RSegmentTree.Range, ArchiveEntry {
	//00: no compression
	//08: deflated
	//14: LZMA
	char method;

	int modTime;
	long pModTime, pAccTime, pCreTime;

	int CRC32;
	long cSize, uSize;

	//Bit 0: apparent ASCII/text file
	public char internalAttr;
	//vendor specific
	public int externalAttr;

	char flags;

	String name;
	byte[] nameBytes;

	byte mzfFlag;
	static final int
		MZ_HASLOC= 1,
		MZ_HASCEN= 2,
		MZ_AESENC= 4,
		MZ_NOCRC = 8,
		MZ_LTIME = 16,
		MZ_UNIPATH = 32;

	/** 数据起始 */
	long offset;
	char extraLenOfLOC;
	byte EXTLenOfLOC;

	public ZEntry(String name) {
		this.name = name;
		this.method = 8;
	}

	ZEntry(Boolean LOC) {
		if (LOC != Boolean.TRUE) mzfFlag |= MZ_HASCEN;
		if (LOC != Boolean.FALSE) mzfFlag |= MZ_HASLOC;
	}

	public String getName() {
		return name;
	}

	@Override
	public final long startPos() { return offset - 30 - nameBytes.length - extraLenOfLOC; }
	@Override
	public final long endPos() { return offset + cSize + EXTLenOfLOC; }
	final void setEndPos(long pos) {
		EXTLenOfLOC = (byte) (pos - cSize - offset);
	}

	final int getMethodFW() {
		return (mzfFlag & MZ_AESENC) != 0 ? 99 : method;
	}
	final int getVersionFW() {
		if ((mzfFlag & MZ_AESENC) != 0) return ZIP_AES;
		if (uSize > U32_MAX || cSize > U32_MAX || offset > U32_MAX) return ZIP_64;
		return method == ZipEntry.DEFLATED ? ZIP_DEFLATED : ZIP_STORED;
	}
	final int getCRC32FW() {
		return (mzfFlag & MZ_NOCRC) != 0 ? 0 : CRC32;
	}

	public final long getModificationTime() { return pModTime == 0 ? dos2JavaTime(modTime) : pModTime; }
	public final void setModificationTime(long t) {
		if (t == -1) {
			modTime = 0;
			pModTime = 0;
		} else {
			modTime = java2DosTime(t);
			pModTime = t;
		}
	}

	public final long getSize() { return uSize; }
	public final long getCompressedSize() { return cSize; }

	public final int getMethod() { return method; }
	public final void setMethod(int m) { this.method = (char) m; }

	public final int getCRC32() { return CRC32; }

	public final boolean isEncrypted() { return (flags & GP_ENCRYPTED) != 0; }
	public final int getEncryptType() {
		if ((flags & GP_ENCRYPTED) == 0) return CRYPT_NONE;
		if ((flags & GP_STRONG_ENC) != 0) return /*CRYPT_UNKNOWN*/-1;
		if ((mzfFlag&MZ_AESENC) != 0) return (mzfFlag&MZ_NOCRC) != 0 ? CRYPT_AES2 : CRYPT_AES;
		return CRYPT_ZIP2;
	}

	public final int getGeneralPurposeFlag() { return flags; }
	public final void setGeneralPurposeFlag(int flag) { flags = (char) flag; }

	@SuppressWarnings("fallthrough")
	final void prepareWrite(int crypt) {
		CRC32 = 0;
		flags &= GP_UTF;
		mzfFlag = MZ_HASLOC|MZ_HASCEN;

		if (crypt != CRYPT_NONE) {
			flags |= GP_ENCRYPTED;
			switch (crypt) {
				case CRYPT_ZIP2: break;
				case CRYPT_AES2: mzfFlag |= MZ_NOCRC;
				case CRYPT_AES: mzfFlag |= MZ_AESENC; break;
				default: throw new IllegalArgumentException("Unknown crypt method: " + crypt);
			}
		}
	}

	final void readLOCExtra(ZipArchive o, ByteList buf) {
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
				default:
					customReadExtraLOC(id, buf, len);
					break;
			}
			buf.rIndex = end;
		}
	}
	protected void writeLOCExtra(ByteList buf, int extOff, int extLenOff) {
		if (cSize >= U32_MAX || uSize >= U32_MAX) {
			if (uSize >= U32_MAX) buf.putIntLE(extLenOff-6, (int) U32_MAX);
			if (cSize >= U32_MAX) buf.putIntLE(extLenOff-10, (int) U32_MAX);

			buf.putShortLE(0x0001).putShortLE(16).putLongLE(uSize).putLongLE(cSize);
		}

		if ((mzfFlag & MZ_AESENC) != 0) {
			writeAES(buf);
			if ((mzfFlag & MZ_NOCRC) != 0) buf.putIntLE(extLenOff-14, 0);
		}

		if ((flags & GP_UTF) == 0 && (mzfFlag & MZ_UNIPATH) != 0) {
			writeUnicodePath(buf);
		}

		buf.putShortLE(extLenOff, extraLenOfLOC = (char) (buf.wIndex() - extOff));
	}
	protected void customReadExtraLOC(int id, ByteList buf, int len) {}

	final long readCENExtra(ZipArchive o, ByteList buf, long header) {
		while (buf.readableBytes() > 4) {
			int id = buf.readUShortLE();
			int len = buf.readUShortLE();
			int end = buf.rIndex + len;
			switch (id) {
				case 0x5455: // Extended timestamp
					read5455ExtTime(buf, len);
					break;
				case 0x000A: // NTFS timestamp
					buf.rIndex += 4;
					while (buf.rIndex < end) {
						int k = buf.readUShortLE();
						DynByteBuf v = buf.slice(buf.readUShortLE());
						if (k == 1) {
							if (v.readableBytes() >= 8) pModTime = winTime2JavaTime(v.readLongLE());
							if (v.readableBytes() >= 8) pAccTime = winTime2JavaTime(v.readLongLE());
							if (v.readableBytes() >= 8) pCreTime = winTime2JavaTime(v.readLongLE());
							mzfFlag |= MZ_LTIME;
						}
					}
					break;
				case 0x0001: // Zip64 extended information
					if (uSize == (int) U32_MAX && len >= 8) {
						len -= 8;
						uSize = buf.readLongLE();
					}
					if (cSize == (int) U32_MAX && len >= 8) {
						len -= 8;
						cSize = buf.readLongLE();
					}
					if (header == (int) U32_MAX && len >= 8) {
						header = buf.readLongLE();
					}
					break;
				case 0x7075:
					readUnicodePath(buf, len, o);
					break;
				case 0x9901:
					readAES(buf, len);
					break;
				default:
					customReadExtraCEN(id, buf, len);
					break;
			}
			if (buf.rIndex > end) throw new IllegalStateException("0x" + Integer.toHexString(id));
			buf.rIndex = end;
		}
		return header;
	}
	protected void writeCENExtra(ByteList buf, int extLenOff) {
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

		if ((mzfFlag & MZ_LTIME) != 0) {
			buf.putShortLE(0x000A).putShortLE(28)
			   .putShortLE(1).putShortLE(24)
			   .putLongLE(java2WinTime(pModTime))
			   .putLongLE(java2WinTime(pAccTime))
			   .putLongLE(java2WinTime(pCreTime));
		}

		if ((flags & GP_UTF) == 0 && (mzfFlag & MZ_UNIPATH) != 0) {
			writeUnicodePath(buf);
		}

		if ((mzfFlag & MZ_AESENC) != 0) {
			writeAES(buf);
			if ((mzfFlag & MZ_NOCRC) != 0) buf.putIntLE(extLenOff-14, 0);
		}
	}
	protected void customReadExtraCEN(int id, ByteList buf, int len) {}

	private void readUnicodePath(ByteList buf, int len, ZipArchive o) {
		if(len >= 5) {
			int crc = CRCAny.CRC_32.INIT_VALUE;
			crc = CRCAny.CRC_32.update(crc, nameBytes, 0, nameBytes.length);
			crc = CRCAny.CRC_32.retVal(crc);

			buf.skipBytes(1);
			int expectedCrc = buf.readIntLE();
			len -= 5;

			if (crc == expectedCrc) {
				String name1 = buf.readUTF(len);
				if ((o.flags & (FLAG_VERIFY| FLAG_BACKWARD_READ)) == FLAG_VERIFY && !name.equals(name1)) {
					throw new IllegalArgumentException("Extra7075: 字符集错误(以只读模式打开,或关闭验证)");
				}
				name = name1;
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

		int crc = CRCAny.CRC_32.INIT_VALUE;
		crc = CRCAny.CRC_32.update(crc, nameBytes, 0, nameBytes.length);

		buf.put(0)
		   .putIntLE(CRCAny.CRC_32.retVal(crc))
		   .putUTFData(name)
		   .putShortLE(pos, buf.wIndex()-pos-2);
	}

	private void readAES(ByteList buf, int len) {
		if(len >= 7) {
			// 1 => AE-1, CRC presented, 2 => CRC set to 0
			int type = buf.readUShortLE();
			if (type == 0 || type > 2) {
				System.err.println("Unknown encryption type " + type + ", excepting AE-1 or AE-2");
			} else if (type == 2) {
				mzfFlag |= MZ_NOCRC;
			}
			int vendor = buf.readUShortLE();
			if (vendor != 0x4541) {
				System.err.println("Unknown vendor " + Integer.toHexString(vendor));
			}
			int algorithm = buf.readUnsignedByte();
			if (algorithm != 0x3) { // AES-256
				System.err.println("Unknown encryption algorithm");
			} else {
				mzfFlag |= MZ_AESENC;
			}

			method = buf.readChar();
		}
	}
	private void writeAES(ByteList buf) {
		buf.putShortLE(0x9901).putShortLE(7)
		   .putShortLE((mzfFlag & MZ_NOCRC) != 0 ? 0x2 : 0x1)
		   .putShortLE(0x4541) // vendor id, ASCII"AE"
		   .put(0x3) // encryption strength: AES-256
		   .putShortLE(method);
	}

	private void read5455ExtTime(ByteList buf, int len) {
		int flag = buf.readUnsignedByte();

		if (len >= 4 && (flag&1) != 0) {
			pModTime = buf.readUIntLE()*1000;
			mzfFlag |= MZ_LTIME;
			len -= 4;
		}
		if (len >= 4 && (flag&2) != 0) {
			pAccTime = buf.readUIntLE()*1000;
			mzfFlag |= MZ_LTIME;
			len -= 4;
		}
		if (len >= 4 && (flag&4) != 0) {
			pCreTime = buf.readUIntLE()*1000;
			mzfFlag |= MZ_LTIME;
		}
	}

	final boolean merge(MyHashMap<String, ZEntry> entries, ZEntry file) throws ZipException {
		if ((mzfFlag & (MZ_HASCEN|MZ_HASLOC)) == (MZ_HASCEN|MZ_HASLOC)) {
			System.err.println("重复的文件: " + name + " 注意这些文件没法合并LOC和CEN");

			int alt = 1;
			while (true) {
				String myName = file.name+"_alt"+alt++;
				if (entries.putIfAbsent(myName, file) == null) {
					file.name = myName;
					return false;
				}
			}
		}

		if (method != file.method) {
			System.err.println("cm not same");
		}

		if (modTime != file.modTime || flags != file.flags) {
			// small violation
			System.err.println("sv");
		}

		if (offset == -1) {
			if (file.offset >= 0) offset = file.offset;
			else throw new ZipException("Both entry(" + this + ", " + file + ") have not 'Offset' set");
		} else if (file.offset != -1 && offset != file.offset) {
			error(file);
		}

		if (CRC32 == 0) {
			if (file.CRC32 != 0) CRC32 = file.CRC32;
		}

		if (cSize != file.cSize || uSize != file.uSize) {
			error(file);
		}

		if ((file.mzfFlag & MZ_HASCEN) != 0) {
			internalAttr = file.internalAttr;
			externalAttr = file.externalAttr;
		}
		if ((file.mzfFlag & MZ_HASLOC) != 0) {
			extraLenOfLOC = file.extraLenOfLOC;
		}

		mzfFlag |= file.mzfFlag;
		return true;
	}
	private void error(ZEntry file) throws ZipException {
		throw new ZipException("文件大小或偏移在CEN和LOC间不匹配(" + this + ", " + file + ")");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		ZEntry file = (ZEntry) o;
		return name.equals(file.name);
	}

	@Override
	public int hashCode() {
		return name.hashCode();
	}

	@Override
	public String toString() {
		return "File{" + "method=" + (int) method +
			", time=" + dos2JavaTime(modTime) +
			", crc=0x" + Integer.toHexString(CRC32) +
			", cSize=" + Long.toUnsignedString(cSize) +
			", uSize=" + Long.toUnsignedString(uSize) +
			", ia=0b" + Integer.toBinaryString(internalAttr) +
			", ea=" + externalAttr +
			", gp=0b" + Integer.toBinaryString(flags) +
			", name='" + name + '\'' +
			", offset=" + startPos() + '}';
	}

	public static long dos2JavaTime(int dtime) {
		long day = ACalendar.daySinceAD(((dtime >> 25) & 0x7f) + 1980, ((dtime >> 21) & 0x0f), (dtime >> 16) & 0x1f, null) - ACalendar.GREGORIAN_OFFSET_DAY;
		return 86400000L * day + 3600_000L * ((dtime >> 11) & 0x1f) + 60_000L * ((dtime >> 5) & 0x3f) + 1000L * ((dtime << 1) & 0x3e);
	}
	public static int java2DosTime(long time) {
		int[] arr = ACalendar.parse1(time + TimeZone.getDefault().getOffset(time));
		int year = arr[ACalendar.YEAR] - 1980;
		if (year < 0) {
			return (1 << 21) | (1 << 16)/*ZipEntry.DOSTIME_BEFORE_1980*/;
		}
		return (year << 25) | (arr[ACalendar.MONTH] << 21) | (arr[ACalendar.DAY] << 16) | (arr[ACalendar.HOUR] << 11) | (arr[ACalendar.MINUTE] << 5) | (arr[ACalendar.SECOND] >> 1);
	}

	private static final long WINDOWS_EPOCH_IN_MICROSECONDS = -11644473600000000L;
	public static long winTime2JavaTime(long wtime) {
		return (wtime / 10 + WINDOWS_EPOCH_IN_MICROSECONDS) / 1000;
	}
	public static long java2WinTime(long time) {
		return (time*1000 - WINDOWS_EPOCH_IN_MICROSECONDS) * 10;
	}
}