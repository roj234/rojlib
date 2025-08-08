package roj.plugins.dpiProxy;

import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/10/3 22:05
 */
public class DpiException extends Exception {
	public int errno;
	public DynByteBuf byteMessage;

	public DpiException(int errno, String data) {
		super(data, null, false, false);
		this.errno = errno;
	}

	public DpiException(int errno, DynByteBuf data) {
		super("<byte message>", null, false, false);
		this.errno = errno;
		this.byteMessage = data;
	}
}
