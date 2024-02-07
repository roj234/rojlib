package roj.sound.mp3;

import roj.concurrent.TaskPool;

/**
 * 解码Layer Ⅲ。
 */
final class Layer3 extends Layer {
	final int granules, channels;

	private final MainDataDecoder mainIn;

	private int main_data_begin;
	private final int[] scfsi; // [channels],scale-factor selector information
	private final ChannelInfo[][] channelInfo; // [maxGr][channels]
	private final int[] sfbIndexLong, sfbIndexShort;
	private final boolean isMPEG1;
	private final SynthesisTask filterCh0;
	private SynthesisTask filterCh1;

	private final TaskPool DECODER;

	public Layer3(Header header, MP3Decoder audio, TaskPool synth) {
		super(header, audio, new BitStream(0));
		isMPEG1 = header.ver() == Header.MPEG1;
		granules = isMPEG1 ? 2 : 1;
		channels = header.channels();

		mainIn = new MainDataDecoder();
		scfsi = new int[channels];
		scalefacLong = new int[channels][23];
		scalefacShort = new int[channels][3 * 13];
		hv = new int[32 * 18 + 4];
		channelInfo = new ChannelInfo[granules][channels];
		for (int gr = 0; gr < granules; gr++)
			for (int ch = 0; ch < channels; ch++)
				channelInfo[gr][ch] = new ChannelInfo();

		DECODER = synth;

		filterCh0 = new SynthesisTask(this, 0); //ch=0
		xrch0 = filterCh0.getEmptyBuffer();
		preBlckCh0 = new float[32 * 18];

		if (channels == 2) {
			filterCh1 = new SynthesisTask(this, 1); //ch=1
			xrch1 = filterCh1.getEmptyBuffer();
			preBlckCh1 = new float[32 * 18];
		}

		//---------------------------------------------------------------------
		//待解码文件的不同特征用到不同的变量.初始化:
		//---------------------------------------------------------------------
		int sfreq = this.header.sampling_frequency;
		sfreq += isMPEG1 ? 0 : ((this.header.ver() == Header.MPEG2) ? 3 : 6);

		// ANNEX B,Table 3-B.8. Layer III scalefactor bands
		switch (sfreq) {
			case 0, 1, 2, 3, 4 -> sfreq <<= 1;
			case 5, 6, 7 -> sfreq = 10;
			case 8 -> sfreq = 12;
			default -> throw new IllegalStateException("Unexpected value: " + sfreq);
		}

		int[] wl = widthLong = new int[22];
		int[] sl = sfbIndexLong = sfbIndexTab[sfreq];
		int i;
		for (i = 0; i < 22; i++)
			wl[i] = sl[i + 1] - sl[i];

		wl = widthShort = new int[13];
		sl = sfbIndexShort = sfbIndexTab[sfreq + 1];
		for (i = 0; i < 13; i++)
			wl[i] = sl[i + 1] - sl[i];
	}

	// ANNEX B,Table 3-B.8. Layer III scalefactor bands
	private static final int[][] sfbIndexTab = new int[][] {
		// MPEG-1, sampling_frequency=0, 44.1kHz   0
		{0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 52, 62, 74, 90, 110, 134, 162, 196, 238, 288, 342, 418, 576}, {0, 4, 8, 12, 16, 22, 30, 40, 52, 66, 84, 106, 136, 192},

		// MPEG-1, sampling_frequency=1, 48kHz   1
		{0, 4, 8, 12, 16, 20, 24, 30, 36, 42, 50, 60, 72, 88, 106, 128, 156, 190, 230, 276, 330, 384, 576}, {0, 4, 8, 12, 16, 22, 28, 38, 50, 64, 80, 100, 126, 192},

		// MPEG-1, sampling_frequency=2, 32kHz   2
		{0, 4, 8, 12, 16, 20, 24, 30, 36, 44, 54, 66, 82, 102, 126, 156, 194, 240, 296, 364, 448, 550, 576}, {0, 4, 8, 12, 16, 22, 30, 42, 58, 78, 104, 138, 180, 192},

		// MPEG-2, sampling_frequency=0, 22.05kHz   3
		{0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576}, {0, 4, 8, 12, 18, 24, 32, 42, 56, 74, 100, 132, 174, 192},

		// MPEG-2, sampling_frequency=1, 24kHz   4
		{0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 114, 136, 162, 194, 232, 278, 330, 394, 464, 540, 576}, {0, 4, 8, 12, 18, 26, 36, 48, 62, 80, 104, 136, 180, 192},

		// MPEG-2.5, sampling_frequency=1, 12kHz   5
		// MPEG-2.5, sampling_frequency=0, 11.025kHz   6
		// MPEG-2, sampling_frequency=2, 16kHz   7
		{0, 6, 12, 18, 24, 30, 36, 44, 54, 66, 80, 96, 116, 140, 168, 200, 238, 284, 336, 396, 464, 522, 576}, {0, 4, 8, 12, 18, 26, 36, 48, 62, 80, 104, 134, 174, 192},

		// MPEG-2.5, sampling_frequency=2, 8kHz   8
		{0, 12, 24, 36, 48, 60, 72, 88, 108, 132, 160, 192, 232, 280, 336, 400, 476, 566, 568, 570, 572, 574, 576}, {0, 8, 16, 24, 36, 52, 72, 96, 124, 160, 162, 164, 166, 192}};

	//1.
	//>>>>SIDE INFORMATION (part1)=============================================
	//private int part2_3_bits;//----debug

