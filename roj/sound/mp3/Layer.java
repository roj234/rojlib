package roj.sound.mp3;

import roj.sound.util.AudioBuffer;

/**
 * 抽象帧解码器
 *
 * @version 0.3
 */
public abstract class Layer {
	final Synthesis synthesis;
	final AudioBuffer buf;
	final Header header;
	final BitStream data;

	public Layer(Header h, AudioBuffer buf, BitStream data) {
		this.data = data;
		this.header = h;
		this.buf = buf;
		this.synthesis = new Synthesis(buf, h.channels());
	}

	/**
	 * 从此位置开始解码一帧
	 *
	 * @return 解码后的offset，用于计算解码下一帧数据的开始位置在源数据缓冲区的偏移量。
	 */
	public abstract int decodeFrame(byte[] b, int off);

	/**
	 * 完成一帧多相合成滤波后将输出的PCM数据写入音频输出
	 *
	 * @see AudioBuffer#flush()
	 */
	public void flush() {
		buf.flush();
	}

	/**
	 * 音频输出缓冲区的全部内容刷向音频输出并将缓冲区偏移量复位。
	 *
	 * @see AudioBuffer#close()
	 */
	public void close() {
		buf.close();
	}
}
