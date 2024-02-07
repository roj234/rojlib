package roj.sound;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.io.source.Source;

import javax.sound.sampled.LineUnavailableException;
import java.io.Closeable;
import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/2/18 0018 23:37
 */
public interface AudioDecoder extends Closeable {
	@Nullable
	AudioMetadata open(Source in, AudioOutput out, boolean parseMetadata) throws IOException, LineUnavailableException;
	/**
	 * 是否打开了文件
	 * @return 是否打开了文件
	 */
	boolean isOpen();
	/**
	 * 停止解码，文件将会关闭，解码器仍可使用。不会关闭in
	 */
	void stop();
	/**
	 * 释放解码器占用的资源，解码器可能无法继续解码。不会关闭in
	 */
	default void close() { stop(); }

	/**
	 * 是否可以解码
	 * @return true: 打开了文件，并且文件不是空的，并且未解码完毕，并且没有调用stop()或close()终止解码
	 */
	boolean isDecoding();
	/**
	 * 在当前或其它线程调用该方法以开始解码
	 * 由于{@link AudioOutput#write}可能是阻塞的，所以有此方法
	 */
	void decodeLoop() throws IOException;

	boolean isSeekable();
	void seek(double second) throws IOException;
	double getCurrentTime();
	double getDuration();

	@NotNull
	default String getDebugInfo() { return ""; }
}