	private int getSideInfo(byte[] b, int off) {
		//part2_3_bits = 0;
		final BitStream bs = this.data;
		bs.feed(b, off);

		ChannelInfo info;
		if (isMPEG1) {
			main_data_begin = bs.get2(9);
			if (channels == 1) {
				bs.skipBits(5);    //private_bits
				scfsi[0] = bs.get2(4);
			} else {
				bs.skipBits(3);    //private_bits
				scfsi[0] = bs.get2(4);
				scfsi[1] = bs.get2(4);
			}

			for (int gr = 0; gr < 2; gr++) {
				for (int ch = 0; ch < channels; ch++) {
					info = channelInfo[gr][ch];
					info.p2_3_len = bs.get3(12);
					//part2_3_bits += info.part2_3_length;
					info.big_values = bs.get2(9);
					info.global_gain = bs.get2(8);
					info.scalefac_compress = bs.get2(4);
					info.window_switching_flag = bs.get1();
					if ((info.window_switching_flag) != 0) {
						info.block_type = bs.get2(2);
						info.mixed_block_flag = bs.get1();
						info.table_select[0] = bs.get2(5);
						info.table_select[1] = bs.get2(5);
						info.subblock_gain[0] = bs.get2(3);
						info.subblock_gain[1] = bs.get2(3);
						info.subblock_gain[2] = bs.get2(3);
						if (info.block_type == 0) return -1;
						else if (info.block_type == 2 && info.mixed_block_flag == 0) info.r0_len = 8;
						else info.r0_len = 7;
						info.r1_len = 20 - info.r0_len;
					} else {
						info.table_select[0] = bs.get2(5);
						info.table_select[1] = bs.get2(5);
						info.table_select[2] = bs.get2(5);
						info.r0_len = bs.get2(4);
						info.r1_len = bs.get2(3);
						info.block_type = 0;
					}
					info.preflag = bs.get1();
					info.scalefac_scale = bs.get1();
					info.count1table_select = bs.get1();
				}
			}
		} else {
			// MPEG-2
			main_data_begin = bs.get2(8);
			if (channels == 1) {bs.get1();} else bs.get2(2);
			final ChannelInfo[] infos = channelInfo[0];
			for (int ch = 0; ch < channels; ch++) {
				info = infos[ch];
				info.p2_3_len = bs.get3(12);
				//part2_3_bits += info.part2_3_length;
				info.big_values = bs.get2(9);
				info.global_gain = bs.get2(8);
				info.scalefac_compress = bs.get2(9);
				info.window_switching_flag = bs.get1();
				if ((info.window_switching_flag) != 0) {
					info.block_type = bs.get2(2);
					info.mixed_block_flag = bs.get1();
					info.table_select[0] = bs.get2(5);
					info.table_select[1] = bs.get2(5);
					info.subblock_gain[0] = bs.get2(3);
					info.subblock_gain[1] = bs.get2(3);
					info.subblock_gain[2] = bs.get2(3);
					if (info.block_type == 0) return -1;
					else if (info.block_type == 2 && info.mixed_block_flag == 0) info.r0_len = 8;
					else {
						info.r0_len = 7;
						info.r1_len = 20 - info.r0_len;
					}
				} else {
					info.table_select[0] = bs.get2(5);
					info.table_select[1] = bs.get2(5);
					info.table_select[2] = bs.get2(5);
					info.r0_len = bs.get2(4);
					info.r1_len = bs.get2(3);
					info.block_type = 0;
					info.mixed_block_flag = 0;
				}
				info.scalefac_scale = bs.get1();
				info.count1table_select = bs.get1();
			}
		}

		return off + header.s_sideInfo;
	}
	//<<<<SIDE INFORMATION=====================================================

	//2.
	//>>>>SCALE FACTORS========================================================
	private final int[][] scalefacLong;        // [channels][23];
	private final int[][] scalefacShort;        // [channels][13*3];

	private static final int[] i_slen2;        // MPEG-2 slen for intensity stereo
	private static final int[] n_slen2;        // MPEG-2 slen for 'normal' mode

	// slen: 增益因子(scalefactor)比特数
	private static final byte[][][] nr_of_sfb;//[3][6][4]

	static {
		i_slen2 = new int[256];         // MPEG-2 slen for intensity_stereo
		n_slen2 = new int[512];         // MPEG-2 slen for normal mode
		nr_of_sfb = new byte[][][] {
			// ISO/IEC 13818-3 subclause 2.4.3.2 nr_of_sfbx x=1..4
			{{6, 5, 5, 5}, {6, 5, 7, 3}, {11, 10, 0, 0}, {7, 7, 7, 0}, {6, 6, 6, 3}, {8, 8, 5, 0}},
			{{9, 9, 9, 9}, {9, 9, 12, 6}, {18, 18, 0, 0}, {12, 12, 12, 0}, {12, 9, 9, 6}, {15, 12, 9, 0}},
			{{6, 9, 9, 9}, {6, 9, 12, 6}, {15, 18, 0, 0}, {6, 15, 12, 0}, {6, 12, 9, 6}, {6, 18, 9, 0}}};

		// ISO/IEC 13818-3 subclause 2.4.3.2 slenx, x=1..4
		int i, j, k, n;
		for (i = 0; i < 5; i++)
			for (j = 0; j < 6; j++)
				for (k = 0; k < 6; k++) {
					n = k + j * 6 + i * 36;
					i_slen2[n] = i | (j << 3) | (k << 6) | (3 << 12);
				}
		for (i = 0; i < 4; i++)
			for (j = 0; j < 4; j++)
				for (k = 0; k < 4; k++) {
					n = k + (j << 2) + (i << 4);
					i_slen2[n + 180] = i | (j << 3) | (k << 6) | (4 << 12);
				}
		for (i = 0; i < 4; i++)
			for (j = 0; j < 3; j++) {
				n = j + i * 3;
				i_slen2[n + 244] = i | (j << 3) | (5 << 12);
				n_slen2[n + 500] = i | (j << 3) | (2 << 12) | (1 << 15);
			}
		for (i = 0; i < 5; i++)
			for (j = 0; j < 5; j++)
				for (k = 0; k < 4; k++)
					for (int l = 0; l < 4; l++) {
						n = l + (k << 2) + (j << 4) + i * 80;
						n_slen2[n] = i | (j << 3) | (k << 6) | (l << 9);
					}
		for (i = 0; i < 5; i++)
			for (j = 0; j < 5; j++)
				for (k = 0; k < 4; k++) {
					n = k + (j << 2) + i * 20;
					n_slen2[n + 400] = i | (j << 3) | (k << 6) | (1 << 12);
				}
	}

	// MPEG-2
	private void getScaleFactors_2(int gr, int ch) {
		final ChannelInfo ci = channelInfo[gr][ch];

		rzeroBandLong = 0;

		int slen = (ch > 0) && header.isIntensityStereo() ? i_slen2[ci.scalefac_compress >> 1] : n_slen2[ci.scalefac_compress];

		ci.preflag = (slen >> 15) & 0x1;
		ci.p2_len = 0;

		int n = 0;
		int[] fc;
		if (ci.block_type == 2) {
			fc = scalefacShort[ch];
			n = (ci.mixed_block_flag) != 0 ? 2 : 1;
		} else {
			fc = scalefacLong[ch];
		}

		byte[] nr = nr_of_sfb[n][(slen >> 12) & 0x7];

		int i, scf = 0;
		for (i = 0; i < 4; i++) {
			int num = slen & 0x7;
			slen >>= 3;
			int band;
			if (num != 0) {
				for (band = 0; band < nr[i]; band++)
					fc[scf++] = mainIn.get3(num);
				ci.p2_len += nr[i] * num;
			} else {
				for (band = 0; band < nr[i]; band++)
					fc[scf++] = 0;
			}
		}

		n = (n << 1) + 1;
		for (i = 0; i < n; i++)
			fc[scf++] = 0;
	}

