package roj.net.http.auth;

/**
 * @author Roj234
 * @since 2023/5/16 0016 15:44
 */
public interface AuthScheme {
	String type();

	String check(String header);
	String send();
}
