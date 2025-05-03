package roj.media.audio.mp3;

/**
 * 子带多相合成滤波器
 */
final class Synthesis {
	private final MP3Decoder owner;

	/*
	 * 暂存DCT输出结果的FIFO队列。
	 */
	private final float[][] fifobuf;

	/*
	 * fifobuf的偏移量，用它完成FIFO队列的移位操作。
	 */
	private final int[] fifoIndex;

	private int maxPCM;

	/**
	 * @param channels 声道数，用于计算输出PCM时的步长
	 */
	public Synthesis(MP3Decoder owner, int channels) {
		this.owner = owner;
		fifobuf = new float[channels][1024];
		fifoIndex = new int[channels];
	}

	/**
	 * 获取PCM最大峰值。
	 *
	 * @return PCM样本的最大峰值。该最大值可用于音量规格化。
	 */
	public int getMaxPCM() { return this.maxPCM; }

	final void flush() { owner.flush(); }

	public static final int[][] u_vector = {
		{0, 96, 128, 224, 256, 352, 384, 480, 512, 608, 640, 736, 768, 864, 896, 992},
		{64, 160, 192, 288, 320, 416, 448, 544, 576, 672, 704, 800, 832, 928, 960, 32},
		{128, 224, 256, 352, 384, 480, 512, 608, 640, 736, 768, 864, 896, 992, 0, 96},
		{192, 288, 320, 416, 448, 544, 576, 672, 704, 800, 832, 928, 960, 32, 64, 160},
		{256, 352, 384, 480, 512, 608, 640, 736, 768, 864, 896, 992, 0, 96, 128, 224},
		{320, 416, 448, 544, 576, 672, 704, 800, 832, 928, 960, 32, 64, 160, 192, 288},
		{384, 480, 512, 608, 640, 736, 768, 864, 896, 992, 0, 96, 128, 224, 256, 352},
		{448, 544, 576, 672, 704, 800, 832, 928, 960, 32, 64, 160, 192, 288, 320, 416},
		{512, 608, 640, 736, 768, 864, 896, 992, 0, 96, 128, 224, 256, 352, 384, 480},
		{576, 672, 704, 800, 832, 928, 960, 32, 64, 160, 192, 288, 320, 416, 448, 544},
		{640, 736, 768, 864, 896, 992, 0, 96, 128, 224, 256, 352, 384, 480, 512, 608},
		{704, 800, 832, 928, 960, 32, 64, 160, 192, 288, 320, 416, 448, 544, 576, 672},
		{768, 864, 896, 992, 0, 96, 128, 224, 256, 352, 384, 480, 512, 608, 640, 736},
		{832, 928, 960, 32, 64, 160, 192, 288, 320, 416, 448, 544, 576, 672, 704, 800},
		{896, 992, 0, 96, 128, 224, 256, 352, 384, 480, 512, 608, 640, 736, 768, 864},
		{960, 32, 64, 160, 192, 288, 320, 416, 448, 544, 576, 672, 704, 800, 832, 928}};

	/**
	 * 子带多相合成滤波
	 *
	 * @param samples 源数据，为32个样本值。
	 * @param ch 当前的声道。左声道0，右声道1。
	 */
	public void synthesisSubBand(float[] samples, int ch) {
		final float[] fifo = fifobuf[ch];

		//1. Shift
		final int[] findex = this.fifoIndex;
		findex[ch] = (findex[ch] - 64) & 0x3FF;
		//960,896,832,768,704,640,576,512,448,384,320,256,192,128,64,0

		//2. Matrixing
		dct32to64(samples, fifo, findex[ch]);

		if ((findex[ch] & 63) != 0) throw new IllegalStateException("Invalid pcm buf");

		/*
		 * 向PCM缓冲区写入数据的步长值，左右声道的PCM数据在PCM缓冲区内是交替排列的。
		 * 指示解码某一声道时写入一次数据后，下一次应该写入的位置。
		 */
		int off = owner.pcmOff[ch];
		//3. Build the U vector
		final int[] u_vector = Synthesis.u_vector[findex[ch] >> 6];

		final byte[] buf = this.owner.pcm;
		final int step = (findex.length == 2) ? 4 : 2;
		//4. Dewindowing
		//5. Calculate and output 32 samples
		final float[][] dewin = this.dewin;
		for (int i = 0; i < 32; i++, off += step) {
			float[] win = dewin[i];

			float sum = 0;
			for (int j = 0; j < 16; j++) {
				sum += win[j] * fifo[i + u_vector[j]];
			}

			//clip
			int pcmi = sum > 32767 ? 32767 : (sum < -32768 ? -32768 : (int) sum);
			buf[off] = (byte) pcmi;
			buf[off + 1] = (byte) (pcmi >>> 8);
			if (pcmi > maxPCM) maxPCM = pcmi;
		}

		owner.pcmOff[ch] = off;
	}