	// MPEG-1
	private static final byte[] sfLen = {0 << 4 | 0, 1 << 4 | 0, 2 << 4 | 0, 3 << 4 | 0,
										 0 << 4 | 3, 1 << 4 | 1, 2 << 4 | 1, 3 << 4 | 1,
										 1 << 4 | 2, 2 << 4 | 2, 3 << 4 | 2,
										 1 << 4 | 3, 2 << 4 | 3, 3 << 4 | 3,
										 2 << 4 | 4, 3 << 4 | 4};

	private void getScaleFactors_1(int gr, int ch) {
		final ChannelInfo ci = channelInfo[gr][ch];


		final byte b = sfLen[ci.scalefac_compress];
		final int len0 = b & 0xf, len1 = b >>> 4;

		final int[] l = scalefacLong[ch];
		final int[] s = scalefacShort[ch];

		int scf;
		int p2len = 0;
		final MainDataDecoder stream = this.mainIn;
		if (ci.window_switching_flag != 0 && ci.block_type == 2) {
			if (ci.mixed_block_flag != 0) {
				// MIXED block
				p2len = 17 * len0 + 18 * len1;
				for (scf = 0; scf < 8; scf++)
					l[scf] = stream.get2(len0);
				for (scf = 9; scf < 18; scf++)
					s[scf] = stream.get2(len0);
			} else {
				// pure SHORT block
				p2len = 18 * (len0 + len1);
				for (scf = 0; scf < 18; scf++)
					s[scf] = stream.get2(len0);
			}
			for (scf = 18; scf < 36; scf++)
				s[scf] = stream.get2(len1);
		} else {
			// LONG types 0,1,3
			int k = scfsi[ch];
			if (gr == 0) {
				p2len = 10 * (len0 + len1) + len0;
				for (scf = 0; scf < 11; scf++)
					l[scf] = stream.get2(len0);
				for (scf = 11; scf < 21; scf++)
					l[scf] = stream.get2(len1);
			} else {
				if ((k & 8) == 0) {
					for (scf = 0; scf < 6; scf++)
						l[scf] = stream.get2(len0);
					p2len += 6 * len0;
				}
				if ((k & 4) == 0) {
					for (scf = 6; scf < 11; scf++)
						l[scf] = stream.get2(len0);
					p2len += 5 * len0;
				}
				if ((k & 2) == 0) {
					for (scf = 11; scf < 16; scf++)
						l[scf] = stream.get2(len1);
					p2len += 5 * len1;
				}
				if ((k & 1) == 0) {
					for (scf = 16; scf < 21; scf++)
						l[scf] = stream.get2(len1);
					p2len += 5 * len1;
				}
			}
		}
		ci.p2_len = p2len;
	}
	//<<<<SCALE FACTORS========================================================

	//3.
	//>>>>HUFFMAN BITS=========================================================
	private final int[] hv;    //[32 * 18 + 4],暂存解得的哈夫曼值

	/*
	 * rzero_index[ch]: 初值为调用方法maindataStream.decodeHuff的返回值;在requantizer方法内被修正;
	 * 在hybrid方法内使用.
	 */
	private final int[] rzeroIndex = new int[2];

	private void huffBits(int gr, int ch) {
		final ChannelInfo ci = channelInfo[gr][ch];

		if (ci.window_switching_flag != 0) {
			int ver = header.ver();
			if (ver == Header.MPEG1 || (ver == Header.MPEG2 && ci.block_type == 2)) {
				ci.r1_off = 36;
			} else {
				if (ver == Header.MPEG2_5) {
					if (ci.block_type == 2 && ci.mixed_block_flag == 0) {ci.r1_off = sfbIndexLong[6];} else ci.r1_off = sfbIndexLong[8];
				} else {
					ci.r1_off = 54;
				}
			}
			ci.r2_off = 576;
		} else {
			int r1 = ci.r0_len + 1;
			int r2 = r1 + ci.r1_len + 1;
			if (r2 > sfbIndexLong.length - 1) r2 = sfbIndexLong.length - 1;
			ci.r1_off = sfbIndexLong[r1];
			ci.r2_off = sfbIndexLong[r2];
		}

		rzeroIndex[ch] = mainIn.huffman(ci, hv);  // 哈夫曼解码
	}
	//<<<<HUFFMAN BITS=========================================================

	//4.
	//>>>>REQUANTIZATION & REORDER=============================================
	private float[][] xrch0, xrch1;    // [maxGr][32*18]

	private static final float[] floatPow2,    // [256 + 118 + 4]
		floatPowIS;   // [8207]

	static {

		// 用于查表求 v^(4/3)，v是经哈夫曼解码出的一个(正)值，该值的范围是0..8191
		floatPowIS = new float[8207];
		int i;
		for (i = 0; i < 8207; i++)
			floatPowIS[i] = (float) Math.pow(i, 4.0 / 3.0);

		// 用于查表求 2^(-0.25 * i)
		// 按公式短块时i最大值: 210 - 0   + 8 * 7 + 4 * 15 + 2 = 328
		// 长块或短块时i最小值: 210 - 255 + 0     + 0      + 0 = -45
		// 查表法时下标范围为0..328+45.
		floatPow2 = new float[328 + 46];
		for (i = 0; i < 374; i++)
			floatPow2[i] = (float) Math.pow(2.0, -0.25 * (i - 45));
	}

	private final int[] widthLong,    // [22] 长块的增益因子频带(用一个增益因子逆量化频率线的条数)
		widthShort;   // [13] 短块的增益因子频带
	private int rzeroBandLong;
	private final int[] rzeroBandShort = new int[3];

