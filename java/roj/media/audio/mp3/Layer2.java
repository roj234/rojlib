package roj.media.audio.mp3;

/**
 * 解码Layer Ⅱ。
 */
final class Layer2 extends Layer {
	private final int channels, max_sb;

	private final byte[][] allocation;    //[channels][sblimit]
	private final byte[][] scfsi;            //[channels][sblimit]
	private final byte[][][] scalefactor;    //[channels][sblimit][3]

	private final float[][] syin;        //[channels][3][32]

	//[3][2][16]
	private static final byte[][][] aidx_table = new byte[][][] {
		{{0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 1, 1, 1, 1, 1, 0}, {0, 2, 2, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}},
		{{0, 2, 2, 2, 2, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0}, {0, 2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}},
		{{0, 3, 3, 3, 3, 3, 3, 0, 0, 0, 1, 1, 1, 1, 1, 0}, {0, 3, 3, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 0}}};

	// Layer1也用到factor[]
	// ISO/IEC 11172-3 Table 3-B.1
	// scalefactor值为'0000 00'..'1111 11'(0..63),应该有64个值.在末尾补一个数0.0f
	public static final float[] SCALE_FACTOR = {
		2.00000000000000f, 1.58740105196820f, 1.25992104989487f, 1.00000000000000f, 0.79370052598410f, 0.62996052494744f, 0.50000000000000f, 0.39685026299205f,
		0.31498026247372f, 0.25000000000000f, 0.19842513149602f, 0.15749013123686f, 0.12500000000000f, 0.09921256574801f, 0.07874506561843f, 0.06250000000000f,
		0.04960628287401f, 0.03937253280921f, 0.03125000000000f, 0.02480314143700f, 0.01968626640461f, 0.01562500000000f, 0.01240157071850f, 0.00984313320230f,
		0.00781250000000f, 0.00620078535925f, 0.00492156660115f, 0.00390625000000f, 0.00310039267963f, 0.00246078330058f, 0.00195312500000f, 0.00155019633981f,
		0.00123039165029f, 0.00097656250000f, 0.00077509816991f, 0.00061519582514f, 0.00048828125000f, 0.00038754908495f, 0.00030759791257f, 0.00024414062500f,
		0.00019377454248f, 0.00015379895629f, 0.00012207031250f, 0.00009688727124f, 0.00007689947814f, 0.00006103515625f, 0.00004844363562f, 0.00003844973907f,
		0.00003051757813f, 0.00002422181781f, 0.00001922486954f, 0.00001525878906f, 0.00001211090890f, 0.00000961243477f, 0.00000762939453f, 0.00000605545445f,
		0.00000480621738f, 0.00000381469727f, 0.00000302772723f, 0.00000240310869f, 0.00000190734863f, 0.00000151386361f, 0.00000120155435f, 0.0f};

	public Layer2(Header h, MP3Decoder audio) {
		super(h, audio, new BitStream());
		channels = h.channels();

		//aidx,sblimit...
		int aidx;
		if (h.ver() == Header.MPEG2) {
			aidx = 4;
			max_sb = 30;
		} else {
			aidx = aidx_table[h.sampling_frequency][2 - channels][h.bitrate_index];
			max_sb = switch (aidx) {
				case 0 -> 27;
				case 1 -> 30;
				case 2 -> 8;
				default -> 12;
			};
		}

		allocation = new byte[channels][max_sb];
		scfsi = new byte[channels][max_sb];
		scalefactor = new byte[channels][max_sb][3];
		syin = new float[channels * 3][32];

		i_nbal = nbal[aidx];
		i_sbquant_offset = sbquant_offset[aidx];
	}

