package roj.plugins.dpiProxy;

import roj.concurrent.OperationDone;
import roj.http.h2.H2Exception;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/9/15 0015 17:04
 */
public interface DpiMatcher {
	default void fail() {throw OperationDone.INSTANCE;}
	default void proxy(String addr, int port) throws DpiException {throw new DpiException(port, addr);}
	default void pipe(String id) throws DpiException {throw new DpiException(H2Exception.ERROR_OK, id);}
	default void close(String message) throws DpiException {throw new DpiException(-1, message);}
	default void close(DynByteBuf message) throws DpiException {throw new DpiException(-1, message);}
	default void limitSpeed(int byteps, Object group) {throw new UnsupportedOperationException("not implemented");}

	void inspect(DynByteBuf data) throws OperationDone, DpiException;
}
