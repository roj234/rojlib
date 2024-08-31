package roj.media.audio;

/**
 * @author Roj234
 * @since 2024/2/20 0020 0:49
 */
public class APETag {
	private int apeVer;

	private static int apeInt32(byte[] b, int off) {
		if (b.length - off < 4) return 0;
		return ((b[off + 3] & 0xff) << 24) | ((b[off + 2] & 0xff) << 16) | ((b[off + 1] & 0xff) << 8) | (b[off] & 0xff);
	}

	/**
	 * 获取APE标签信息长度。源数据b的可用长度不少于32字节。
	 *
	 * @param b 源数据。
	 * @param off 源数据偏移量。
	 *
	 * @return APE标签信息长度。以下两种情况返回0：
	 * <ul>
	 * <li>如果源数据b偏移量off开始的数据内未检测到APE标签信息；</li>
	 * <li>如果源数据b的可用长度少于32字节。</li>
	 * </ul>
	 */
	public int checkFooter(byte[] b, int off) {
		if (b.length - off < 32) return 0;
		if (b[off] == 'A' && b[off + 1] == 'P' && b[off + 2] == 'E' && b[off + 3] == 'T' && b[off + 4] == 'A' && b[off + 5] == 'G' && b[off + 6] == 'E' && b[off + 7] == 'X') {
			apeVer = apeInt32(b, off + 8);
			return apeInt32(b, off + 12) + 32;
		}
		return 0;
	}

	/**
	 * 获取APE标签信息版本。
	 *
	 * @return 以整数形式返回APE标签信息版本。
	 */
	public int getApeVer() {
		return apeVer;
	}
}