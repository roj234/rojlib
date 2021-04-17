package roj.util;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/29 23:05
 */
@FunctionalInterface
public interface Operation<T extends Throwable, K> {
    void work(K k) throws T;
}
