package roj.plugins.frp;

import roj.concurrent.Shutdownable;
import roj.net.mss.MSSContext;

/**
 * @author Roj233
 * @since 2021/12/26 2:55
 */
@Deprecated
abstract class IAEClient extends Constants implements Shutdownable {
	static final MSSContext client_factory = new MSSContext();
}