package roj.net.http.srv;

import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;

public interface ResponseHeader {
	ChannelCtx ch();

	void finishHandler(HFinishHandler o);

	ResponseHeader code(int code);
	ResponseHeader die();

	ResponseHeader chunked();
	ResponseHeader compressed();
	ResponseHeader uncompressed();

	ResponseHeader body(Response resp);

	ResponseHeader date();
	ResponseHeader header(String k, String v);
	ResponseHeader headers(String hdr);
	Headers headers();

	default <T> T returnNull() {
		return null;
	}
	default <T> T returns(T t) {
		return t;
	}
}
