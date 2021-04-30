package roj.collect;

import java.util.Map;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
 * Filename: FindMap.java.java
 */
public interface FindMap<K, V> extends Map<K, V> {
    Map.Entry<K, V> find(K k);
}
