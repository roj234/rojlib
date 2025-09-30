package roj.http.server;

/**
 * 配置和处理请求体。
 * @author Roj234
 * @since 2023/2/6 2:15
 */
public interface PayloadInfo {
	/**
	 * 预期大小（-1未知）字节。
	 */
	long expectedLength();
	boolean isAccepted();
	/**
	 * 允许接受请求体，设置最大长度和额外接收时间。
	 *
	 * @param maxLen    最大长度（字节）
	 * @param extraTime 额外接收时间（毫秒）
	 */
	void accept(long maxLen, int extraTime);
	/**
	 * 设置请求体解析器。
	 * 若不设置，将会保存到缓冲区中，大小小于64KB会尝试池化，可以在稍后通过 {@link Request#body()} 读取.
	 * 若不设置，请求体大小不能超过{@link HttpServer#POST_BUFFER_MAX}限制，以免占用过多内存.
	 * 请求体为短JSON可以不设置。
	 * 若是文件，FormData建议设置为{@link MultipartParser}，传统表单可以{@link UrlEncodedParser}
	 */
	void setParser(BodyParser bodyParser);
}