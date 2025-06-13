package roj.archive.zip;

import org.intellij.lang.annotations.MagicConstant;

/**
 * @author Roj234
 * @since 2023/3/14 0:42
 */
public final class EntryMod {
	String name;
	ZEntry entry;

	public static final byte KEEP_TIME = 1, UFS = 2, LARGE = 4, COMPRESS = 8;
	@MagicConstant(flags = {KEEP_TIME, UFS, LARGE, COMPRESS})
	public byte flag;

	public Object data;

	public byte[] pass;
	@MagicConstant(intValues = {ZipArchive.CRYPT_NONE, ZipArchive.CRYPT_ZIP2, ZipArchive.CRYPT_AES, ZipArchive.CRYPT_AES2})
	public byte cryptType;

	public String getName() { return name; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		EntryMod file = (EntryMod) o;
		return name.equals(file.name);
	}

	@Override
	public int hashCode() { return name.hashCode(); }

	public boolean large() { return (flag & LARGE) != 0; }
}