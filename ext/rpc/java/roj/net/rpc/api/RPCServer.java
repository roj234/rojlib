package roj.net.rpc.api;

/**
 * @author Roj234
 * @since 2025/10/13 1:09
 */
public interface RPCServer {
	<T extends RemoteProcedure> void registerImplementation(Class<T> type, T impl);
}
