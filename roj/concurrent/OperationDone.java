package roj.concurrent;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/23 0:54
 */
public final class OperationDone extends RuntimeException {
    static final long serialVersionUID = 2333L;

    public static final OperationDone NEVER = new OperationDone("It never throws, and once you caught it, the stack trace is probably useless.");
    public static final OperationDone INSTANCE = new OperationDone("Operation done.");

    private OperationDone(String s) {
        super(s);
    }
}
