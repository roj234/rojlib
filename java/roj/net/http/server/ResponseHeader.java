package roj.net.http.server;

import roj.net.ch.MyChannel;
import roj.net.http.Headers;

import java.io.IOException;

public interface ResponseHeader {
	MyChannel ch();
	String _getState();
	Request request();

	/**
	 * Gets speed limit in KB per second
	 */
	int getStreamLimit();
	void setStreamLimit(int kbps);

	void onFinish(HFinishHandler o);

	ResponseHeader code(int code);
	ResponseHeader die();
	/**
	 * 不立即发送请求头, 等待异步调用body()再响应.
	 */
	ResponseHeader enableAsyncResponse();
	/**
	 * Request对象不会在连接关闭后被共享
	 */
	void unsharedRequest();
	/**
	 * (默认)该请求对应的Request对象会在连接关闭后共享给其它请求使用
	 */
	void sharedRequest();
	/**
	 * 异步调用返回响应.
	 * 未调用enableAsyncResponse: 和return resp效果相同
	 * 否则在获取锁后立即发送响应
	 */
	void body(Response resp) throws IOException;

	ResponseHeader enableCompression();
	/**
	 * 禁用压缩.
	 * 只有调用过enableCompression才会主动压缩
	 */
	ResponseHeader disableCompression();

	default ResponseHeader date() {return header("date", HttpCache.getInstance().toRFC(System.currentTimeMillis()));}
	default ResponseHeader header(String k, String v) {headers().put(k, v);return this;}
	default ResponseHeader header(String h) {headers().putAllS(h);return this;}
	Headers headers();

	default <T> T returnNull() {return null;}
	default <T> T returns(T t) {return t;}
}