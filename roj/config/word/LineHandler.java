package roj.config.word;

/**
 * @author Roj234
 * @since 2020/10/17 1:18
 */
@FunctionalInterface
public interface LineHandler {
	void handleLineNumber(int line);
}
