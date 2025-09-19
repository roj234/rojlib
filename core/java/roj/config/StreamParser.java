package roj.config;

import roj.text.ParseException;
import roj.text.Token;

/**
 * @author Roj234
 * @since 2023/3/19 11:01
 */
public interface StreamParser {
	void element(Token w, int flags, ValueEmitter out) throws ParseException;
}