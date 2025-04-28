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

	void limitSpeed(SpeedLimiter limiter);
	SpeedLimiter getSpeedLimiter();

	long getBytesSent();

	void write(DynByteBuf buf) throws IOException;
	default int write(InputStream in) throws IOException {return write(in, 0);}
	int write(InputStream in, int limit) throws IOException;
}