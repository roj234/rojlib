package roj.plugins.obfuscator.naming;

/**
 * @author Roj234
 * @since 2025/6/3 6:17
 */
public final class InvalidUnicode extends ABC {
	public InvalidUnicode() {}

	protected String num2str(int i) {
		return new String(new char[] {0xD800, (char) (0xDC00 | i)});
	}
}