package roj.net.http.server;

import roj.net.MyChannel;
import roj.net.util.SpeedLimiter;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2023/2/6 0006 2:15
 */
public interface ResponseWriter {
	MyChannel ch();

	/**
	 * Gets speed limit in Byte per second
	 */
	int getStreamLimit();
	void setStreamLimit(int bps);
	void setStreamLimit(SpeedLimiter limiter);

	long getSendBytes();

	void write(DynByteBuf buf) throws IOException;
	default int write(InputStream in) throws IOException {return write(in, 0);}
	int write(InputStream in, int limit) throws IOException;
}