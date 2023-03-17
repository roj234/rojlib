/*
 * Layer1.java -- MPEG-1 Audio Layer I 解码
 */
package roj.sound.mp3;

import roj.sound.util.AudioBuffer;

final class Layer1 extends Layer {
	byte[][] allocation, scalefactor;    //[2][32]
	float[][] syin;            //[2][32]

	public Layer1(Header header, AudioBuffer audio) {
		this(header, audio, new BitStream());
	}

	public Layer1(Header header, AudioBuffer audio, BitStream stream) {
		super(header, audio, stream);
		allocation = new byte[2][32];
		scalefactor = new byte[2][32];
		syin = new float[2][32];
	}

	/*
	 * 逆量化公式:
	 * s'' = (2^nb / (2^nb - 1)) * (s''' + 2^(-nb + 1))
	 * s' = factor * s''
	 */
	private float requantization(int ch, int sb, int nb) {
		int samplecode = data.get3(nb);
		/*int*/
		float nlevels = (1 << nb);
		float requ = 2.0f * samplecode / nlevels - 1.0f;    //s'''
		requ += (float) Math.pow(2, 1 - nb);
		return requ * (nlevels / (nlevels - 1)) * Layer2.factor[scalefactor[ch][sb]];
	}

	public int decodeFrame(byte[] b, int off) {
		int nch = header.channels();

		int bound = (header.getMode() == 1) ? ((header.getModeExtension() + 1) * 4) : 32;

		int mainData = header.getMainDataSize();
		if (data.append(b, off, mainData) < mainData) return -1;
		off += mainData;

		int maindata_begin = data.getBytePos();

		//1. Bit allocation decoding
		int sb, ch, nb;
		for (ch = 0; ch < nch; ++ch)
			for (sb = 0; sb < bound; sb++) {
				nb = data.get2(4);
				if (nb == 15) return -2;
				allocation[ch][sb] = (byte) ((nb != 0) ? (nb + 1) : 0);
			}
		for (sb = bound; sb < 32; sb++) {
			nb = data.get2(4);
			if (nb == 15) return -2;
			allocation[0][sb] = (byte) ((nb != 0) ? (nb + 1) : 0);
		}

		//2. Scalefactor decoding
		for (ch = 0; ch < nch; ch++)
			for (sb = 0; sb < 32; sb++)
				if (allocation[ch][sb] != 0) scalefactor[ch][sb] = (byte) data.get2(6);

		final float[][] syin = this.syin;
		for (int gr = 0; gr < 12; gr++) {
			//3. Requantization of subband samples
			for (ch = 0; ch < nch; ch++)
				for (sb = 0; sb < bound; sb++) {
					nb = allocation[ch][sb];
					if (nb == 0) {syin[ch][sb] = 0;} else syin[ch][sb] = requantization(ch, sb, nb);
				}
			//mode=1(Joint Stereo)
			for (sb = bound; sb < 32; sb++)
				if ((nb = allocation[0][sb]) != 0) {
					for (ch = 0; ch < nch; ch++)
						syin[ch][sb] = requantization(ch, sb, nb);
				} else {
					for (ch = 0; ch < nch; ch++)
						syin[ch][sb] = 0;
				}

			//4. Synthesis subband filter
			for (ch = 0; ch < nch; ch++)
				synthesis.synthesisSubBand(syin[ch], ch);
		}

		//5. Ancillary bits
		int discard = mainData + maindata_begin - data.getBytePos();
		data.skipBytes(discard);

		//6. output
		super.flush();

		return off;
	}
}
