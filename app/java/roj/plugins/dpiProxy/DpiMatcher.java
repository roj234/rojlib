package roj.plugins.dpiProxy;

import roj.util.OperationDone;
import roj.util.DynByteBuf;

/**
 * @author Roj234
 * @since 2024/9/15 17:04
 */
public interface DpiMatcher {
	default void fail() {throw OperationDone.INSTANCE;}
	default void proxy(String addr, int port) throws DpiException {throw new DpiException(port, addr);}
	default void pipe(String id) throws DpiException {throw new DpiException(-1, id);}
	default void close(String message) throws DpiException {throw new DpiException(-2, message);}
	default void close(DynByteBuf message) throws DpiException {throw new DpiException(-2, message);}
	default void ban() throws DpiException {throw new DpiException(0, "");}

	void inspect(DynByteBuf data) throws OperationDone, DpiException;
}
