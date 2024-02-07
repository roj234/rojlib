package roj.sound;

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
	void start(AudioFormat format, int buffer) throws LineUnavailableException;
	void stop();
	/**
	 * 阻塞直到缓冲区的音频全部播放完毕
	 */
	void flush();
	void write(byte[] b, int size);
}