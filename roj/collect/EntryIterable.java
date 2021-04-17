package roj.collect;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/14 17:08
 */
interface EntryIterable<T extends EntryIterable<T>> {
    T nextEntry();
}
