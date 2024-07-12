package roj.net.http.server;

import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

/**
 * @author Roj234
 * @since 2023/2/3 0003 15:38
 */
public interface FileInfo {
	// FileResponse#def referred this
	int FILE_RA = 1, FILE_DEFLATED = 2, FILE_CAN_DEFLATE = 4;
	int stats();

	long length(boolean deflated);
	default FileChannel getSendFile(boolean deflated) throws IOException {return null;}
	InputStream get(boolean deflated, long offset) throws IOException;

	long lastModified();
	default String getETag() {return null;}
	default void prepare(ResponseHeader rh, Headers h) {}
	default void release(ChannelCtx ctx) {}
}