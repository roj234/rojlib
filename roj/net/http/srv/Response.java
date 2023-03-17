package roj.net.http.srv;

import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;

import java.io.IOException;

public interface Response {
	String CRLF = "\r\n";

	void prepare(ResponseHeader srv, Headers h) throws IOException;

	/**
	 * @return true if not all data were written.
	 */
	boolean send(ResponseWriter rh) throws IOException;

	default void release(ChannelCtx ctx) throws IOException {}
}
