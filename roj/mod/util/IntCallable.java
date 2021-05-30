package roj.mod.util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/14 13:03
 */
@FunctionalInterface
public interface IntCallable {
    int call() throws Exception;

    default boolean stop() {
        return false;
    }
}
