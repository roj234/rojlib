package roj.collect;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.PrimitiveIterator;

/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有, 仿冒不究,如有雷同,纯属活该
 * <p>
 * @author Roj234
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

    default IBitSet addAll(CharSequence s) {
        for (int i = 0; i < s.length(); i++)
            add(s.charAt(i));
        return this;
    }

    default IBitSet addAll(int... array) {
        for (int i : array)
            add(i);
        return this;
    }

    IBitSet addAll(IBitSet ibs);
}
