package roj.http.server;

import org.jetbrains.annotations.Nullable;

/**
 * 请求路由器：处理HTTP请求并生成响应。
 * @author Roj234
 * @since 2020/11/28 20:54
 */
@FunctionalInterface
public interface Router {
	/**
	 * 获取请求头读取超时时间（毫秒）。
	 * @param isNewConnection 是否为新连接（还未从客户端收到任何字节）
	 * @return 请求头读取超时（毫秒）
	 */
	default int readTimeout(boolean isNewConnection) {return isNewConnection ? 30000 : 1000;}
	/**
	 * 获取最大头部大小（字节）。
	 * 默认值假定为4KB的Cookie + 1KB的其他部分，总计5KB。
	 *
	 * @return 最大头部大小（字节）
	 */
	default int maxHeaderSize() {return 4096 + 1024;}

	/**
	 * 重载用：检查请求头并应用POST设置。
	 * 仅当有请求体时，PayloadInfo不为空。
	 * 如果有请求体时不调用accept，那么连接将被断开
	 *
	 * @param req 请求对象
	 * @param cfg 请求体信息和控制对象
	 * @throws IllegalRequestException 如果用户自定义的检查失败
	 */
	default void checkHeader(Request req, @Nullable PayloadInfo cfg) throws IllegalRequestException {
		if (cfg != null) cfg.accept(4096, 1000);
	}

	/**
	 * 处理请求并生成响应内容。
	 * 这是路由器的核心方法，必须由实现类提供。
	 *
	 * @param req 请求头信息
	 * @param resp  响应头接口
	 * @return 响应内容 可以为空
	 * @throws Exception 如果处理请求时发生异常
	 */
	@Nullable
	Content response(Request req, Response resp) throws Exception;

	/**
	 * 获取写入超时时间（毫秒）。
	 * 默认：如果响应为空则为10秒，否则为1小时。
	 * 如果使用异步响应，这不会生效。
	 *
	 * @param req  请求对象（可为空）
	 * @param resp 响应内容（可为空）
	 * @return 写入超时时间（毫秒）
	 */
	default int writeTimeout(@Nullable Request req, @Nullable Content resp) {
		return resp == null ? 10000 : 3600_000;
	}

	default int keepaliveTimeout() {return 300_000;}
}