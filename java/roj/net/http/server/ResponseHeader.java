package roj.net.http.server;

import roj.net.ch.MyChannel;
import roj.net.http.Headers;

public interface ResponseHeader {
	MyChannel ch();

	/**
	 * Gets speed limit in KB per second
	 */
	int getStreamLimit();
	void setStreamLimit(int kbps);

	void onFinish(HFinishHandler o);

	ResponseHeader code(int code);
	ResponseHeader die();
	// 停在Process阶段，直到异步返回Response
	ResponseHeader asyncResponse();

	ResponseHeader enableCompression();
	// 除非调用了enableCompression，否则不会主动压缩
	ResponseHeader disableCompression();

	ResponseHeader body(Response resp);

	ResponseHeader date();
	ResponseHeader header(String k, String v);
	ResponseHeader headers(String hdr);
	Headers headers();

	default <T> T returnNull() {return null;}
	default <T> T returns(T t) {return t;}
}