package roj.http.server;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import roj.http.Headers;
import roj.net.MyChannel;
import roj.net.util.SpeedLimiter;
import roj.text.DateFormat;

import java.io.IOException;

/**
 * 响应控制器，构建和发送HTTP响应。
 */
public interface Response {
	MyChannel connection();

	/**
	 * 获取响应状态（用于调试）。
	 */
	@ApiStatus.Internal String _getState();

	Request request();

	void limitSpeed(SpeedLimiter limiter);

	void onFinish(ResponseFinishHandler onFinish);

	Response code(int code);
	/**
	 * 不使用keepalive，在当前请求结束后关闭连接。
	 */
	Response die();

	/**
	 * 启用异步响应。不立即发送请求头，等待异步调用 {@link #body(Content)} 后再响应。
	 * 额外时间用于处理异步逻辑。
	 *
	 * @param extraTimeMs 额外时间（毫秒）
	 */
	Response async(int extraTimeMs);

	/**
	 * 发送响应体内容。
	 * 如果未调用 {@link #async(int)}，则立即发送并返回响应。
	 * 否则，在获取锁后立即发送响应。
	 *
	 * 计划支持全双工模式（一边接收POST数据一边发送响应）。
	 *
	 * @param content 响应内容
	 * @throws IOException 如果发送失败
	 */
	void body(Content content) throws IOException;

	/**
	 * 启用响应压缩（例如GZIP）。
	 */
	Response enableCompression();
	/**
	 * 禁用响应压缩。
	 * 不会主动压缩，仅取消之前 {@link #enableCompression()} 的效果。
	 */
	Response disableCompression();

	/**
	 * 添加Date头部，使用当前时间（RFC 5322格式）。
	 */
	default Response date() {return setHeader("date", DateFormat.toRFC5322Datetime(System.currentTimeMillis()));}
	default Response setHeader(String k, String v) {headers().put(k, v);return this;}
	default Response addHeader(String k, String v) {headers().add(k, v);return this;}
	/**
	 * 添加多个头部（从字符串解析）。
	 */
	default Response setHeader(String headerLines) {headers().putAllS(headerLines);return this;}

	/**
	 * 获取当前已经存在的响应头部。
	 */
	Headers headers();

	// Utility methods for fluent API
	/**
	 * 返回空内容（无响应体），用于链式表达（不应重载）。
	 */
	@Contract(pure = true)
	default Content noContent() {return Content.EMPTY;}

	/**
	 * 返回Content时使用，用于链式表达（不应重载）。
	 *
	 * @param <T> 类型参数
	 * @param t   对象
	 * @return 铸型后的对象
	 */
	@Contract(pure = true)
	default <T> T cast(T t) {return t;}
}