package roj.ui.terminal;

/**
 * @author Roj234
 * @since 2023/11/21 0021 17:34
 */
public interface CommandContext {
	<T> T argument(String name, Class<T> type);
	void writeToSystemIn(byte[] b, int off, int len);
}
