package roj.net.http.srv;

import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2023/2/3 0003 15:38
 */
public interface FileInfo {
	int CH_DEFL = 1, CH_WANT_DEFL = 2, CH_RA_RAW = 4, CH_RA_DEFL = 8;
	int stats();

	long length(boolean deflated);
	InputStream get(boolean deflated, long offset) throws IOException;

	long lastModified();
	default String getETag() {
		return null;
	}
	default void prepare(ResponseHeader srv, Headers h) {}
	default void release(ChannelCtx ctx) {}
}
