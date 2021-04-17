package roj.collect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.PrimitiveIterator;

/**
 * This file is a part of more items mod (MI)
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * Author: Asyncorized_MC
 * Filename: IIntSet.java
 */
public interface IBitSet extends Iterable<Integer> {
    boolean contains(int key);

    boolean remove(int key);

    boolean add(int key);

    void clear();

    @Nullable
    Integer first();

    int size();

    /**
     * 全1
     */
    void fillAll();

    void fillAll(int len);

    @Nonnull
    PrimitiveIterator.OfInt iterator();

    IBitSet copy();

    static boolean isBitTrue(long i, int bit) {
        return (i & (1L << bit)) != 0;
    }
}
