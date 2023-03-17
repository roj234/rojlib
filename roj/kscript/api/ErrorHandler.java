package roj.kscript.api;

import roj.config.ParseException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/1/10 1:28
 */
public interface ErrorHandler {
	void handle(String type, String file, ParseException e);
}
