package roj.http.server;

/**
 * @author Roj233
 * @since 2022/3/17 3:08
 */
@FunctionalInterface
public interface ResponseFinishHandler {
	boolean onResponseFinish(Response response, boolean success);
	default boolean onResponseTimeout(Response response) throws IllegalRequestException {return false;}
}