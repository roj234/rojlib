package roj.media.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.LineUnavailableException;

/**
 * @author Roj234
 * @since 2024/2/18 0018 23:29
 */
public interface AudioOutput {
	/**
	 * @param buffer 缓冲区容量提示，并非一定要用
	 */
	void init(AudioFormat format, int buffer) throws LineUnavailableException;
	/**
	 * @param flush 如果为真，那么write阻塞直到缓冲区的音频全部写入
	 * @return 写入的字节，如果flush为真，那么总是len
	 */
	int write(byte[] b, int off, int len, boolean flush);
	/**
	 * 阻塞直到缓冲区的音频全部播放完毕
	 * 解码器退出解码后，调用者应当调用此方法再关闭音频输出
	 */
	default void flush() {}
	void close();
}