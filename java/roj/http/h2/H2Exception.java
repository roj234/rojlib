package roj.http.h2;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/10/7 23:16
 */
public final class H2Exception extends IOException {
	public static final int
		ERROR_OK = 0,
		ERROR_PROTOCOL = 1,
		ERROR_INTERNAL = 2,
		ERROR_FLOW_CONTROL = 3,
		ERROR_INITIAL_TIMEOUT = 4,
		ERROR_STREAM_CLOSED = 5,
		ERROR_FRAME_SIZE = 6,
		ERROR_REFUSED = 7,
		ERROR_CANCEL = 8,
		ERROR_COMPRESS = 9,
		ERROR_CONNECT = 10,
		ERROR_CALM_DOWN = 11,
		ERROR_INSECURITY = 12,
		ERROR_HTTP1_1_REQUIRED = 13;

	public final int errno;

	public H2Exception(int code, String desc) {super(desc);errno = code;}

	@Override
	public Throwable fillInStackTrace() {return this;}
}