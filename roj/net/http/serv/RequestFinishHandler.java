package roj.net.http.serv;

/**
 * @author Roj233
 * @since 2022/3/17 3:08
 */
public interface RequestFinishHandler {
    boolean onRequestFinish(RequestHandler rh, boolean normalFinish);
}
