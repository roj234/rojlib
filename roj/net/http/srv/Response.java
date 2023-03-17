package roj.net.http.srv;

import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;

import java.io.IOException;

public interface Response {
	Response EMPTY = new Response() {
		public void prepare(ResponseHeader srv, Headers h) { h.put("content-length", "0"); }
		public boolean send(ResponseWriter rh) { return false; }
		public void release(ChannelCtx ctx) {}
	};
	String CRLF = "\r\n";

	void prepare(ResponseHeader srv, Headers h) throws IOException;

	/**
	 * @return true if not all data were written.
	 */
	boolean send(ResponseWriter rh) throws IOException;

	default void release(ChannelCtx ctx) throws IOException {}
}
