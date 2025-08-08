package roj.ui;

import org.intellij.lang.annotations.MagicConstant;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/21 7:37
 */
public interface ITty {
	int ANSI = 1, INTERACTIVE = 2, UNICODE = 4, VIRTUAL = 8;
	@MagicConstant(flags = {ANSI, INTERACTIVE, UNICODE, VIRTUAL}) int characteristics();
	default void getConsoleSize(Tty tty) {
		int winSize = tty.getCursor(Tty.GET_CONSOLE_DIMENSION);
		tty.columns = winSize&0xFFFF;
		tty.rows = winSize >>> 16;
	}
	default boolean readSync() {return false;}
	void write(CharSequence str);
	default void flush() {}
}