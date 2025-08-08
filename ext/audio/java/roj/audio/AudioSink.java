package roj.audio;

import org.jetbrains.annotations.NotNull;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;

/**
 * 音频输出设备，负责播放原始音频数据。
 * <p>使用方法
 * <ol>
 *   <li>调用 {@link #open} 初始化</li>
 *   <li>调用 {@link #write} 或 {@link #writeNow} 写入数据</li>
 *   <li>数据结束后调用 {@link #flush} 确保清空内部缓冲区</li>
 *   <li>调用 {@link #close} 释放资源，部分实现不支持重新打开</li>
 * </ol>
 * @author Roj234
 * @since 2024/2/18 23:29
 */
public interface AudioSink extends AutoCloseable {
	/**
	 * 初始化音频输出设备。
	 * @param format 音频格式，不可为null
	 * @throws IOException 设备初始化失败
	 * @throws IllegalStateException 当设备已开启时以不同参数再次开启
	 * @apiNote 当设备已打开时：<ul>
	 * 	   <li>若传入格式与当前配置相同，应保持当前状态</li>
	 * 	   <li>若格式不同，可选择抛出异常，或静默重新配置</li>
	 * 	 </ul>
	 */
	void open(@NotNull AudioFormat format) throws IOException;

	/**
	 * 阻塞写入音频数据。
	 */
	void write(byte[] buf, int off, int len);
	/**
	 * 非阻塞写入音频数据，该方法尽可能写入数据并返回实际写入的字节数.
	 * @apiNote 不支持的实现必须阻塞写入并始终返回 {@code len}
	 */
	default int writeNow(byte[] buf, int off, int len) {write(buf, off, len);return len;}

	/**
	 * 阻塞直到所有缓冲音频数据播放完毕。
	 * <p>
	 * 在解码后/关闭前调用，确保清空内部缓冲区。
	 *
	 * @throws IllegalStateException 未初始化或已关闭
	 */
	default void flush() {}

	/**
	 * 释放资源并关闭设备。
	 * 某些实现可能无法重新 {@link #open}。
	 */
	default void close() {}
}