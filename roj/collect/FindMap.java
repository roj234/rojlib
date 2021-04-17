package roj.collect;

import java.util.Map;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: FindMap.java.java
 */
public interface FindMap<K, V> extends Map<K, V> {
    Map.Entry<K, V> find(K k);
}
