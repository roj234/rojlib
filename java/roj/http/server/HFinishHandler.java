package roj.http.server;

/**
 * @author Roj233
 * @since 2022/3/17 3:08
 */
public interface HFinishHandler {
	boolean onRequestFinish(HttpServer11 rh);
}