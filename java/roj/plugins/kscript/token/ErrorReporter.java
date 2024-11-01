package roj.plugins.kscript.token;

import roj.config.ParseException;

/**
 * @author Roj234
 * @since  2021/1/10 1:28
 */
public interface ErrorReporter {
    void handle(String type, String file, ParseException e);
}
