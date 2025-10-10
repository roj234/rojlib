package roj.net.rpc.api;

import roj.net.rpc.RemoteException;

import java.io.Closeable;

/**
 * @author Roj234
 * @since 2025/10/13 1:09
 */
public interface RPCClient extends Closeable {
	<T extends RemoteProcedure> T getImplementation(Class<T> type) throws RemoteException;
}
