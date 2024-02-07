package roj.archive.zip;

import roj.io.IOUtil;
import roj.util.ArrayCache;

import java.util.Arrays;

/**
 * @author Roj234
 * @since 2023/3/14 0014 0:42
 */
public final class END {
	/*int diskId;
	int cDirBegin;*/
	int cDirOnDisk;
	int cDirTotal;

	long cDirLen, cDirOffset;

	byte[] comment = ArrayCache.BYTES;

	@Override
	public String toString() {
		return "ZEnd{" + "cDirLen=" + cDirLen + ", cDirOff=" + cDirOffset + ", comment='" + new String(comment) + '\'' + '}';
	}

	public void setComment(String str) {
		comment = str == null || str.isEmpty() ? ArrayCache.BYTES : IOUtil.SharedCoder.get().encode(str);
		if (comment.length > 65535) {
			comment = Arrays.copyOf(comment, 65535);
			throw new IllegalArgumentException("Comment too long");
		}
	}

	public void setComment(byte[] str) {
		comment = str == null ? ArrayCache.BYTES : str;
	}
}
