package roj.plugins.frp;

import roj.net.mss.MSSContext;

import java.io.Closeable;

/**
 * @author Roj233
 * @since 2021/12/26 2:55
 */
@Deprecated
abstract class IAEClient extends Constants implements Closeable {
	static final MSSContext client_factory = new MSSContext();
}