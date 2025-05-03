package roj.media.audio;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.io.source.Source;

import java.io.Closeable;
import java.io.IOException;

/**
 * 音频解码器，继承自AudioSource，支持元数据解析和播放控制。
 * <p>
 * **状态机**（{@link #getState()}）：
 * <pre>
 *   [READY] → open() → [OPENED] → connect() → [DECODING] → (End of Stream) → [FINISHED]
 *      │                      │                   │                              │
 *      └─ close() → [UNKNOWN] └─ disconnect() ────┴───────── disconnect() ───────┘
 * </pre>
 * @author Roj234
 * @since 2024/2/18 23:37
 */
public interface AudioDecoder extends AudioSource, Closeable {
	/**
	 * 打开音频流并解析元数据。
	 *
	 * @param in 输入数据源，不可为null
	 * @param parseMetadata 是否解析元数据（如ID3标签）
	 * @return 元数据对象，若parseMetadata=false或格式不支持时返回null
	 * @throws IOException 读取输入流失败或格式解析错误
	 * @throws IllegalStateException 当前状态不是READY
	 */
	@Nullable
	AudioMetadata open(@NotNull Source in, boolean parseMetadata) throws IOException;

	/**
	 * 释放解码器占用的所有资源（包括断开连接），解码器不可复用。
	 */
	@Override
	default void close() { disconnect(); }

	int READY = 0, OPENED = 1, DECODING = 2, FINISHED = 3, UNKNOWN = 4;
	/**
	 * 获取当前解码器状态。
	 *
	 * @return 枚举值：
	 *   READY（未打开）、OPENED（已打开）、DECODING（解码中）、
	 *   FINISHED（解码完成）、UNKNOWN（已关闭或错误）
	 */
	int getState();

	/**
	 * {@inheritDoc}
	 */
	@Override
	void connect(@NotNull AudioSink sink) throws IOException;

	/**
	 * 停止解码并关闭当前文件，但保持解码器处于可复用状态（可再次调用open）。
	 */
	@Override
	void disconnect();

	/**
	 * 是否支持随机访问（如WAV文件通常支持，MP3 VBR流可能不支持）。
	 *
	 * @return 如果返回true，则允许调用 {@link #seek}
	 */
	default boolean isSeekable() { return false; }

	/**
	 * 跳转到指定时间点（单位：秒）。
	 *
	 * @param timeSec 目标时间点（0 ≤ timeSec ≤ {@link #getDuration()}）
	 * @throws UnsupportedOperationException 如果 {@link #isSeekable} 返回false
	 * @throws IOException 跳转操作失败（如文件损坏）
	 * @throws IllegalStateException 当前状态不是OPENED或DECODING
	 */
	default void seek(double timeSec) throws IOException {
		throw new UnsupportedOperationException(getClass().getName());
	}

	/**
	 * 获取当前播放位置。
	 *
	 * @return 当前解码到的时间点（秒），精度由实现决定
	 * @throws IllegalStateException 如果不处于OPENED、DECODING或FINISHED状态
	 */
	double getCurrentTime();

	/**
	 * 获取音频总时长。
	 *
	 * @return 音频长度（秒），未知返回-1
	 * @throws IllegalStateException 如果不处于OPENED、DECODING或FINISHED状态
	 */
	double getDuration();

	/**
	 * 获取调试信息
	 */
	@NotNull
	default String getDebugInfo() { return ""; }
}