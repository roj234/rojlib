package roj.net.http.srv;

/**
 * @author Roj234
 * @since 2023/2/6 0006 2:15
 */
public interface PostSetting {
	long postExceptLength();
	void postMaxLength(long len);
	void postMaxTime(int time);
}
