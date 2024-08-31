package roj.media.audio.mp3;

/**
 * 解码Layer1/2全部数据或者Layer3的帧边信息<br>
 * Layer3的其他信息使用{@link MainDataDecoder}
 */
class BitStream {
	int bitPos, bytePos;
	byte[] reservoir;
	private int endPos;        // reservoir已填入的字节数
	private final int maxOff;

	/**
	 * 解码帧边信息<br>
	 * (will: )len为9 17 32
	 */
	public BitStream(int magic) {
		this.maxOff = 0;
	}

	/**
	 * 解码主数据<br>
	 * len >= 512+1732<br>
	 * 为防止哈夫曼解码时位流有错误导致缓冲区溢出，尾部空出512字节(part2_3_length，2^12)
	 */
	public BitStream() {
		maxOff = 4096;
		reservoir = new byte[4096 + 512];
	}

	/**
	 * 复制数据到缓冲区
	 *
	 * @return 实际复制的字节数
	 */
	public int append(byte[] b, int off, int len) {
		if (len + endPos > maxOff) {
			// 将缓冲区bytePos及之后的(未处理过的)数据移动到缓冲区首
			System.arraycopy(reservoir, bytePos, reservoir, 0, endPos - bytePos);
			endPos -= bytePos;
			bitPos = bytePos = 0;
		}
		if (len + endPos > maxOff) len = maxOff - endPos;
		System.arraycopy(b, off, reservoir, endPos, len);
		endPos += len;
		return len;
	}

	/**
	 * 指定缓冲区为b <br>
	 * 不复制数据
	 */
	public void feed(byte[] b, int off) {
		reservoir = b;
		bytePos = off;
		bitPos = 0;
	}

	/**
	 * 从缓冲区读取1位
	 */
	public int get1() {
		int bit = ((reservoir[bytePos] << bitPos++) >> 7) & 0x1;
		bytePos += bitPos >> 3;
		bitPos &= 0x7;
		return bit;
	}

	/**
	 * 从缓冲区读取2-9位
	 */
	public int get2(int n) {
		byte[] res = this.reservoir;                                            // 高16位置0
		int n2 = (((((res[bytePos] & 0xFF) << 8) | (res[bytePos + 1] & 0xFF)) << bitPos) & 0xFFFF) >> 16 - n;
		bitPos += n;
		bytePos += bitPos >> 3;
		bitPos &= 0x7;
		return n2;
	}

	/**
	 * 从缓冲区读取2-17位
	 */
	public int get3(int n) {
		byte[] res = this.reservoir;
		int n3 = (((((res[bytePos] & 0xFF) << 16) | ((res[bytePos + 1] & 0xFF) << 8) | (res[bytePos + 2] & 0xFF)) << bitPos) & 0xFFFFFF) >> 24 - n;
		bitPos += n;
		bytePos += bitPos >> 3;
		bitPos &= 0x7;
		return n3;
	}

	/**
	 * 获取缓冲区字节指针
	 */
	public int getBytePos() {
		return bytePos;
	}

	/**
	 * 获取缓冲区已经填入的字节数
	 */
	public int getSize() {
		return endPos;
	}

	/**
	 * 缓冲区丢弃n字节，复位bit指针
	 */
	public void skipBytes(int n) {
		bytePos += n;
		bitPos = 0;
	}

	/**
	 * 缓冲区丢弃或回退(n < 0)指定比特
	 */
	public void skipBits(int n) {
		bitPos += n;
		bytePos += bitPos >> 3;
		bitPos &= 0x7;
	}
}