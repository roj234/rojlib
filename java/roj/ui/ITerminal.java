package roj.ui;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/21 0021 7:37
 */
public interface ITerminal {
	default boolean unicode() {return false;}
	boolean read(boolean sync) throws IOException;
	void write(CharSequence str);
	default void flush() {}
}