	/**
	 * 一个子带的矩阵运算。
	 *
	 * @param src 输入的32个样本值。
	 * @param dest 暂存输出值的长度为1024个元素的FIFO队列。
	 * @param off FIFO队列的偏移量。一个子带一次矩阵运算输出64个值连续存储到FIFO队列，存储的起始位置由off指定。
	 */
	private static void dct32to64(float[] src, float[] dest, int off) {
		//>>>>>>>>>>>>>>>>
		// 用DCT16计算DCT32输出[0..31]的偶数下标元素
		float in0 = src[0] + src[31];
		float in1 = src[1] + src[30];
		float in2 = src[2] + src[29];
		float in3 = src[3] + src[28];
		float in4 = src[4] + src[27];
		float in5 = src[5] + src[26];
		float in6 = src[6] + src[25];
		float in7 = src[7] + src[24];
		float in8 = src[8] + src[23];
		float in9 = src[9] + src[22];
		float in10 = src[10] + src[21];
		float in11 = src[11] + src[20];
		float in12 = src[12] + src[19];
		float in13 = src[13] + src[18];
		float in14 = src[14] + src[17];
		float in15 = src[15] + src[16];

		//DCT16
		//{
		//>>>>>>>> 用DCT8计算DCT16输出[0..15]的偶数下标元素
		float d8_0 = in0 + in15;
		float d8_1 = in1 + in14;
		float d8_2 = in2 + in13;
		float d8_3 = in3 + in12;
		float d8_4 = in4 + in11;
		float d8_5 = in5 + in10;
		float d8_6 = in6 + in9;
		float d8_7 = in7 + in8;

		//DCT8. 加(减)法29,乘法12次
		//{
		//>>>>e 用DCT4计算DCT8的输出[0..7]的偶数下标元素
		float out1 = d8_0 + d8_7;
		float out3 = d8_1 + d8_6;
		float out5 = d8_2 + d8_5;
		float out7 = d8_3 + d8_4;

		//>>e DCT2
		float ein0 = out1 + out7;
		float ein1 = out3 + out5;
		dest[off + 48] = -ein0 - ein1;
		dest[off] = (ein0 - ein1) * 0.7071068f;// 0.5/cos(PI/4)

		//>>o DCT2
		// 0.5/cos( PI/8)
		float oin0 = (out1 - out7) * 0.5411961f;
		// 0.5/cos(3PI/8)
		float oin1 = (out3 - out5) * 1.3065630f;

		float out2 = oin0 + oin1;
		// cos(PI/4)
		float out12 = (oin0 - oin1) * 0.7071068f;

		dest[off + 40] = dest[off + 56] = -out2 - out12;
		dest[off + 8] = out12;
		//<<<<e 完成计算DCT8的输出[0..7]的偶数下标元素

		//>>>>o 用DCT4计算DCT8的输出[0..7]的奇数下标元素
		//o DCT4 part1
		out1 = (d8_0 - d8_7) * 0.5097956f;    // 0.5/cos( PI/16)
		out3 = (d8_1 - d8_6) * 0.6013449f;    // 0.5/cos(3PI/16)
		out5 = (d8_2 - d8_5) * 0.8999762f;    // 0.5/cos(5PI/16)
		out7 = (d8_3 - d8_4) * 2.5629154f;    // 0.5/cos(7PI/16)

		//o DCT4 part2

		//e DCT2 part1
		ein0 = out1 + out7;
		ein1 = out3 + out5;

		//o DCT2 part1
		oin0 = (out1 - out7) * 0.5411961f;    // 0.5/cos(PI/8)
		oin1 = (out3 - out5) * 1.3065630f;    // 0.5/cos(3PI/8)

		//e DCT2 part2
		out1 = ein0 + ein1;
		out5 = (ein0 - ein1) * 0.7071068f;    // cos(PI/4)

		//o DCT2 part2
		out3 = oin0 + oin1;
		out7 = (oin0 - oin1) * 0.7071068f;    // cos(PI/4)
		out3 += out7;

		//o DCT4 part3
		dest[off + 44] = dest[off + 52] = -out1 - out3;    //out1+=out3
		dest[off + 36] = dest[off + 60] = -out3 - out5;    //out3+=out5
		dest[off + 4] = out5 + out7;                    //out5+=out7
		dest[off + 12] = out7;
		//<<<<o 完成计算DCT8的输出[0..7]的奇数下标元素
		//}
		//<<<<<<<< 完成计算DCT16输出[0..15]的偶数下标元素

		//-----------------------------------------------------------------

		//>>>>>>>> 用DCT8计算DCT16输出[0..15]的奇数下标元素
		d8_0 = (in0 - in15) * 0.5024193f;    // 0.5/cos( 1 * PI/32)
		d8_1 = (in1 - in14) * 0.5224986f;    // 0.5/cos( 3 * PI/32)
		d8_2 = (in2 - in13) * 0.5669440f;    // 0.5/cos( 5 * PI/32)
		d8_3 = (in3 - in12) * 0.6468218f;    // 0.5/cos( 7 * PI/32)
		d8_4 = (in4 - in11) * 0.7881546f;    // 0.5/cos( 9 * PI/32)
		d8_5 = (in5 - in10) * 1.0606777f;    // 0.5/cos(11 * PI/32)
		d8_6 = (in6 - in9) * 1.7224471f;    // 0.5/cos(13 * PI/32)
		d8_7 = (in7 - in8) * 5.1011486f;    // 0.5/cos(15 * PI/32)

		//DCT8
		//{
		//>>>>e 用DCT4计算DCT8的输出[0..7]的偶数下标元素.
		out3 = d8_0 + d8_7;
		out7 = d8_1 + d8_6;
		float out11 = d8_2 + d8_5;
		float out15 = d8_3 + d8_4;

		//>>e DCT2
		ein0 = out3 + out15;
		ein1 = out7 + out11;
		out1 = ein0 + ein1;
		// 0.5/cos(PI/4)
		float out9 = (ein0 - ein1) * 0.7071068f;

		//>>o DCT2
		oin0 = (out3 - out15) * 0.5411961f;    // 0.5/cos( PI/8)
		oin1 = (out7 - out11) * 1.3065630f;    // 0.5/cos(3PI/8)

		out5 = oin0 + oin1;
		// cos(PI/4)
		float out13 = (oin0 - oin1) * 0.7071068f;

		out5 += out13;
		//<<<<e 完成计算DCT8的输出[0..7]的偶数下标元素

		//>>>>o 用DCT4计算DCT8的输出[0..7]的奇数下标元素
		//o DCT4 part1
		out3 = (d8_0 - d8_7) * 0.5097956f;    // 0.5/cos( PI/16)
		out7 = (d8_1 - d8_6) * 0.6013449f;    // 0.5/cos(3PI/16)
		out11 = (d8_2 - d8_5) * 0.8999762f;    // 0.5/cos(5PI/16)
		out15 = (d8_3 - d8_4) * 2.5629154f;    // 0.5/cos(7PI/16)

		//o DCT4 part2

		//e DCT2 part1
		ein0 = out3 + out15;
		ein1 = out7 + out11;

		//o DCT2 part1
		oin0 = (out3 - out15) * 0.5411961f;    // 0.5/cos(PI/8)
		oin1 = (out7 - out11) * 1.3065630f;    // 0.5/cos(3PI/8)

		//e DCT2 part2
		out3 = ein0 + ein1;
		out11 = (ein0 - ein1) * 0.7071068f;    // cos(PI/4)

		//o DCT2 part2
		out7 = oin0 + oin1;
		out15 = (oin0 - oin1) * 0.7071068f;    // cos(PI/4)
		out7 += out15;

		//o DCT4 part3
		out3 += out7;
		out7 += out11;
		out11 += out15;
		//<<<<o 完成计算DCT8的输出[0..7]的奇数下标元素
		//}

		dest[off + 46] = dest[off + 50] = -out1 - out3;    //out1 += out3
		dest[off + 42] = dest[off + 54] = -out3 - out5;    //out3 += out5
		dest[off + 38] = dest[off + 58] = -out5 - out7;    //out5 += out7
		dest[off + 34] = dest[off + 62] = -out7 - out9;    //out7 += out9
		dest[off + 2] = out9 + out11;                    //out9 += out11
		dest[off + 6] = out11 + out13;                //out11 += out13
		dest[off + 10] = out13 + out15;                //out13 += out15
		//<<<<<<<< 完成计算DCT16输出[0..15]的奇数下标元素
		//}
		dest[off + 14] = out15;    //out[i + 14]=out32[30]
		//<<<<<<<<<<<<<<<<
		// 完成计算DCT32输出[0..31]的偶数下标元素

		//=====================================================================

		//>>>>>>>>>>>>>>>>
		// 用DCT16计算DCT32输出[0..31]的奇数下标元素
		in0 = (src[0] - src[31]) * 0.5006030f;    // 0.5/cos( 1 * PI/64)
		in1 = (src[1] - src[30]) * 0.5054710f;    // 0.5/cos( 3 * PI/64)
		in2 = (src[2] - src[29]) * 0.5154473f;    // 0.5/cos( 5 * PI/64)
		in3 = (src[3] - src[28]) * 0.5310426f;    // 0.5/cos( 7 * PI/64)
		in4 = (src[4] - src[27]) * 0.5531039f;    // 0.5/cos( 9 * PI/64)
		in5 = (src[5] - src[26]) * 0.5829350f;    // 0.5/cos(11 * PI/64)
		in6 = (src[6] - src[25]) * 0.6225041f;    // 0.5/cos(13 * PI/64)
		in7 = (src[7] - src[24]) * 0.6748083f;    // 0.5/cos(15 * PI/64)
		in8 = (src[8] - src[23]) * 0.7445362f;    // 0.5/cos(17 * PI/64)
		in9 = (src[9] - src[22]) * 0.8393496f;    // 0.5/cos(19 * PI/64)
		in10 = (src[10] - src[21]) * 0.9725682f;    // 0.5/cos(21 * PI/64)
		in11 = (src[11] - src[20]) * 1.1694399f;    // 0.5/cos(23 * PI/64)
		in12 = (src[12] - src[19]) * 1.4841646f;    // 0.5/cos(25 * PI/64)
		in13 = (src[13] - src[18]) * 2.0577810f;    // 0.5/cos(27 * PI/64)
		in14 = (src[14] - src[17]) * 3.4076084f;    // 0.5/cos(29 * PI/64)
		in15 = (src[15] - src[16]) * 10.190008f;    // 0.5/cos(31 * PI/64)

		//DCT16
		//{
		//>>>>>>>> 用DCT8计算DCT16输出[0..15]的偶数下标元素
		d8_0 = in0 + in15;
		d8_1 = in1 + in14;
		d8_2 = in2 + in13;
		d8_3 = in3 + in12;
		d8_4 = in4 + in11;
		d8_5 = in5 + in10;
		d8_6 = in6 + in9;
		d8_7 = in7 + in8;

		//DCT8
		//{
		//>>>>e 用DCT4计算DCT8的输出[0..7]的偶数下标元素
		out1 = d8_0 + d8_7;
		out3 = d8_1 + d8_6;
		out5 = d8_2 + d8_5;
		out7 = d8_3 + d8_4;

		//>>e DCT2
		ein0 = out1 + out7;
		ein1 = out3 + out5;
		float out0 = ein0 + ein1;
		// 0.5/cos(PI/4)
		float out8 = (ein0 - ein1) * 0.7071068f;

		//>>o DCT2
		oin0 = (out1 - out7) * 0.5411961f;    // 0.5/cos( PI/8)
		oin1 = (out3 - out5) * 1.3065630f;    // 0.5/cos(3PI/8)

		float out4 = oin0 + oin1;
		out12 = (oin0 - oin1) * 0.7071068f;// cos(PI/4)

		out4 += out12;
		//<<<<e 完成计算DCT8的输出[0..7]的偶数下标元素

		//>>>>o 用DCT4计算DCT8的输出[0..7]的奇数下标元素
		//o DCT4 part1
		out1 = (d8_0 - d8_7) * 0.5097956f;    // 0.5/cos( PI/16)
		out3 = (d8_1 - d8_6) * 0.6013449f;    // 0.5/cos(3PI/16)
		out5 = (d8_2 - d8_5) * 0.8999762f;    // 0.5/cos(5PI/16)
		out7 = (d8_3 - d8_4) * 2.5629154f;    // 0.5/cos(7PI/16)

		//o DCT4 part2

		//e DCT2 part1
		ein0 = out1 + out7;
		ein1 = out3 + out5;

		//o DCT2 part1
		oin0 = (out1 - out7) * 0.5411961f;    // 0.5/cos(PI/8)
		oin1 = (out3 - out5) * 1.3065630f;    // 0.5/cos(3PI/8)

		//e DCT2 part2
		out2 = ein0 + ein1;
		// cos(PI/4)
		float out10 = (ein0 - ein1) * 0.7071068f;

		//o DCT2 part2
		float out6 = oin0 + oin1;
		float out14 = (oin0 - oin1) * 0.7071068f;
		out6 += out14;

		//o DCT4 part3
		out2 += out6;
		out6 += out10;
		out10 += out14;
		//<<<<o 完成计算DCT8的输出[0..7]的奇数下标元素
		//}
		//<<<<<<<< 完成计算DCT16输出[0..15]的偶数下标元素

		//-----------------------------------------------------------------

		//>>>>>>>> 用DCT8计算DCT16输出[0..15]的奇数下标元素
		d8_0 = (in0 - in15) * 0.5024193f;    // 0.5/cos( 1 * PI/32)
		d8_1 = (in1 - in14) * 0.5224986f;    // 0.5/cos( 3 * PI/32)
		d8_2 = (in2 - in13) * 0.5669440f;    // 0.5/cos( 5 * PI/32)
		d8_3 = (in3 - in12) * 0.6468218f;    // 0.5/cos( 7 * PI/32)
		d8_4 = (in4 - in11) * 0.7881546f;    // 0.5/cos( 9 * PI/32)
		d8_5 = (in5 - in10) * 1.0606777f;    // 0.5/cos(11 * PI/32)
		d8_6 = (in6 - in9) * 1.7224471f;    // 0.5/cos(13 * PI/32)
		d8_7 = (in7 - in8) * 5.1011486f;    // 0.5/cos(15 * PI/32)

		//DCT8
		//{
		//>>>>e 用DCT4计算DCT8的输出[0..7]的偶数下标元素.
		out1 = d8_0 + d8_7;
		out3 = d8_1 + d8_6;
		out5 = d8_2 + d8_5;
		out7 = d8_3 + d8_4;

		//>>e DCT2
		ein0 = out1 + out7;
		ein1 = out3 + out5;
		in0 = ein0 + ein1;    //out0->in0,out4->in4
		in4 = (ein0 - ein1) * 0.7071068f;    // 0.5/cos(PI/4)

		//>>o DCT2
		oin0 = (out1 - out7) * 0.5411961f;    // 0.5/cos( PI/8)
		oin1 = (out3 - out5) * 1.3065630f;    // 0.5/cos(3PI/8)

		in2 = oin0 + oin1;                    //out2->in2,out6->in6
		in6 = (oin0 - oin1) * 0.7071068f;    // cos(PI/4)

		in2 += in6;
		//<<<<e 完成计算DCT8的输出[0..7]的偶数下标元素

		//>>>>o 用DCT4计算DCT8的输出[0..7]的奇数下标元素
		//o DCT4 part1
		out1 = (d8_0 - d8_7) * 0.5097956f;    // 0.5/cos( PI/16)
		out3 = (d8_1 - d8_6) * 0.6013449f;    // 0.5/cos(3PI/16)
		out5 = (d8_2 - d8_5) * 0.8999762f;    // 0.5/cos(5PI/16)
		out7 = (d8_3 - d8_4) * 2.5629154f;    // 0.5/cos(7PI/16)

		//o DCT4 part2

		//e DCT2 part1
		ein0 = out1 + out7;
		ein1 = out3 + out5;

		//o DCT2 part1
		oin0 = (out1 - out7) * 0.5411961f;    // 0.5/cos(PI/8)
		oin1 = (out3 - out5) * 1.3065630f;    // 0.5/cos(3PI/8)

		//e DCT2 part2
		out1 = ein0 + ein1;
		out5 = (ein0 - ein1) * 0.7071068f;    // cos(PI/4)

		//o DCT2 part2
		out3 = oin0 + oin1;
		out15 = (oin0 - oin1) * 0.7071068f;
		out3 += out15;

		//o DCT4 part3
		out1 += out3;
		out3 += out5;
		out5 += out15;
		//<<<<o 完成计算DCT8的输出[0..7]的奇数下标元素
		//}
		//out15=out7
		out13 = in6 + out15;    //out13=out6+out7
		out11 = out5 + in6;        //out11=out5+out6
		out9 = in4 + out5;        //out9 =out4+out5
		out7 = out3 + in4;        //out7 =out3+out4
		out5 = in2 + out3;        //out5 =out2+out3
		out3 = out1 + in2;        //out3 =out1+out2
		out1 += in0;            //out1 =out0+out1
		//<<<<<<<< 完成计算DCT16输出[0..15]的奇数下标元素
		//}

		//DCT32out[i]=out[i]+out[i+1]; DCT32out[31]=out[15]
		dest[off + 47] = dest[off + 49] = -out0 - out1;
		dest[off + 45] = dest[off + 51] = -out1 - out2;
		dest[off + 43] = dest[off + 53] = -out2 - out3;
		dest[off + 41] = dest[off + 55] = -out3 - out4;
		dest[off + 39] = dest[off + 57] = -out4 - out5;
		dest[off + 37] = dest[off + 59] = -out5 - out6;
		dest[off + 35] = dest[off + 61] = -out6 - out7;
		dest[off + 33] = dest[off + 63] = -out7 - out8;
		dest[off + 1] = out8 + out9;
		dest[off + 3] = out9 + out10;
		dest[off + 5] = out10 + out11;
		dest[off + 7] = out11 + out12;
		dest[off + 9] = out12 + out13;
		dest[off + 11] = out13 + out14;
		dest[off + 13] = out14 + out15;
		dest[off + 15] = out15;
		//<<<<<<<<<<<<<<<<

		dest[off + 16] = 0;

		dest[off + 17] = -out15;    //out[i + 17] = -out[i + 15]
		dest[off + 18] = -dest[off + 14];
		dest[off + 19] = -dest[off + 13];
		dest[off + 20] = -dest[off + 12];
		dest[off + 21] = -dest[off + 11];
		dest[off + 22] = -dest[off + 10];
		dest[off + 23] = -dest[off + 9];
		dest[off + 24] = -dest[off + 8];
		dest[off + 25] = -dest[off + 7];
		dest[off + 26] = -dest[off + 6];
		dest[off + 27] = -dest[off + 5];
		dest[off + 28] = -dest[off + 4];
		dest[off + 29] = -dest[off + 3];
		dest[off + 30] = -dest[off + 2];
		dest[off + 31] = -dest[off + 1];
		dest[off + 32] = -dest[off];
	}

