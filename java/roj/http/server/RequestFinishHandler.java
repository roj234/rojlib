package roj.http.server;

/**
 * @author Roj233
 * @since 2022/3/17 3:08
 */
public interface RequestFinishHandler {
	boolean onRequestFinish(ResponseHeader rh, boolean success);
	default boolean onResponseTimeout(ResponseHeader rh) throws IllegalRequestException {return false;}
}