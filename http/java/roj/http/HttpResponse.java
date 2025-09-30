package roj.http;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import roj.net.ChannelHandler;
import roj.util.ArtifactVersion;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2025/10/06 00:05
 */
public interface HttpResponse {
	// owner
	HttpRequest request();
	default URI uri() {return request().uri();}

	// 请求头
	int statusCode() throws IOException;
	ArtifactVersion version() throws IOException;
	@UnmodifiableView
	Headers headers() throws IOException;

	// 状态管理
	/**
	 * 请求是否成功
	 */
	boolean isSuccess();
	/**
	 * 请求是否完成，无论成功或失败
	 */
	boolean isDone();

	void awaitCompletion() throws InterruptedException;
	void onCompletion(Consumer<HttpResponse> o) throws IOException;

	void disconnect() throws IOException;

	// 请求体
	@Nullable
	Throwable exception();

	ByteList bytes() throws IOException;

	default String utf() throws IOException { return text(StandardCharsets.UTF_8); }
	/**
	 * 基于Content-Type或{@link roj.text.CharsetDetector}自动检测编码，并返回结果
	 */
	default String text() throws IOException {
		String charset = headers().getHeaderValue("content-type", "charset");
		return text(charset == null ? null : Charset.forName(charset));
	}
	String text(Charset charset) throws IOException;

	void pipe(ChannelHandler h) throws IOException;

	InputStream stream() throws IOException;
}
