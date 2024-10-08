package roj.plugins.dpiProxy;

/**
 * @author Roj234
 * @since 2024/10/3 0003 22:05
 */
public class DpiException extends Exception {
	public int errno;

	public DpiException(int errno, String target) {
		super(target, null, false, false);
		this.errno = errno;
	}
}
