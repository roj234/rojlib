package roj.net.http.srv;

import org.jetbrains.annotations.ApiStatus;
import roj.net.ch.ChannelCtx;
import roj.net.http.Headers;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2021/2/16 11:21
 */
public class StreamResponse implements Response {
	protected InputStream stream;

	public StreamResponse(InputStream in) {
		stream = in;
	}

	protected StreamResponse() {}

	@Override
	public void prepare(ResponseHeader srv, Headers h) throws IOException {
		if (getClass() == StreamResponse.class) return;
		if (stream != null) stream.close();
		stream = getStream();
	}

	@ApiStatus.OverrideOnly
	protected InputStream getStream() throws IOException {
		return stream;
	}

	public boolean send(ResponseWriter rh) throws IOException {
		if (stream == null) throw new IllegalStateException();
		return rh.write(stream) >= 0;
	}

	public void release(ChannelCtx ctx) throws IOException {
		if (stream != null) {
			stream.close();
			stream = null;
		}
	}
}
