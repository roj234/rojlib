package roj.net.rpc;

/**
 * @author Roj234
 * @since 2025/10/16 16:48
 */
public class RemoteException extends RuntimeException {
	public RemoteException() {}
	public RemoteException(String message) {super(message);}
	public RemoteException(String message, Throwable cause) {super(message, cause);}
	public RemoteException(Throwable cause) {super(cause);}
}

