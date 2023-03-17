package roj.net.mss;

import java.io.IOException;

/**
 * @author Roj233
 * @since 2021/12/22 13:46
 */
public class MSSException extends IOException {
	public MSSException(String msg) {
		super(msg);
	}

	public MSSException(String msg, Throwable cause) {
		super(msg, cause);
	}
}
