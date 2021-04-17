package roj.collect;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/23 0:46
 */
public interface Filter<T> {
    boolean isBetterThan(T old, T latest);
}
