package roj.plugins.unpacker;

/**
 * @author Roj234
 * @since 2023/12/1 0:49
 */
final class PosInfo {
	long offset;
	int length;

	PosInfo(long offset, int length) {
		this.offset = offset;
		this.length = length;
	}
}