package roj.net.http.srv;

import roj.net.ch.ChannelCtx;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2023/2/6 0006 2:15
 */
public interface ResponseWriter {
	ChannelCtx ch();

	int write(DynByteBuf buf) throws IOException;
	default int write(InputStream in) throws IOException {
		return write(in, 0);
	}
	int write(InputStream in, int limit) throws IOException;
}
