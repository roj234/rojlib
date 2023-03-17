package roj.net.http.h2;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2022/10/7 0007 23:16
 */
public class H2Error extends IOException {
	public final int errCode;

	public H2Error(int code, String desc) {
		super(desc);
		errCode = code;
	}

	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}
