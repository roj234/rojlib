package roj.mildwind.api;

import roj.config.ParseException;

/**
 * @author Roj234
 * @since 2021/1/10 1:28
 */
public interface ErrorHandler {
	void handle(String type, String file, ParseException e);
}
