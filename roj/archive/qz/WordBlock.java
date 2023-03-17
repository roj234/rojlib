package roj.archive.qz;

/**
 * 字块
 * @author Roj233
 * @since 2022/3/14 7:09
 */
final class WordBlock extends WordBlock4W {
	// temporary used
	QZCoder[] coders;
    // seems only length useful
    int[] inputTargets;

    long offset;
	QZEntry firstEntry;

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("字块{\n")
		  .append("  位于").append(offset).append('+').append(size).append('\n');
		myToString(sb);
		sb.append("\n  包含").append(fileCount).append("个文件\n")
		  .append("  CRC=").append((hasCrc&1)==0?"~":Integer.toHexString(crc)).append('/').append((hasCrc&2)==0?"~":Integer.toHexString(cCrc));
		return sb.append("\n}").toString();
	}
}