	/*
	 * dewin: D[i] * 32767 (i=0..511), 然后重新排序
	 * D[]: Coefficients Di of the synthesis window. ISO/IEC 11172-3 ANNEX_B Table 3-B.3
	 */
	private final float[][] dewin = { // [32][16]
		{0f, -14.5f, 106.5f, -229.5f, 1018.5f, -2576.5f, 3287f, -18744.5f, 37519f, 18744.5f, 3287f, 2576.5f, 1018.5f, 229.5f, 106.5f, 14.5f},
		{-0.5f, -15.5f, 109f, -259.5f, 1000f, -2758.5f, 2979.5f, -19668f, 37496f, 17820f, 3567f, 2394f, 1031.5f, 200.5f, 104f, 13f},
		{-0.5f, -17.5f, 111f, -290.5f, 976f, -2939.5f, 2644f, -20588f, 37428f, 16895.5f, 3820f, 2212.5f, 1040f, 173.5f, 101f, 12f},
		{-0.5f, -19f, 112.5f, -322.5f, 946.5f, -3118.5f, 2280.5f, -21503f, 37315f, 15973.5f, 4046f, 2031.5f, 1043.5f, 147f, 98f, 10.5f},
		{-0.5f, -20.5f, 113.5f, -355.5f, 911f, -3294.5f, 1888f, -22410.5f, 37156.5f, 15056f, 4246f, 1852.5f, 1042.5f, 122f, 95f, 9.5f},
		{-0.5f, -22.5f, 114f, -389.5f, 869.5f, -3467.5f, 1467.5f, -23308.5f, 36954f, 14144.5f, 4420f, 1675.5f, 1037.5f, 98.5f, 91.5f, 8.5f},
		{-0.5f, -24.5f, 114f, -424f, 822f, -3635.5f, 1018.5f, -24195f, 36707.5f, 13241f, 4569.5f, 1502f, 1028.5f, 76.5f, 88f, 8f},
		{-1f, -26.5f, 113.5f, -459.5f, 767.5f, -3798.5f, 541f, -25068.5f, 36417.5f, 12347f, 4694.5f, 1331.5f, 1016f, 55.5f, 84.5f, 7f},
		{-1f, -29f, 112f, -495.5f, 707f, -3955f, 35f, -25926.5f, 36084.5f, 11464.5f, 4796f, 1165f, 1000.5f, 36f, 80.5f, 6.5f},
		{-1f, -31.5f, 110.5f, -532f, 640f, -4104.5f, -499f, -26767f, 35710f, 10594.5f, 4875f, 1003f, 981f, 18f, 77f, 5.5f},
		{-1f, -34f, 107.5f, -568.5f, 565.5f, -4245.5f, -1061f, -27589f, 35295f, 9739f, 4931.5f, 846f, 959.5f, 1f, 73.5f, 5f},
		{-1.5f, -36.5f, 104f, -605f, 485f, -4377.5f, -1650f, -28389f, 34839.5f, 8899.5f, 4967.5f, 694f, 935f, -14.5f, 69.5f, 4.5f},
		{-1.5f, -39.5f, 100f, -641.5f, 397f, -4499f, -2266.5f, -29166.5f, 34346f, 8077.5f, 4983f, 547.5f, 908.5f, -28.5f, 66f, 4f},
		{-2f, -42.5f, 94.5f, -678f, 302.5f, -4609.5f, -2909f, -29919f, 33814.5f, 7274f, 4979.5f, 407f, 879.5f, -41.5f, 62.5f, 3.5f},
		{-2f, -45.5f, 88.5f, -714f, 201f, -4708f, -3577f, -30644.5f, 33247f, 6490f, 4958f, 272.5f, 849f, -53f, 58.5f, 3.5f},
		{-2.5f, -48.5f, 81.5f, -749f, 92.5f, -4792.5f, -4270f, -31342f, 32645f, 5727.5f, 4919f, 144f, 817f, -63.5f, 55.5f, 3f},
		{-2.5f, -52f, 73f, -783.5f, -22.5f, -4863.5f, -4987.5f, -32009.5f, 32009.5f, 4987.5f, 4863.5f, 22.5f, 783.5f, -73f, 52f, 2.5f},
		{-3f, -55.5f, 63.5f, -817f, -144f, -4919f, -5727.5f, -32645f, 31342f, 4270f, 4792.5f, -92.5f, 749f, -81.5f, 48.5f, 2.5f},
		{-3.5f, -58.5f, 53f, -849f, -272.5f, -4958f, -6490f, -33247f, 30644.5f, 3577f, 4708f, -201f, 714f, -88.5f, 45.5f, 2f},
		{-3.5f, -62.5f, 41.5f, -879.5f, -407f, -4979.5f, -7274f, -33814.5f, 29919f, 2909f, 4609.5f, -302.5f, 678f, -94.5f, 42.5f, 2f},
		{-4f, -66f, 28.5f, -908.5f, -547.5f, -4983f, -8077.5f, -34346f, 29166.5f, 2266.5f, 4499f, -397f, 641.5f, -100f, 39.5f, 1.5f},
		{-4.5f, -69.5f, 14.5f, -935f, -694f, -4967.5f, -8899.5f, -34839.5f, 28389f, 1650f, 4377.5f, -485f, 605f, -104f, 36.5f, 1.5f},
		{-5f, -73.5f, -1f, -959.5f, -846f, -4931.5f, -9739f, -35295f, 27589f, 1061f, 4245.5f, -565.5f, 568.5f, -107.5f, 34f, 1f},
		{-5.5f, -77f, -18f, -981f, -1003f, -4875f, -10594.5f, -35710f, 26767f, 499f, 4104.5f, -640f, 532f, -110.5f, 31.5f, 1f},
		{-6.5f, -80.5f, -36f, -1000.5f, -1165f, -4796f, -11464.5f, -36084.5f, 25926.5f, -35f, 3955f, -707f, 495.5f, -112f, 29f, 1f},
		{-7f, -84.5f, -55.5f, -1016f, -1331.5f, -4694.5f, -12347f, -36417.5f, 25068.5f, -541f, 3798.5f, -767.5f, 459.5f, -113.5f, 26.5f, 1f},
		{-8f, -88f, -76.5f, -1028.5f, -1502f, -4569.5f, -13241f, -36707.5f, 24195f, -1018.5f, 3635.5f, -822f, 424f, -114f, 24.5f, 0.5f},
		{-8.5f, -91.5f, -98.5f, -1037.5f, -1675.5f, -4420f, -14144.5f, -36954f, 23308.5f, -1467.5f, 3467.5f, -869.5f, 389.5f, -114f, 22.5f, 0.5f},
		{-9.5f, -95f, -122f, -1042.5f, -1852.5f, -4246f, -15056f, -37156.5f, 22410.5f, -1888f, 3294.5f, -911f, 355.5f, -113.5f, 20.5f, 0.5f},
		{-10.5f, -98f, -147f, -1043.5f, -2031.5f, -4046f, -15973.5f, -37315f, 21503f, -2280.5f, 3118.5f, -946.5f, 322.5f, -112.5f, 19f, 0.5f},
		{-12f, -101f, -173.5f, -1040f, -2212.5f, -3820f, -16895.5f, -37428f, 20588f, -2644f, 2939.5f, -976f, 290.5f, -111f, 17.5f, 0.5f},
		{-13f, -104f, -200.5f, -1031.5f, -2394f, -3567f, -17820f, -37496f, 19668f, -2979.5f, 2758.5f, -1000f, 259.5f, -109f, 15.5f, 0.5f}
	};
}