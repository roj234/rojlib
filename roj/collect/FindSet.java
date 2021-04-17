package roj.collect;

import java.util.Set;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: FindSeta.java.java
 */
public interface FindSet<K> extends Set<K> {
    K find(K k);
}
