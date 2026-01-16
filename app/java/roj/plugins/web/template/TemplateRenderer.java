package roj.plugins.web.template;

import roj.http.server.AsyncContent;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

/**
 * @author Roj234
 * @since 2024/7/1 16:26
 */
public class TemplateRenderer extends AsyncContent implements WritableByteChannel {
	@Override
	public int write(ByteBuffer src) throws IOException {
		offer(DynByteBuf.nioRead(src));

		int remaining = src.remaining();
		src.position(src.position() + remaining);
		return remaining;
	}

	@Override
	public boolean isOpen() {return !isEof();}

	@Override
	public void close() throws IOException {setEof();}
}