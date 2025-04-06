package roj.http.server;

import roj.net.MyChannel;
import roj.net.util.SpeedLimiter;
import roj.util.DynByteBuf;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author Roj234
 * @since 2023/2/6 0006 2:15
 */
public interface ContentWriter {
	MyChannel connection();

	/**
	 * Gets speed limit in Byte per second
	 */
	int getSpeedLimit();
	SpeedLimiter getSpeedLimiter();
	void limitSpeed(int bps);
	void limitSpeed(SpeedLimiter limiter);

	long getSendBytes();

	void write(DynByteBuf buf) throws IOException;
	default int write(InputStream in) throws IOException {return write(in, 0);}
	int write(InputStream in, int limit) throws IOException;
}