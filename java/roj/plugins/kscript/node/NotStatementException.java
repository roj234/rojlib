package roj.plugins.kscript.node;

/**
 * @author Roj234
 * @since  2021/4/29 22:10
 */
@Deprecated
public class NotStatementException extends RuntimeException {
    public NotStatementException() {
        super("This operator does not support no-return environment");
    }
}
