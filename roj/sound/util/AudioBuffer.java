package roj.sound.util;

/**
 * 音频输出缓冲区。
 */
public final class AudioBuffer {
	public byte[] pcm;
	final int[] off = new int[] {0, 2}; // 两个声道的缓冲区偏移量
	private final IAudio audio;

	/**
	 * 音频输出缓冲区构建器。
	 *
	 * @param audio 音频输出对象。如果指定为null则调用 {@link #flush()} 不产生输出，仅清空缓冲区。
	 * @param size 音频输出缓冲区长度，单位“字节”。
	 */
	public AudioBuffer(IAudio audio, int size) {
		this.audio = audio;
		this.pcm = new byte[size];
	}

	/**
	 * 音频输出缓冲区的内容刷向音频输出对象并将缓冲区偏移量复位。当缓冲区填满时才向音频输出对象写入，但调用者并不需要知道当前缓冲区是否已经填满。
	 * 防止缓冲区溢出， 每解码完一帧应调用此方法一次。
	 */
	public void flush() {
		if (audio != null) audio.write(pcm, off[0]);
		off[0] = 0;
		off[1] = 2;
	}

	/**
	 * 音频输出缓冲区的全部内容刷向音频输出对象并将缓冲区偏移量复位。在解码完一个文件的最后一帧后调用此方法，将缓冲区剩余内容写向音频输出对象。
	 */
	public void close() {
		if (off[0] > 0 && audio != null) {
			audio.write(pcm, off[0]);
			audio.drain();
		}
		off[0] = 0;
		off[1] = 2;
	}

	public final void offset(int ch, int val) {
		off[ch] = val;
	}

	public final int offsetOf(int ch) {
		return off[ch];
	}
}
