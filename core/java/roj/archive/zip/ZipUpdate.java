package roj.archive.zip;

import org.intellij.lang.annotations.MagicConstant;

import static roj.archive.zip.ZipEntry.*;

/**
 * @author Roj234
 * @since 2023/3/14 0:42
 */
public final class ZipUpdate {
	String name;
	ZipEntry entry;

	public ZipUpdate(String name) {
		this.name = name;
	}

	// bit 1-2: method
	// bit 3-4: cryptType
	// bit 5: zip64
	private static final int
		FL_METHOD = 0x03,
		FL_CRYPT = 0x0C,
		FL_ZIP64 = 0x10;

	byte flags;

	public Object data;
	public long modificationTime;
	public byte[] password;

	public String getName() { return name; }

	private static final byte[] METHOD_MAP = {STORED, DEFLATED, LZMA, -1};
	public void setMethod(@MagicConstant(intValues = {STORED, DEFLATED, LZMA}) int method) {
		var b = switch (method) {
			case STORED -> 0;
			case DEFLATED -> 1;
			case LZMA -> 2;
			default -> throw new IllegalStateException("Unsupported method: " + method);
		};

		flags = (byte) ((flags & ~FL_METHOD) | b);
	}
	public int getMethod() { return METHOD_MAP[flags & FL_METHOD]; }

	public void setEncryptMethod(@MagicConstant(intValues = {ENC_NONE, ENC_ZIPCRYPTO, ENC_AES, ENC_AES_NOCRC}) int cryptType) {
		flags = (byte) ((flags & ~FL_CRYPT) | (cryptType << 2));
	}
	public int getEncryptMethod() { return (flags & FL_CRYPT) >>> 2; }

	public void setZip64(boolean zip64) {
		if (zip64) flags |= FL_ZIP64;
		else flags &= ~FL_ZIP64;
	}
	public boolean isZip64() { return (flags & FL_ZIP64) != 0; }

	public void setModificationTime(long modificationTime) {
		this.modificationTime = modificationTime;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ZipUpdate file = (ZipUpdate) o;
		return name.equals(file.name);
	}

	@Override
	public int hashCode() { return name.hashCode(); }
}