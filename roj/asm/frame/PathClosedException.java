package roj.asm.frame;

/**
 * @author Roj233
 * @since 2022/2/26 22:41
 */
public class PathClosedException extends RuntimeException {
    public static final PathClosedException NULL_ARRAY = new PathClosedException("null as an array");

    public PathClosedException(String msg) {
        super(msg, null, false, false);
    }
}
