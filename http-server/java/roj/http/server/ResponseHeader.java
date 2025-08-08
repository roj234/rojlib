package roj.http.server;

import roj.http.Headers;
import roj.net.MyChannel;
import roj.net.util.SpeedLimiter;

import java.io.IOException;

public interface ResponseHeader {
	MyChannel connection();
	String _getState();
	Request request();

	void limitSpeed(SpeedLimiter limiter);

	void onFinish(RequestFinishHandler o);

	ResponseHeader code(int code);
	ResponseHeader die();
	/**
	 * 不立即发送请求头, 等待异步调用body()再响应.
	 */
	ResponseHeader enableAsyncResponse(int extraTimeMs);
	/**
	 * 异步调用返回响应.
	 * 未调用enableAsyncResponse: 和return resp效果相同
	 * 否则在获取锁后立即发送响应
	 *
	 * 或者在header阶段全双工启动，一边接受post数据一边发送响应
	 */
	void body(Content resp) throws IOException;

	ResponseHeader enableCompression();
	/**
	 * 禁用压缩.
	 * 不会主动压缩，仅仅是取消之前enable调用的效果。
	 */
	ResponseHeader disableCompression();

	default ResponseHeader date() {return header("date", HSConfig.getInstance().toRFC(System.currentTimeMillis()));}
	default ResponseHeader header(String k, String v) {headers().put(k, v);return this;}
	default ResponseHeader header(String h) {headers().putAllS(h);return this;}
	Headers headers();

	// 没有作用也不应该重载，只为了链式表达
	default Content noContent() {return Content.EMPTY;}
	default <T> T cast(T t) {return t;}
}