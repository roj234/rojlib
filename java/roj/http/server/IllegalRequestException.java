package roj.http.server;

import org.jetbrains.annotations.Range;
import roj.http.HttpUtil;

import java.io.IOException;

/**
 * 中断请求处理并返回状态码和响应
 */
public class IllegalRequestException extends IOException {
	public final int code;
	public Content response;

	/**
	 * 中断请求处理并返回'标准'错误响应
	 */
	public IllegalRequestException(@Range(from = 300, to = 599) int code) {this.code = code;}
	public IllegalRequestException(int code, String text) {
		super(text);
		this.code = code;
		response = text == null ? null : Content.text(text);
	}
	public IllegalRequestException(int code, Content resp) {
		this.code = code;
		response = resp;
	}

	@Override public Throwable fillInStackTrace() {return this;}

	public Content createResponse() {return response != null || code < 400 ? response : Content.httpError(code);}

	public static final IllegalRequestException
		BAD_REQUEST = new IllegalRequestException(HttpUtil.BAD_REQUEST),
		NOT_FOUND = new IllegalRequestException(HttpUtil.NOT_FOUND);
	/**
	 * 内部使用，保留可选的调试信息，如果以后要调试
	 */
	public static IllegalRequestException badRequest(String debugInformation) {
		return BAD_REQUEST
		/*new IllegalRequestException(HttpUtil.BAD_REQUEST, debugInformation)*/
		;
	}
}