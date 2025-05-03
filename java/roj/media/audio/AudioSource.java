package roj.media.audio;

import java.io.IOException;

/**
 * @author Roj234-N
 * @since 2025/5/11 19:14
 */
public interface AudioSource {
	/**
	 * 连接音频输出设备并开始同步传输数据。
	 * 由于{@link AudioSink#write}可能无法不阻塞执行，所以有此方法
	 * <p>
	 * 执行流程：
	 * <ol>
	 *   <li>检查sink是否已初始化（未初始化时可能抛出异常）</li>
	 *   <li>在当前线程持续从数据源读取数据并调用 {@link AudioSink#write}</li>
	 *   <li>完成后自动断开连接</li>
	 * </ol>
	 *
	 * @param sink 目标输出设备，不可为null
	 * @throws IOException 数据传输过程中发生I/O错误
	 * @throws IllegalStateException 如果已有另一个connect正在执行（不可重入）
	 */
	void connect(AudioSink sink) throws IOException;

	/**
	 * 断开与当前AudioSink的连接。
	 * <p>
	 * 可能导致正在执行的 {@link #connect} 抛出异常
	 */
	void disconnect();
}
