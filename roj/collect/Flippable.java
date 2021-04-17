package roj.collect;

import java.util.Map;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/9/19 17:44
 */
public interface Flippable<K, V> extends Map<K, V> {
    Flippable<V, K> flip();
}