	// ISO/IEC 11172-3 ANNEX B,Table 3-B.6. Layer III Preemphasis
	private static final int[] pretab = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 3, 3, 3, 2, 0};

	/**
	 * 逆量化并对短块(纯短块和混合块中的短块)重排序.在逆量化时赋值的变量:<br>
	 * rzero_bandL -- 长块非零哈夫曼值的频带数,用于强度立体声(intensity stereo)处理<br>
	 * rzero_bandS -- 短块非零哈夫曼值的频带数,用于强度立体声处理<br>
	 * rzero_index -- 非零哈夫曼值的"子带"数
	 * <p>
	 * Layer3 逆量化公式ISO/IEC 11172-3, 2.4.3.4
	 * <p>
	 * XRi = pow(2, 0.25 * (global_gain - 210)) <br>
	 * if (LONG block types) <br>
	 * 　　XRi *= pow(2, -0.5 * (1 + scalefac_scale) * (L[sfb] + preflag * pretab[sfb])) <br>
	 * if (SHORT block types) { <br>
	 * 　　XRi *= pow(2, 0.25 * -8 * subblock_gain[sfb]) <br>
	 * 　　XRi *= pow(2, 0.25 * -2 * (1 + scalefac_scale) * S[scf]) } <br>
	 * XRi *= sign(haffVal) * pow(abs(haffVal), 4/3) <br>
	 *
	 * @param gr 当前粒度。
	 * @param ch 当前声道。
	 * @param xrch 保存逆量化输出的576个值。
	 */
	private void requantizer(int gr, int ch, float[] xrch) {
		final int[] l = scalefacLong[ch];
		final ChannelInfo ci = channelInfo[gr][ch];
		final boolean preflag = ci.preflag == 1;
		final int shift = 1 + ci.scalefac_scale;
		final int maxi = rzeroIndex[ch];
		int pow2i = 255 - ci.global_gain;

		if (header.isMidStereo()) pow2i += 2; // 若声道模式为ms_stereo,要除以根2

		// pure SHORT blocks:
		// window_switching_flag=1, block_type=2, mixed_block_flag=0

		float requVal;
		int bi;
		int sfb = 0;
		int pre;
		int val;
		int hvIdx = 0;
		int xri = 0;
		if (ci.window_switching_flag == 1 && ci.block_type == 2) {
			rzeroBandShort[0] = rzeroBandShort[1] = rzeroBandShort[2] = -1;
			int width;
			int scf = 0;
			// 用于计算短块重排序后的下标
			int xriStart = 0;
			if (ci.mixed_block_flag == 1) {
				/*
				 * 混合块:
				 * 混合块的前8个频带是长块。 前8块各用一个增益因子逆量化，这8个增益因子 的频带总和为36，
				 * 这36条频率线用长块公式逆量化。
				 */
				rzeroBandLong = -1;
				for (; sfb < 8; sfb++) {
					pre = preflag ? pretab[sfb] : 0;
					requVal = floatPow2[pow2i + ((l[sfb] + pre) << shift)];
					width = widthLong[sfb];
					for (bi = 0; bi < width; bi++) {
						val = hv[hvIdx]; // 哈夫曼值
						if (val < 0) {
							xrch[hvIdx] = -requVal * floatPowIS[-val];
							rzeroBandLong = sfb;
						} else if (val > 0) {
							xrch[hvIdx] = requVal * floatPowIS[val];
							rzeroBandLong = sfb;
						} else {xrch[hvIdx] = 0;}
						hvIdx++;
					}
				}

				/*
				 * 混合块的后9个频带是被加窗的短块，其每一块同一窗口内3个值的增益因子频带相同。
				 * 后9块增益因子对应的频率子带值为widthShort[3..11]
				 */
				rzeroBandShort[0] = rzeroBandShort[1] = rzeroBandShort[2] = 2;
				rzeroBandLong++;
				sfb = 3;
				scf = 9;
				xriStart = 36; // 为短块重排序准备好下标
			}

			// 短块(混合块中的短块和纯短块)
			final int[] s = scalefacShort[ch];
			final int[] subgain = ci.subblock_gain;
			subgain[0] <<= 3;
			subgain[1] <<= 3;
			subgain[2] <<= 3;
			for (; hvIdx < maxi; sfb++) {
				width = widthShort[sfb];
				for (int win = 0; win < 3; win++) {
					requVal = floatPow2[pow2i + subgain[win] + (s[scf++] << shift)];
					xri = xriStart + win;
					for (bi = 0; bi < width; bi++) {
						val = hv[hvIdx];
						if (val < 0) {
							xrch[xri] = -requVal * floatPowIS[-val];
							rzeroBandShort[win] = sfb;
						} else if (val > 0) {
							xrch[xri] = requVal * floatPowIS[val];
							rzeroBandShort[win] = sfb;
						} else xrch[xri] = 0;
						hvIdx++;
						xri += 3;
					}
				}
				xriStart = xri - 2;
			}
			rzeroBandShort[0]++;
			rzeroBandShort[1]++;
			rzeroBandShort[2]++;
			rzeroBandLong++;
		} else {
			// 长块
			xri = -1;
			for (; hvIdx < maxi; sfb++) {
				pre = preflag ? pretab[sfb] : 0;
				requVal = floatPow2[pow2i + ((l[sfb] + pre) << shift)];
				bi = hvIdx + widthLong[sfb];
				for (; hvIdx < bi; hvIdx++) {
					val = hv[hvIdx];
					if (val < 0) {
						xrch[hvIdx] = -requVal * floatPowIS[-val];
						xri = sfb;
					} else if (val > 0) {
						xrch[hvIdx] = requVal * floatPowIS[val];
						xri = sfb;
					} else xrch[hvIdx] = 0;
				}
			}
			rzeroBandLong = xri + 1;
		}

		// 不逆量化0值区,置0.
		for (; hvIdx < 576; hvIdx++)
			xrch[hvIdx] = 0;
	}
	//<<<<REQUANTIZATION & REORDER=============================================

	//5.
	//>>>>STEREO===============================================================

	// 在requantizer方法内已经作了除以根2处理,ms_stereo内不再除以根2.
	private void ms_stereo(int gr) {
		final float[] xr0 = xrch0[gr], xr1 = xrch1[gr];
		final int rzero_xr = Math.max(rzeroIndex[0], rzeroIndex[1]);

		for (int xri = 0; xri < rzero_xr; xri++) {
			float tmp0 = xr0[xri];
			float tmp1 = xr1[xri];
			xr0[xri] = tmp0 + tmp1;
			xr1[xri] = tmp0 - tmp1;
		}
		rzeroIndex[0] = rzeroIndex[1] = rzero_xr; // ...不然可能导致声音细节丢失
	}

	// MPEG-1, intensity_stereo
	private static final float[] is_coef = new float[] {0.0f, 0.211324865f, 0.366025404f, 0.5f, 0.633974596f, 0.788675135f, 1.0f};
	// MPEG-2, intensity_stereo
	private static final float[][] lsf_is_coef = new float[][] {
		{0.840896415f, 0.707106781f, 0.594603558f, 0.5f, 0.420448208f, 0.353553391f, 0.297301779f, 0.25f, 0.210224104f, 0.176776695f, 0.148650889f, 0.125f, 0.105112052f, 0.088388348f, 0.074325445f},
		{0.707106781f, 0.5f, 0.353553391f, 0.25f, 0.176776695f, 0.125f, 0.088388348f, 0.0625f, 0.044194174f, 0.03125f, 0.022097087f, 0.015625f, 0.011048543f, 0.0078125f, 0.005524272f}};

	// 解码一个频带强度立体声,MPEG-1
	private void is_lines_1(int pos, int idx0, int width, int step, int gr) {
		for (int w = width; w > 0; w--) {
			float xr0 = xrch0[gr][idx0];
			xrch0[gr][idx0] = xr0 * is_coef[pos];
			xrch1[gr][idx0] = xr0 * is_coef[6 - pos];
			idx0 += step;
		}
	}

	// 解码一个频带强度立体声,MPEG-2
	private void is_lines_2(int tab2, int pos, int idx0, int width, int step, int gr) {
		for (int w = width; w > 0; w--) {
			float xr0 = xrch0[gr][idx0];
			if (pos == 0) {xrch1[gr][idx0] = xr0;} else {
				if ((pos & 1) == 0) {xrch1[gr][idx0] = xr0 * lsf_is_coef[tab2][(pos - 1) >> 1];} else {
					xrch0[gr][idx0] = xr0 * lsf_is_coef[tab2][(pos - 1) >> 1];
					xrch1[gr][idx0] = xr0;
				}
			}
			idx0 += step;
		}
	}

	/*
	 * 强度立体声(intensity stereo)解码
	 *
	 * ISO/IEC 11172-3不对混合块中的长块作强度立体声处理,但很多MP3解码程序都作了处理.
	 */
	private void intensity_stereo(int gr) {
		final ChannelInfo ci = channelInfo[gr][1]; //信息保存在右声道
		if (channelInfo[gr][0].mixed_block_flag != ci.mixed_block_flag || channelInfo[gr][0].block_type != ci.block_type) return;

		int scf, idx, sfb;
		if (isMPEG1) {    //MPEG-1
			if (ci.block_type == 2) {
				//MPEG-1, short block/mixed block
				for (int w3 = 0; w3 < 3; w3++) {
					sfb = rzeroBandShort[w3]; // 混合块sfb最小为3
					for (; sfb < 12; sfb++) {
						idx = 3 * sfbIndexShort[sfb] + w3;
						scf = scalefacShort[1][3 * sfb + w3];
						if (scf >= 7) continue;
						is_lines_1(scf, idx, widthShort[sfb], 3, gr);
					}
				}
			} else {
				//MPEG-1, long block
				for (sfb = rzeroBandLong; sfb <= 21; sfb++) {
					scf = scalefacLong[1][sfb];
					if (scf < 7) is_lines_1(scf, sfbIndexLong[sfb], widthLong[sfb], 1, gr);
				}
			}
		} else {    //MPEG-2
			int tab2 = ci.scalefac_compress & 0x1;
			if (ci.block_type == 2) {
				//MPEG-2, short block/mixed block
				for (int w3 = 0; w3 < 3; w3++) {
					sfb = rzeroBandShort[w3]; // 混合块sfb最小为3
					for (; sfb < 12; sfb++) {
						idx = 3 * sfbIndexShort[sfb] + w3;
						scf = scalefacShort[1][3 * sfb + w3];
						is_lines_2(tab2, scf, idx, widthShort[sfb], 3, gr);
					}
				}
			} else {
				//MPEG-2, long block
				for (sfb = rzeroBandLong; sfb <= 21; sfb++)
					is_lines_2(tab2, scalefacLong[1][sfb], sfbIndexLong[sfb], widthLong[sfb], 1, gr);
			}
		}
	}
	//<<<<STEREO===============================================================

	//6.
	//>>>>ANTIALIAS============================================================

	private void antialias(int gr, int ch, float[] xrch) {
		int maxidx;

		if (channelInfo[gr][ch].block_type == 2) {
			if (channelInfo[gr][ch].mixed_block_flag == 0) return;
			maxidx = 18;
		} else {maxidx = rzeroIndex[ch] - 18;}

		for (int i = 0; i < maxidx; i += 18) {
			float bu = xrch[i + 17];
			float bd = xrch[i + 18];
			xrch[i + 17] = bu * 0.85749293f + bd * 0.51449576f;
			xrch[i + 18] = bd * 0.85749293f - bu * 0.51449576f;
			bu = xrch[i + 16];
			bd = xrch[i + 19];
			xrch[i + 16] = bu * 0.8817420f + bd * 0.47173197f;
			xrch[i + 19] = bd * 0.8817420f - bu * 0.47173197f;
			bu = xrch[i + 15];
			bd = xrch[i + 20];
			xrch[i + 15] = bu * 0.94962865f + bd * 0.31337745f;
			xrch[i + 20] = bd * 0.94962865f - bu * 0.31337745f;
			bu = xrch[i + 14];
			bd = xrch[i + 21];
			xrch[i + 14] = bu * 0.98331459f + bd * 0.18191320f;
			xrch[i + 21] = bd * 0.98331459f - bu * 0.18191320f;
			bu = xrch[i + 13];
			bd = xrch[i + 22];
			xrch[i + 13] = bu * 0.99551782f + bd * 0.09457419f;
			xrch[i + 22] = bd * 0.99551782f - bu * 0.09457419f;
			bu = xrch[i + 12];
			bd = xrch[i + 23];
			xrch[i + 12] = bu * 0.99916056f + bd * 0.04096558f;
			xrch[i + 23] = bd * 0.99916056f - bu * 0.04096558f;
			bu = xrch[i + 11];
			bd = xrch[i + 24];
			xrch[i + 11] = bu * 0.99989920f + bd * 0.0141986f;
			xrch[i + 24] = bd * 0.99989920f - bu * 0.0141986f;
			bu = xrch[i + 10];
			bd = xrch[i + 25];
			xrch[i + 10] = bu * 0.99999316f + bd * 3.69997467e-3f;
			xrch[i + 25] = bd * 0.99999316f - bu * 3.69997467e-3f;
		}
	}
	//<<<<ANTIALIAS============================================================

	//7.
	//>>>>HYBRID(synthesize via iMDCT)=========================================
	private static final float[][] imdctWin = {
		{0.0322824f, 0.1072064f, 0.2014143f, 0.3256164f, 0.5f, 0.7677747f, 1.2412229f, 2.3319514f, 7.7441506f, -8.4512568f, -3.0390580f, -1.9483297f, -1.4748814f, -1.2071068f, -1.0327232f,
		 -0.9085211f, -0.8143131f, -0.7393892f, -0.6775254f, -0.6248445f, -0.5787917f, -0.5376016f, -0.5f, -0.4650284f, -0.4319343f, -0.4000996f, -0.3689899f, -0.3381170f, -0.3070072f, -0.2751725f,
		 -0.2420785f, -0.2071068f, -0.1695052f, -0.1283151f, -0.0822624f, -0.0295815f},
		{0.0322824f, 0.1072064f, 0.2014143f, 0.3256164f, 0.5f, 0.7677747f, 1.2412229f, 2.3319514f, 7.7441506f, -8.4512568f, -3.0390580f, -1.9483297f, -1.4748814f, -1.2071068f, -1.0327232f,
		 -0.9085211f, -0.8143131f, -0.7393892f, -0.6781709f, -0.6302362f, -0.5928445f, -0.5636910f, -0.5411961f, -0.5242646f, -0.5077583f, -0.4659258f, -0.3970546f, -0.3046707f, -0.1929928f,
		 -0.0668476f, -0.0f, -0.0f, -0.0f, -0.0f, -0.0f, -0.0f}, {/* block_type = 2 */},
		{0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.3015303f, 1.4659259f, 6.9781060f, -9.0940447f, -3.5390582f, -2.2903500f, -1.6627548f, -1.3065630f, -1.0828403f, -0.9305795f, -0.8213398f, -0.7400936f,
		 -0.6775254f, -0.6248445f, -0.5787917f, -0.5376016f, -0.5f, -0.4650284f, -0.4319343f, -0.4000996f, -0.3689899f, -0.3381170f, -0.3070072f, -0.2751725f, -0.2420785f, -0.2071068f, -0.1695052f,
		 -0.1283151f, -0.0822624f, -0.0295815f}};

	private void imdct12(float[] xrch, float[] pre, int off) {
		float out6 = 0, out7 = 0, out8 = 0, out9 = 0, out10 = 0, out11 = 0;
		float out12 = 0, out13 = 0, out14 = 0, out15 = 0, out16 = 0, out17 = 0;
		float f0 = 0, f1 = 0, f2 = 0, f3 = 0, f4 = 0, f5 = 0;

		for (int j = 0; j != 3; j++) {
			int i = j + off;
			//>>>>>>>>>>>> 12-point IMDCT
			//>>>>>> 6-point IDCT
			xrch[15 + i] += (xrch[12 + i] += xrch[9 + i]) + xrch[6 + i];
			xrch[9 + i] += (xrch[6 + i] += xrch[3 + i]) + xrch[i];
			xrch[3 + i] += xrch[i];

			//>>> 3-point IDCT on even
			float in1;
			float in2;
			float out1 = (in1 = xrch[i]) - (in2 = xrch[12 + i]);
			float in3 = in1 + in2 * 0.5f;
			float in4 = xrch[6 + i] * 0.8660254f;
			float out0 = in3 + in4;
			float out2 = in3 - in4;
			//<<< End 3-point IDCT on even

			//>>> 3-point IDCT on odd (for 6-point IDCT)
			float out4 = ((in1 = xrch[3 + i]) - (in2 = xrch[15 + i])) * 0.7071068f;
			in3 = in1 + in2 * 0.5f;
			in4 = xrch[9 + i] * 0.8660254f;
			float out5 = (in3 + in4) * 0.5176381f;
			float out3 = (in3 - in4) * 1.9318516f;
			//<<< End 3-point IDCT on odd

			// Output: butterflies on 2,3-point IDCT's (for 6-point IDCT)
			float tmp = out0;
			out0 += out5;
			out5 = tmp - out5;
			tmp = out1;
			out1 += out4;
			out4 = tmp - out4;
			tmp = out2;
			out2 += out3;
			out3 = tmp - out3;
			//<<<<<< End 6-point IDCT
			//<<<<<<<<<<<< End 12-point IDCT

			tmp = out3 * 0.1072064f;
			switch (j) {
				case 0:
					out6 = tmp;
					out7 = out4 * 0.5f;
					out8 = out5 * 2.3319512f;
					out9 = -out5 * 3.0390580f;
					out10 = -out4 * 1.2071068f;
					out11 = -tmp * 7.5957541f;

					f0 = out2 * 0.6248445f;
					f1 = out1 * 0.5f;
					f2 = out0 * 0.4000996f;
					f3 = out0 * 0.3070072f;
					f4 = out1 * 0.2071068f;
					f5 = out2 * 0.0822623f;
					break;
				case 1:
					out12 = tmp - f0;
					out13 = out4 * 0.5f - f1;
					out14 = out5 * 2.3319512f - f2;
					out15 = -out5 * 3.0390580f - f3;
					out16 = -out4 * 1.2071068f - f4;
					out17 = -tmp * 7.5957541f - f5;

					f0 = out2 * 0.6248445f;
					f1 = out1 * 0.5f;
					f2 = out0 * 0.4000996f;
					f3 = out0 * 0.3070072f;
					f4 = out1 * 0.2071068f;
					f5 = out2 * 0.0822623f;
					break;
				case 2:
					// output
					i = off;
					xrch[i] = pre[i];
					xrch[i + 1] = pre[i + 1];
					xrch[i + 2] = pre[i + 2];
					xrch[i + 3] = pre[i + 3];
					xrch[i + 4] = pre[i + 4];
					xrch[i + 5] = pre[i + 5];
					xrch[i + 6] = pre[i + 6] + out6;
					xrch[i + 7] = pre[i + 7] + out7;
					xrch[i + 8] = pre[i + 8] + out8;
					xrch[i + 9] = pre[i + 9] + out9;
					xrch[i + 10] = pre[i + 10] + out10;
					xrch[i + 11] = pre[i + 11] + out11;
					xrch[i + 12] = pre[i + 12] + out12;
					xrch[i + 13] = pre[i + 13] + out13;
					xrch[i + 14] = pre[i + 14] + out14;
					xrch[i + 15] = pre[i + 15] + out15;
					xrch[i + 16] = pre[i + 16] + out16;
					xrch[i + 17] = pre[i + 17] + out17;

					pre[i] = tmp - f0;
					pre[i + 1] = out4 * 0.5f - f1;
					pre[i + 2] = out5 * 2.3319512f - f2;
					pre[i + 3] = -out5 * 3.0390580f - f3;
					pre[i + 4] = -out4 * 1.2071068f - f4;
					pre[i + 5] = -tmp * 7.5957541f - f5;
					pre[i + 6] = -out2 * 0.6248445f;
					pre[i + 7] = -out1 * 0.5f;
					pre[i + 8] = -out0 * 0.4000996f;
					pre[i + 9] = -out0 * 0.3070072f;
					pre[i + 10] = -out1 * 0.2071068f;
					pre[i + 11] = -out2 * 0.0822623f;
					pre[i + 12] = pre[i + 13] = pre[i + 14] = 0;
					pre[i + 15] = pre[i + 16] = pre[i + 17] = 0;
			}
		}
	}

	private static void imdct36(float[] xrch, float[] preBlck, int off, int block_type) {

		//>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>> 36-point IDCT
		//>>>>>>>>>>>>>>>>>> 18-point IDCT for odd
		xrch[off + 17] += (xrch[off + 16] += xrch[off + 15]) + xrch[off + 14];
		xrch[off + 15] += (xrch[off + 14] += xrch[off + 13]) + xrch[off + 12];
		xrch[off + 13] += (xrch[off + 12] += xrch[off + 11]) + xrch[off + 10];
		xrch[off + 11] += (xrch[off + 10] += xrch[off + 9]) + xrch[off + 8];
		xrch[off + 9] += (xrch[off + 8] += xrch[off + 7]) + xrch[off + 6];
		xrch[off + 7] += (xrch[off + 6] += xrch[off + 5]) + xrch[off + 4];
		xrch[off + 5] += (xrch[off + 4] += xrch[off + 3]) + xrch[off + 2];
		xrch[off + 3] += (xrch[off + 2] += xrch[off + 1]) + xrch[off];
		xrch[off + 1] += xrch[off];

		//>>>>>>>>> 9-point IDCT on even
		/*
		 *  for(m = 0; m < 9; m++) {
		 *      sum = 0;
		 *      for(n = 0; n < 18; n += 2)
		 *          sum += in[n] * cos(PI36 * (2 * m + 1) * n);
		 *      out18[m] = sum;
		 *  }
		 */
		float in0 = xrch[off] + xrch[off + 12] * 0.5f;
		float in1 = xrch[off] - xrch[off + 12];
		float in2 = xrch[off + 8] + xrch[off + 16] - xrch[off + 4];

		float out4 = in1 + in2;

		float in3 = in1 - in2 * 0.5f;
		// cos(PI/6)
		float in4 = (xrch[off + 10] + xrch[off + 14] - xrch[off + 2]) * 0.8660254f;

		float out1 = in3 - in4;
		float out7 = in3 + in4;

		//cos( PI/9)
		float in5 = (xrch[off + 4] + xrch[off + 8]) * 0.9396926f;
		//cos(4PI/9)
		float in6 = (xrch[off + 16] - xrch[off + 8]) * 0.1736482f;
		//cos(2PI/9)
		float in7 = -(xrch[off + 4] + xrch[off + 16]) * 0.7660444f;

		float in17 = in0 - in5 - in7;
		float in8 = in5 + in0 + in6;
		float in9 = in0 + in7 - in6;

		//cos(PI/6)
		float in12 = xrch[off + 6] * 0.8660254f;
		//cos(PI/18)
		float in10 = (xrch[off + 2] + xrch[off + 10]) * 0.9848078f;
		//cos(7PI/18)
		float in11 = (xrch[off + 14] - xrch[off + 10]) * 0.3420201f;

		float in13 = in10 + in11 + in12;

		float out0 = in8 + in13;
		float out8 = in8 - in13;

		//cos(5PI/18)
		float in14 = -(xrch[off + 2] + xrch[off + 14]) * 0.6427876f;
		float in15 = in10 + in14 - in12;
		float in16 = in11 - in14 - in12;

		float out3 = in9 + in15;
		float out5 = in9 - in15;

		float out2 = in17 + in16;
		float out6 = in17 - in16;
		//<<<<<<<<< End 9-point IDCT on even

		//>>>>>>>>> 9-point IDCT on odd
		/*
		 *  for(m = 0; m < 9; m++) {
		 *      sum = 0;
		 *      for(n = 0;n < 18; n += 2)
		 *          sum += in[n + 1] * cos(PI36 * (2 * m + 1) * n);
		 *      out18[17-m] = sum;
		 * }
		 */
		in0 = xrch[off + 1] + xrch[off + 13] * 0.5f;    //cos(PI/3)
		in1 = xrch[off + 1] - xrch[off + 13];
		in2 = xrch[off + 9] + xrch[off + 17] - xrch[off + 5];

		//cos(PI/4)
		float out13 = (in1 + in2) * 0.7071068f;

		in3 = in1 - in2 * 0.5f;
		in4 = (xrch[off + 11] + xrch[off + 15] - xrch[off + 3]) * 0.8660254f;    //cos(PI/6)

		// 0.5/cos( PI/12)
		float out16 = (in3 - in4) * 0.5176381f;
		// 0.5/cos(5PI/12)
		float out10 = (in3 + in4) * 1.9318517f;

		in5 = (xrch[off + 5] + xrch[off + 9]) * 0.9396926f;    // cos( PI/9)
		in6 = (xrch[off + 17] - xrch[off + 9]) * 0.1736482f;    // cos(4PI/9)
		in7 = -(xrch[off + 5] + xrch[off + 17]) * 0.7660444f;    // cos(2PI/9)

		in17 = in0 - in5 - in7;
		in8 = in5 + in0 + in6;
		in9 = in0 + in7 - in6;

		in12 = xrch[off + 7] * 0.8660254f;                // cos(PI/6)
		in10 = (xrch[off + 3] + xrch[off + 11]) * 0.9848078f;    // cos(PI/18)
		in11 = (xrch[off + 15] - xrch[off + 11]) * 0.3420201f;    // cos(7PI/18)

		in13 = in10 + in11 + in12;

		// 0.5/cos(PI/36)
		float out17 = (in8 + in13) * 0.5019099f;
		// 0.5/cos(17PI/36)
		float out9 = (in8 - in13) * 5.7368566f;

		in14 = -(xrch[off + 3] + xrch[off + 15]) * 0.6427876f;    // cos(5PI/18)
		in15 = in10 + in14 - in12;
		in16 = in11 - in14 - in12;

		// 0.5/cos(7PI/36)
		float out14 = (in9 + in15) * 0.6103873f;
		// 0.5/cos(11PI/36)
		float out12 = (in9 - in15) * 0.8717234f;

		// 0.5/cos(5PI/36)
		float out15 = (in17 + in16) * 0.5516890f;
		// 0.5/cos(13PI/36)
		float out11 = (in17 - in16) * 1.1831008f;
		//<<<<<<<<< End. 9-point IDCT on odd

		// Butterflies on 9-point IDCT's
		float tmp = out0;
		out0 += out17;
		out17 = tmp - out17;
		tmp = out1;
		out1 += out16;
		out16 = tmp - out16;
		tmp = out2;
		out2 += out15;
		out15 = tmp - out15;
		tmp = out3;
		out3 += out14;
		out14 = tmp - out14;
		tmp = out4;
		out4 += out13;
		out13 = tmp - out13;
		tmp = out5;
		out5 += out12;
		out12 = tmp - out12;
		tmp = out6;
		out6 += out11;
		out11 = tmp - out11;
		tmp = out7;
		out7 += out10;
		out10 = tmp - out10;
		tmp = out8;
		out8 += out9;
		out9 = tmp - out9;
		//<<<<<<<<<<<<<<<<<< End of 18-point IDCT
		//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<< End of 36-point IDCT

		// output
		float[] win = imdctWin[block_type];

		xrch[off] = preBlck[off] + out9 * win[0];
		xrch[off + 1] = preBlck[off + 1] + out10 * win[1];
		xrch[off + 2] = preBlck[off + 2] + out11 * win[2];
		xrch[off + 3] = preBlck[off + 3] + out12 * win[3];
		xrch[off + 4] = preBlck[off + 4] + out13 * win[4];
		xrch[off + 5] = preBlck[off + 5] + out14 * win[5];
		xrch[off + 6] = preBlck[off + 6] + out15 * win[6];
		xrch[off + 7] = preBlck[off + 7] + out16 * win[7];
		xrch[off + 8] = preBlck[off + 8] + out17 * win[8];
		xrch[off + 9] = preBlck[off + 9] + out17 * win[9];
		xrch[off + 10] = preBlck[off + 10] + out16 * win[10];
		xrch[off + 11] = preBlck[off + 11] + out15 * win[11];
		xrch[off + 12] = preBlck[off + 12] + out14 * win[12];
		xrch[off + 13] = preBlck[off + 13] + out13 * win[13];
		xrch[off + 14] = preBlck[off + 14] + out12 * win[14];
		xrch[off + 15] = preBlck[off + 15] + out11 * win[15];
		xrch[off + 16] = preBlck[off + 16] + out10 * win[16];
		xrch[off + 17] = preBlck[off + 17] + out9 * win[17];

		preBlck[off] = out8 * win[18];
		preBlck[off + 1] = out7 * win[19];
		preBlck[off + 2] = out6 * win[20];
		preBlck[off + 3] = out5 * win[21];
		preBlck[off + 4] = out4 * win[22];
		preBlck[off + 5] = out3 * win[23];
		preBlck[off + 6] = out2 * win[24];
		preBlck[off + 7] = out1 * win[25];
		preBlck[off + 8] = out0 * win[26];
		preBlck[off + 9] = out0 * win[27];
		preBlck[off + 10] = out1 * win[28];
		preBlck[off + 11] = out2 * win[29];
		preBlck[off + 12] = out3 * win[30];
		preBlck[off + 13] = out4 * win[31];
		preBlck[off + 14] = out5 * win[32];
		preBlck[off + 15] = out6 * win[33];
		preBlck[off + 16] = out7 * win[34];
		preBlck[off + 17] = out8 * win[35];
	}

	private final float[] preBlckCh0;
	private float[] preBlckCh1; // [32*18],右左声道FIFO队列

	private void hybrid(int gr, int ch, float[] xrch, float[] preb) {
		final ChannelInfo ci = channelInfo[gr][ch];
		int i;

		final int maxi = rzeroIndex[ch];
		for (i = 0; i < maxi; i += 18) {
			int block_type = ((ci.window_switching_flag != 0) && (ci.mixed_block_flag != 0) && (i < 36)) ? 0 : ci.block_type;

			if (block_type == 2) {imdct12(xrch, preb, i);} else imdct36(xrch, preb, i, block_type);
		}

		// 0值区
		for (; i < 576; i++) {
			xrch[i] = preb[i];
			preb[i] = 0;
		}
	}
	//<<<<HYBRID(synthesize via iMDCT)=========================================

	//8.
	//>>>>INVERSE QUANTIZE SAMPLES=============================================
	//
	// 在decoder.ConcurrentSynthesis.run 方法内实现多相频率倒置
	//
	//<<<<INVERSE QUANTIZE SAMPLES=============================================

	//9.
	//>>>>SYNTHESIZE VIA POLYPHASE MDCT========================================
	//
	// 在decoder.ConcurrentSynthesis.run()方法内调用filter.synthesisSubBand()
	// 实现多相合成滤波
	//
	//<<<<SYNTHESIZE VIA POLYPHASE MDCT========================================

	//10.
	//>>>>OUTPUT PCM SAMPLES===================================================
	//
	// decoder.AudioBuffer, output.Audio
	//
	//<<<<OUTPUT PCM SAMPLES===================================================

	/**
	 * 解码1帧Layer Ⅲ
	 */
	public int decodeFrame(byte[] b, int off) {
		/*
		 * part1 : side information
		 */
		int i = getSideInfo(b, off);
		if (i < 0) {
			return off + header.getFrameSize() - 4; // 跳过这一帧
		}
		off = i;

		/*
		 * part2_3: scale factors + huffman bits
		 * length: ((part2_3_bits + 7) >> 3) bytes
		 */
		int maindataSize = header.s_main;
		int bufSize = mainIn.getSize();
		if (bufSize < main_data_begin) {
			// 若出错，不解码当前这一帧， 将主数据(main_data)填入位流缓冲区后返回，
			// 在解码下一帧时全部或部分利用填入的这些主数据。
			mainIn.append(b, off, maindataSize);
			return off + maindataSize;
		}

		// 丢弃上一帧的填充位
		int discard = bufSize - mainIn.getBytePos() - main_data_begin;

		mainIn.skipBytes(discard);

		// 主数据添加到位流缓冲区
		mainIn.append(b, off, maindataSize);
		off += maindataSize;

		for (int gr = 0; gr < granules; gr++) {
			if (isMPEG1) {getScaleFactors_1(gr, 0);} else getScaleFactors_2(gr, 0);
			huffBits(gr, 0);
			requantizer(gr, 0, xrch0[gr]);

			if (channels == 2) {
				if (isMPEG1) {getScaleFactors_1(gr, 1);} else getScaleFactors_2(gr, 1);
				huffBits(gr, 1);
				requantizer(gr, 1, xrch1[gr]);

				if (header.isMidStereo()) ms_stereo(gr);
				if (header.isIntensityStereo()) intensity_stereo(gr);
			}

			antialias(gr, 0, xrch0[gr]);
			hybrid(gr, 0, xrch0[gr], preBlckCh0);

			if (channels == 2) {
				antialias(gr, 1, xrch1[gr]);
				hybrid(gr, 1, xrch1[gr], preBlckCh1);
			}
		}

		// 可以在这调用maindataStream.skipBits(part2_3_bits & 7)丢弃填充位，
		// 更好的方法是放在解码下一帧主数据之前处理，如果位流错误，可以顺便纠正。

		try {
			filterCh0.await();
			if (channels == 2) filterCh1.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
			close();
			return off;
		}

		synthesis.flush();

		// 异步多相合成滤波
		xrch0 = filterCh0.swapBuf();
		DECODER.pushTask(filterCh0);
		if (channels == 2) {
			xrch1 = filterCh1.swapBuf();
			DECODER.pushTask(filterCh1);
		}

		return off;
	}

	public void close() {
		filterCh0.cancel(true);
		if (channels == 2) filterCh1.cancel(true);
	}

	/**
	 * 一个粒度内一个声道的信息
	 */
	static final class ChannelInfo {
		// 从位流读取数据依次初始化的14个变量
		int p2_3_len;
		int big_values;
		private int global_gain;
		private int scalefac_compress;
		private int window_switching_flag;
		private int block_type;
		private int mixed_block_flag;
		int[] table_select;
		private final int[] subblock_gain;
		private int r0_len;
		private int r1_len;
		private int preflag;
		private int scalefac_scale;
		int count1table_select;

		// 这3个通过计算初始化
		int r1_off, r2_off;
		int p2_len; // 增益因子(scale-factor)比特数

		private ChannelInfo() {
			table_select = new int[3];
			subblock_gain = new int[3];
		}
	}

}