package roj.audio.mp3;

abstract class Layer {
	final Synthesis synthesis;
	final Header header;
	final BitStream data;

	Layer(Header h, MP3Decoder buf, BitStream data) {
		this.data = data;
		this.header = h;
		this.synthesis = new Synthesis(buf, h.channels());
	}

	/**
	 * 从此位置解码一帧
	 * @return new offset
	 */
	abstract int decodeFrame(byte[] b, int off);

	void close() {}
}