package roj.sound.mp3;

import roj.math.EMA;

import javax.sound.sampled.AudioFormat;

/**
 * MPEG-1/2/2.5 Audio Layer I/II/III 帧同步和帧头信息解码
 */
public final class Header {
	public static final int MPEG1 = 3, MPEG2 = 2, MPEG2_5 = 0,

	MAX_FRAME_SIZE = 1732;

	/*
	 * bitrate[lsf][layer-1][bitrate_index]
	 */
	private static final int[][][] BITRATE = {
		{
			//MPEG-1
			//Layer I
			{0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448},
			//Layer II
			{0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384},
			//Layer III
			{0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320}
		}, {
			//MPEG-2/2.5
			//Layer I
			{0, 32, 48, 56, 64, 80, 96, 112, 128, 144, 160, 176, 192, 224, 256},
			//Layer II
			{0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160},
			//Layer III
			{0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160}
		}};

	/*
	 * samplingRate[verID][sampling_frequency]
	 */
	private static final int[][] SAMPLING_RATE = {
		{11025, 12000, 8000, 0},     //MPEG-2.5
		{},                          //reserved
		{22050, 24000, 16000, 0},    //MPEG-2 (ISO/IEC 13818-3)
		{44100, 48000, 32000, 0}     //MPEG-1 (ISO/IEC 11172-3)
	};

	/**
	 * verID: 2-bit
	 * '00'  MPEG-2.5 (unofficial extension of MPEG 2);
	 * '01'  reserved;
	 * '10'  MPEG-2 (ISO/IEC 13818-3);
	 * '11'  MPEG-1 (ISO/IEC 11172-3).
	 */
	private byte ver;

	/**
	 * layer: 2-bit
	 * '11'	 Layer I
	 * '10'	 Layer II
	 * '01'	 Layer III
	 * '00'	 reserved
	 * <p>
	 * 已换算layer=4-layer: 1--Layer I; 2--Layer II; 3--Layer III; 4--reserved
	 */
	private byte layer;

	/**
	 * protection_bit: 1-bit
	 * '1'  no CRC;
	 * '0'  protected by 16 bit CRC following header.
	 */
	private byte protectionBit;

	/**
	 * 码率的索引[1-14]
	 * bitrate_index: 4-bit
	 */
	byte bitrate_index;

	/**
	 * PCM采样率的索引
	 * sampling_frequency: 2-bit
	 * '00'	 44.1kHz
	 * '01'	 48kHz
	 * '10'	 32kHz
	 * '11'  reserved
	 */
	byte sampling_frequency;

	/**
	 * mode: 2-bit
	 * '00'  Stereo;
	 * '01'  Joint Stereo (Stereo);
	 * '10'  Dual channel (Binary mono channels);
	 * '11'  Single channel (Mono).
	 */
	private byte mode;

	/**
	 * mode_extension: 2-bit
	 * intensity_stereo	MS_stereo
	 * '00'	 off				off
	 * '01'	 on					off
	 * '10'	 off				on
	 * '11'	 on					on
	 */
	private byte mode_extension;

	/**
	 * Size of frame, main data and side information
	 */
	int s_frame, s_main, s_sideInfo;

	private int lsf;

	private int syncMask;
	private boolean sync;    //true:帧头的特征未改变

	public void reset() {
		syncMask = 0xffe00000;
		sync = false;

		frames = 0;
		frameDuration = 0;
		frameSize2.clear();
		s_sideInfo = 0;
	}

	private void parseHeader(int h) {
		ver = (byte) ((h >> 19) & 3);
		layer = (byte) (4 - (h >> 17) & 3);
		protectionBit = (byte) ((h >> 16) & 0x1);
		int bi = this.bitrate_index = (byte) ((h >> 12) & 0xF);
		sampling_frequency = (byte) ((h >> 10) & 3);

		mode = (byte) ((h >> 6) & 3);
		mode_extension = (byte) ((h >> 4) & 3);

		int s_frame = this.s_frame;
		final int pad = (h >> 9) & 0x1;
		bi = BITRATE[lsf = (ver == MPEG1) ? 0 : 1][layer - 1][bi];
		final int s_rate = SAMPLING_RATE[ver][sampling_frequency];
		switch (layer) {
			case 1: s_frame = (bi * 12000 / s_rate) + (pad << 2); break;
			case 2: s_frame = bi * 144000 / s_rate + pad; break;
			case 3: s_frame = (bi * 144000 / (s_rate << lsf)) + pad;

				// 帧边信息长度
				s_sideInfo = ver == MPEG1 ? (mode == 3) ? 17 : 32 : (mode == 3) ? 9 : 17;
				break;
		}

		// 主数据长度
		s_main = (this.s_frame = s_frame) - 4 - s_sideInfo;
		if (protectionBit == 0) s_main -= 2;    //CRC

		frameSize2.add(s_main);
	}

