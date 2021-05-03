package roj.kscript.util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * 来自Java代码的异常
 *
 * @author solo6975
 * @since 2021/5/3 14:20
 */
public final class JavaException extends RuntimeException {
    public JavaException(String msg) {
        super(msg);
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
