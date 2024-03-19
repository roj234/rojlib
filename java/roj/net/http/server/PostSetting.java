package roj.net.http.server;

/**
 * @author Roj234
 * @since 2023/2/6 0006 2:15
 */
public interface PostSetting {
	long postExceptLength();
	/**
	 * @param extraTime 再给多少ms用来接收请求
	 */
	void postAccept(long maxLen, int extraTime);
	void postHandler(HPostHandler ph);
	boolean postAccepted();
}