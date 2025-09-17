package roj.config;

import roj.text.ParseException;

/**
 * @author Roj234
 * @since 2023/3/19 11:01
 */
public interface StreamParser {
	void streamElement(int flags, ValueEmitter out) throws ParseException;
}