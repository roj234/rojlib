package roj.collect;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/12 13:04
 */
public abstract class AbstractIterator<T> implements Iterator<T> {
    public T result;
    private int stage = INITIAL;

    public static final int INITIAL = 0;
    public static final int GOTTEN = 1;
    public static final int CHECKED = 2;
    public static final int ENDED = 3;

    /**
     * Returns {@code true} if the iteration has more elements.
     * (In other words, returns {@code true} if {@link #next} would
     * return an element rather than throwing an exception.)
     *
     * @return {@code true} if the iteration has more elements
     */
    @Override
    public final boolean hasNext() {
        check();
        return stage != ENDED;
    }

    /**
     * @return true if currentObject is updated, false if not elements
     */
    public abstract boolean computeNext();

    /**
     * Returns the next element in the iteration.
     *
     * @return the next element in the iteration
     * @throws NoSuchElementException if the iteration has no more elements
     */
    @Override
    public final T next() {
        check();
        if (stage == ENDED) {
            throw new NoSuchElementException();
        }
        stage = GOTTEN;
        return result;
    }

    private void check() {
        if (stage <= 1) {
            if (!computeNext()) {
                stage = ENDED;
            } else {
                stage = CHECKED;
            }
        }
    }

    /**
     * Removes from the underlying collection the last element returned
     * by this iterator (optional operation).  This method can be called
     * only once per call to {@link #next}.  The behavior of an iterator
     * is unspecified if the underlying collection is modified while the
     * iteration is in progress in any way other than by calling this
     * method.
     *
     * @throws UnsupportedOperationException if the {@code remove}
     *                                       operation is not supported by this iterator
     * @throws IllegalStateException         if the {@code next} method has not
     *                                       yet been called, or the {@code remove} method has already
     *                                       been called after the last call to the {@code next}
     *                                       method
     * @implSpec The default implementation throws an instance of
     * {@link UnsupportedOperationException} and performs no other action.
     */
    @Override
    public final void remove() {
        if (stage != GOTTEN)
            throw new IllegalStateException();
        remove(result);
        stage = INITIAL;
    }

    public void remove(T currentObject) {
        throw new UnsupportedOperationException();
    }
}
