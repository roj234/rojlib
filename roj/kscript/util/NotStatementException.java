package roj.kscript.util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * Indicate this operator is not support no-return environment
 *
 * @author Roj233
 * @since 2021/4/29 22:10
 */
public class NotStatementException extends RuntimeException {
    public NotStatementException() {
        super("This operator does not support no-return environment");
    }
}