	// cq_xxx: Layer II classes of quantization, ISO/IEC 11172-3 Table 3-B.4
	// length = 17
	private static final char[] cq_steps = {3, 5, 7, 9, 15, 31, 63, 127, 255, 511, 1023, 2047, 4095, 8191, 16383, 32767, 65535};
	private static final float[]
		cq_C = {1.3333333f, 1.6f, 1.1428571f, 1.77777778f, 1.0666667f, 1.0322581f, 1.015873f, 1.007874f, 1.0039216f, 1.0019569f, 1.0009775f, 1.0004885f, 1.0002442f, 1.000122f,
				1.000061f, 1.0000305f, 1.00001525902f},
		cq_D = {0.5f, 0.5f, 0.25f, 0.5f, 0.125f, 0.0625f, 0.03125f, 0.015625f, 0.0078125f, 0.00390625f, 0.001953125f,
				0.0009765625f, 0.00048828125f, 0.00024414063f, 0.00012207031f, 0.00006103516f, 0.00003051758f};
	private static final byte[] cq_bits = {5, 7, 3, 10, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

	@SuppressWarnings("fallthrough")
	private void requantization(int index, int gr, int ch, int sb) {
		int nlevels = cq_steps[index];

		int sm1, sm2, sm3;

		int nb = -1;
		switch (index) { // if ((nb = group[index]) != 0) {
			case 0: nb = 2;
			case 1: if (nb == -1) nb = 3;
			case 3: if (nb == -1) nb = 4;

				int c = data.get3(cq_bits[index]);

				sm1 = c % nlevels;
				c /= nlevels;
				sm2 = c % nlevels;
				c /= nlevels;
				sm3 = c % nlevels;

				nlevels = (1 << nb) - 1;    //用于计算fractional

			break;
			default:
				nb = cq_bits[index];
				sm1 = data.get3(nb);
				sm2 = data.get3(nb);
				sm3 = data.get3(nb);
			break;
		}

		float fractional = 2.0f * sm1 / (++nlevels) - 1.0f;

		final float[][] syin = this.syin;
		if (ch == -1) {
			final float factor0 = SCALE_FACTOR[scalefactor[0][sb][gr >>= 2]], factor1 = SCALE_FACTOR[scalefactor[1][sb][gr >> 2]];

			float pf = cq_C[index] * (fractional + cq_D[index]);
			syin[0 * 3 + 0][sb] = pf * factor0;
			syin[1 * 3 + 0][sb] = pf * factor1;

			fractional = 2.0f * sm2 / nlevels - 1.0f;
			pf = cq_C[index] * (fractional + cq_D[index]);
			syin[0 * 3 + 1][sb] = pf * factor0;
			syin[1 * 3 + 1][sb] = pf * factor1;

			fractional = 2.0f * sm3 / nlevels - 1.0f;
			pf = cq_C[index] * (fractional + cq_D[index]);
			syin[0 * 3 + 2][sb] = pf * factor0;
			syin[1 * 3 + 2][sb] = pf * factor1;

		} else {
			final float factor = SCALE_FACTOR[scalefactor[ch][sb][gr >> 2]];
			// s'' = C * (s''' + D)
			// s' = factor * s''
			syin[ch * 3 + 0][sb] = cq_C[index] * (fractional + cq_D[index]) * factor;

			fractional = 2.0f * sm2 / nlevels - 1.0f;
			syin[ch * 3 + 1][sb] = cq_C[index] * (fractional + cq_D[index]) * factor;

			fractional = 2.0f * sm3 / nlevels - 1.0f;
			syin[ch * 3 + 2][sb] = cq_C[index] * (fractional + cq_D[index]) * factor;
		}
	}

	// length = 5, ?
	private static final byte[][] nbal = new byte[][] {
		// ISO/IEC 11172-3 Table 3-B.2a
		new byte[] {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2},
		// ISO/IEC 11172-3 Table 3-B.2b
		new byte[] {4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2},
		// ISO/IEC 11172-3 Table 3-B.2c
		new byte[] {4, 4, 3, 3, 3, 3, 3, 3},
		// ISO/IEC 11172-3 Table 3-B.2d
		new byte[] {4, 4, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3},
		// ISO/IEC 13818-3 Table B.1
		new byte[] {4, 4, 4, 4, 3, 3, 3, 3, 3, 3, 3, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2}};
	private static final byte[][] sbquant_offset = new byte[][] {
		// ISO/IEC 11172-3 Table 3-B.2a
		{7, 7, 7, 6, 6, 6, 6, 6, 6, 6, 6, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 0, 0, 0, 0},

		// ISO/IEC 11172-3 Table 3-B.2b
		{7, 7, 7, 6, 6, 6, 6, 6, 6, 6, 6, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 0, 0, 0, 0, 0, 0, 0},

		// ISO/IEC 11172-3 Table 3-B.2c
		{5, 5, 2, 2, 2, 2, 2, 2},

		// ISO/IEC 11172-3 Table 3-B.2d
		{5, 5, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2},

		// ISO/IEC 13818-3 Table B.1
		{4, 4, 4, 4, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1}};

	private final byte[] i_nbal;
	private final byte[] i_sbquant_offset;

	private static final byte[][] i_offset_table;
	static {
		byte[] _3 = {0, 1, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
		i_offset_table = new byte[][] {
			{0, 1, 16}, _3, _3, {0, 1, 2, 3, 4, 5, 16},
			{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14},
			_3,
			{0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 16},
			{0, 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}
		};
	}

	@SuppressWarnings("fallthrough")
	public int decodeFrame(byte[] b, int off) {
		int mainSize = header.s_main;
		if (data.append(b, off, mainSize) < mainSize) return off + mainSize; // skip
		off += mainSize;

		int maindata_begin = data.getBytePos();
		int bound = (header.getMode() == 1) ? ((header.getModeExtension() + 1) << 2) : 32;
		if (bound > max_sb) bound = max_sb;

		int sb, ch;
		/*
		 * 1. Bit allocation decoding
		 */
		for (ch = 0; ch < channels; ch++)
			for (sb = 0; sb < bound; sb++)
				allocation[ch][sb] = (byte) data.get2(i_nbal[sb]); // 2..4 bits
		for (sb = bound; sb < max_sb; sb++)
			allocation[1][sb] = allocation[0][sb] = (byte) data.get2(i_nbal[sb]);

		/*
		 * 2. Scalefactor selection information decoding
		 */
		for (ch = 0; ch < channels; ch++)
			for (sb = 0; sb < max_sb; sb++)
				scfsi[ch][sb] = allocation[ch][sb] != 0 ? (byte) data.get2(2) : 2;

		/*
		 * 3. Scalefactor decoding
		 */
		for (ch = 0; ch < channels; ++ch)
			for (sb = 0; sb < max_sb; ++sb)
				if (allocation[ch][sb] != 0) {
					final byte[] sf = scalefactor[ch][sb];

					sf[0] = (byte) data.get2(6);

					byte si = scfsi[ch][sb];
					switch (si) {
						case 2:
							sf[2] = sf[1] = sf[0];
							break;
						case 0:
							sf[1] = (byte) data.get2(6);
						case 1:
						case 3:
							sf[2] = (byte) data.get2(6);
					}
					if ((si & 1) == 1) sf[1] = sf[si - 1];
				}

		final float[][] syin = this.syin;
		for (int gr = 0; gr < 12; gr++) {
			/*
			 * 4. Requantization of subband samples
			 */
			int index;
			for (ch = 0; ch < channels; ch++)
				for (sb = 0; sb < bound; sb++)
					if ((index = allocation[ch][sb]) != 0) {
						index = i_offset_table[i_sbquant_offset[sb]][index - 1];
						requantization(index, gr, ch, sb);
					} else {syin[ch * 3/* + 0*/][sb] = syin[ch * 3 + 1][sb] = syin[ch * 3 + 2][sb] = 0;}

			//mode=1(Joint Stereo)
			for (sb = bound; sb < max_sb; sb++)
				if ((index = allocation[0][sb]) != 0) {
					index = i_offset_table[i_sbquant_offset[sb]][index - 1];
					requantization(index, gr, -1, sb);
				} else {
					for (ch = 0; ch < channels; ch++)
						syin[ch * 3/* + 0*/][sb] = syin[ch * 3 + 1][sb] = syin[ch * 3 + 2][sb] = 0;
				}

			int s;
			for (ch = 0; ch < channels; ch++)
				for (s = 0; s < 3; s++)
					for (sb = max_sb; sb < 32; sb++)
						syin[ch * 3 + s][sb] = 0;

			/*
			 * 5. Synthesis subband filter
			 */
			for (ch = 0; ch < channels; ch++)
				for (s = 0; s < 3; s++)
					synthesis.synthesisSubBand(syin[ch * 3 + s], ch);
		}

		/*
		 * 6. Ancillary bits
		 */
		int discard = mainSize + maindata_begin - data.getBytePos();
		data.skipBytes(discard);

		/*
		 * 7. output
		 */
		synthesis.flush();

		return off;
	}
}