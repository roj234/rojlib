package roj.http;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import roj.concurrent.Promise;
import roj.net.ChannelHandler;
import roj.util.ArtifactVersion;
import roj.util.ByteList;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

/**
 * @author Roj234
 * @since 2025/10/06 00:05
 */
public interface HttpResponse {
	// owner
	HttpRequest request();
	default URI uri() {return request().uri();}

	// 以下几个调用可能阻塞（等待响应头）或抛出异常（在读取完响应头前出错）
	// 所有阻塞方法，若被Thread.interrupt打断，将自动关闭连接
	// 若抛出异常，isSuccess必定为false，且exception()必定不为null
	// 事实上，抛出异常的cause将是exception()

	int statusCode() throws IOException;
	// HTTP版本 可能是 1.0, 1.1 或 2
	ArtifactVersion version() throws IOException;
	@UnmodifiableView
	Headers headers() throws IOException;

	// 状态管理
	/**
	 * 响应是否成功
	 * 这是协议层的成功，定义为：收到了完整的HTTP响应
	 * 任何应用层的错误，例如状态码为500，不影响，它还是true
	 */
	boolean isSuccess();
	/**
	 * 响应是否完成，可能成功或失败
	 */
	boolean isDone();
	/**
	 * 阻塞直到响应完成
	 */
	void awaitCompletion() throws InterruptedException;

	/**
	 * 注册状态改变回调.
	 * 这个回调在网络线程上触发，请勿进行阻塞操作，否则可能影响同一线程上的其余连接
	 * 值得注意的是，它最多可能被调用三次
	 * 1. 注册时
	 * 2. 响应头获取时
	 * 3. 响应完成时
	 * @throws IllegalStateException 如果已经存在事件监听器（只能注册一个）
	 */
	void onReadyStateChange(Consumer<HttpResponse> handler);

	/**
	 * 如果请求失败了(isSuccess=false)，那么必然存在异常
	 * @return 导致请求失败的异常
	 */
	@Nullable
	Throwable exception();

	/**
	 * 断开与服务器的连接
	 * 当你决定不再接收请求体（例如，statusCode表示发生了错误）时可以调用它
	 */
	void disconnect() throws IOException;

	// 获取请求体
	// 请注意下列所有获取请求体的方法，只能调用其一，只能调用一次
	// @throws IllegalStateException 如果响应体已经以其它方式读取
	// 此外，如果不调用下列任一方法，应尽最大努力调用disconnect
	// 否则虽然不会缓存到内存爆炸，但会处于readInactive状态直到超时

	ByteList bytes() throws IOException;

	default Promise<ByteList> asyncBytes() {return asyncBytes(code -> code >= 200 && code < 300);}
	/**
	 * 这个predicate是必须的，因为要决定是否应该成功，并完整的接收响应体，还是抛出一个异常，并传递给Promise链
	 * 如果响应失败，它会生成一个rejected(exception())的Promise，而不是像其他方法一样抛出IOException
	 * @throws IllegalStateException 如果响应体已经以其它方式读取
	 */
	Promise<ByteList> asyncBytes(IntPredicate code);

	default String utf() throws IOException { return text(StandardCharsets.UTF_8); }
	/**
	 * 基于Content-Type或{@link roj.text.CharsetDetector}自动检测编码，并返回结果
	 */
	default String text() throws IOException {
		String charset = headers().getHeaderValue("content-type", "charset");
		return text(charset == null ? null : Charset.forName(charset));
	}
	String text(@Nullable Charset charset) throws IOException;

	/**
	 * 提供一个异步TCP连接处理器以接收响应
	 * @throws IllegalStateException 如果响应体已经以其它方式读取
	 */
	void pipe(ChannelHandler h) throws IOException;

	/**
	 * 获取一个管道流，它的性能可能不如asyncBytes，但有自动流控避免缓冲区过大
	 * 如果需要最高性能，请使用#pipe
	 * @throws IllegalStateException 如果响应体已经以其它方式读取
	 */
	InputStream stream() throws IOException;
}