	private static int b2i(byte[] b, int i) {
		return ((b[i++] & 0xFF) << 24) | ((b[i++] & 0xFF) << 16) | ((b[i++] & 0xFF) << 8) | (b[i] & 0xFF);
	}

	private static boolean findSyncFrame(int h, int mask) {
		return (h & mask) == mask && ((h >> 19) & 3) != 1     // version ID:  '01' - reserved
			&& ((h >> 17) & 3) != 0     // Layer index: '00' - reserved
			&& ((h >> 12) & 15) != 15   // Bitrate Index: '1111' - reserved
			&& ((h >> 12) & 15) != 0    // Bitrate Index: '0000' - free
			&& ((h >> 10) & 3) != 3;    // Sampling Rate Index: '11' - reserved
	}

	private int idx; // 暂存findSyncFrame方法中缓冲区b的偏移量
	final int offset() { return idx; }

	/**
	 * 帧同步及帧头信息解码。调用前应确保源数据缓冲区 b 长度 b.length 不小于最大帧长 1732。
	 * <p>本方法执行的操作：
	 * <ol><li>查找源数据缓冲区 b 内帧同步字（syncword）。</li>
	 * <li>如果查找到帧同步字段：</li><ol type="I"><li>解析帧头4字节。</li><li>如果当前是第一帧，解码VBR信息。</li></ol>
	 * <li>返回。</li>
	 * <ul><li>若返回<b>true</b>表示查找到帧同步字段， 接下来调用 {@link #ver()}、 {@link #getFrameSize()}
	 * 等方法能够返回正确的值。</li>
	 * <li>若未查找到帧同步字段，返回<b>false</b>。</li></ul></ol>
	 *
	 * @param b 源数据缓冲区。
	 * @param off 缓冲区 b 中数据的初始偏移量。
	 * @param endPos 缓冲区 b 中允许访问的最大偏移量。最大偏移量可能比缓冲区 b 的上界小。
	 *
	 * @return 返回<b>true</b>表示查找到帧同步字段。
	 */
	public boolean findSyncFrame(byte[] b, int off, int endPos) {
		int hdr, mask, orig_off = off;

		//int skipBytes = 0; // debug

		idx = off;

		if (endPos - off <= 4) return false;

		hdr = b2i(b, off);
		idx = orig_off += 4;

		while (true) {
			// 1.查找帧同步字
			while (!findSyncFrame(hdr, this.syncMask)) {
				hdr = (hdr << 8) | (b[orig_off++] & 0xff);
				if (orig_off == endPos) {
					idx = orig_off - 4;
					return false;
				}
			}

			if (orig_off > 4 + off) {
				sync = false;
				//skipBytes += idx - off - 4;
			}

			// 2. 解析帧头
			parseHeader(hdr);
			if (orig_off + s_frame > endPos + 4) {
				idx = orig_off - 4;
				return false;
			}

			// 若verID等帧的特征未改变(sync==true),不用与下一帧的同步头比较
			if (sync) break;

			// 3.与下一帧的同步头比较,确定是否找到有效的同步字.
			if (orig_off + s_frame > endPos) {
				idx = orig_off - 4;
				return false;
			}

			//     syncword    version      Layer    sampling_freq
			//    0xffe00000   0x180000    0x60000      0xc00;
			mask = 0xffe00000 | (hdr & 1969152);

			// mode, mode_extension 不是每帧都相同.
			if (findSyncFrame(b2i(b, orig_off + s_frame - 4), mask)) {
				if (this.syncMask == 0xffe00000) { // 是第一帧
					this.syncMask = mask;
					frameDuration = 1152f / (getSamplingRate() << lsf);
				}
				sync = true;
				break; // 找到有效的帧同步字段，结束查找
			}

			// 移动到下一字节，继续重复1-3
			hdr = (hdr << 8) | (b[orig_off++] & 0xff);
		}

		if (protectionBit == 0) orig_off += 2; // CRC word
		idx = orig_off;

		frames++;

		return true;
	}

	/**
	 * 声道是否为中/侧立体声（Mid/Side stereo）模式
	 */
	public boolean isMidStereo() { return mode == 1 && (mode_extension & 2) != 0; }
	/**
	 * 声道是否为强度立体声（Intensity Stereo）模式
	 */
	public boolean isIntensityStereo() { return mode == 1 && (mode_extension & 1) != 0; }

	/**
	 * 获取当前帧的码率（Kbps）
	 */
	public int getBitrate() { return BITRATE[lsf][layer - 1][bitrate_index]; }

	/**
	 * 声道数
	 */
	public int channels() { return (mode == 3) ? 1 : 2; }

	/**
	 * 获道模式
	 *
	 * @return 声道模式，其值表示的含义：
	 * <table border="1" cellpadding="8">
	 * <tr><th>返回值</th><th>声道模式</th></tr>
	 * <tr><td>0</td><td>立体声（stereo）</td></tr>
	 * <tr><td>1</td><td>联合立体声（joint stereo）</td></tr>
	 * <tr><td>2</td><td>双声道（dual channel）</td></tr>
	 * <tr><td>3</td><td>单声道（mono channel）</td></tr>
	 * </table>
	 *
	 * @see #getModeExtension()
	 */
	public int getMode() { return mode; }

	/**
	 * 声道扩展模式
	 *
	 * @return 声道扩展模式，该值表示当前声道使用的立体声编码方式：
	 * 强度立体声     中/侧立体声
	 * bit 0 (1)     bit1 (2)
	 *
	 * @see #getMode()
	 */
	public int getModeExtension() { return mode_extension; }

	/**
	 * 获取MPEG版本。
	 *
	 * @return MPEG版本：{@link #MPEG1}、 {@link #MPEG2} 或 {@link #MPEG2_5} 。
	 */
	public int ver() { return ver; }

	/**
	 * 获取MPEG Layer (1-3)
	 */
	public int getLayer() { return layer; }

	/**
	 * 获取PCM采样率 (Hz)
	 */
	public int getSamplingRate() { return SAMPLING_RATE[ver][sampling_frequency]; }

	/**
	 * 获取帧长度。帧的长度 = 4字节帧头 + CRC（如果有的话，2字节） + 边信息长度 + 主数据长度
	 * <p>无论可变位率（VBR）编码的文件还是固定位率（CBR）编码的文件，每帧的长度不一定同
	 */
	public int getFrameSize() { return s_frame; }

	public AudioFormat getAudioFormat() { return new AudioFormat(getSamplingRate(), 16, channels(), true, false); }

	/**
	 * 获取当前帧解码后得到的PCM样本长度。通常情况下同一文件每一帧解码后得到的PCM样本长度是相同的。
	 */
	public int getPcmSize() {
		int pcmsize = (ver == MPEG1) ? 4608 : 2304;
		if (mode == 3) // if channels == 1
			pcmsize >>= 1;
		return pcmsize;
	}

	@Override
	public String toString() {
		return "MPEG"+(ver == MPEG1 ? "1" : ver == MPEG2 ? "2" : "2.5")+" Layer"+layer+ ", "+getSamplingRate()+"kHz "+getBitrate()+"k, " +
			"mode=0b"+Integer.toBinaryString(mode)+", mode_ext=0b"+Integer.toBinaryString(mode_extension) + ", frameSizeAvg="+frameSize2.avg();
	}

	EMA frameSize2 = new EMA(0.9995);
	float frameDuration;//一帧时长(秒)
	int frames;         //当前帧序号

	public int getFrames() { return frames; }
	public double getFrameSizeAvg() { return frameSize2.avg(); }
	/**
	 * 获取文件一帧的长度(sec)
	 * 每一帧的播放时长一般相同
	 */
	public double getFrameDuration() { return frameDuration; }
}