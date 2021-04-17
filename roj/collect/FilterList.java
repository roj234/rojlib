package roj.collect;


import java.util.AbstractList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/8/23 0:44
 */
public class FilterList<T> extends AbstractList<T> {
    public T found;
    final Filter<T> checker;

    public FilterList(Filter<T> checker) {
        this.checker = checker;
    }

    public void add(int index, T element) {
        if (checker.isBetterThan(found, element)) {
            found = element;
        }
    }

    /**
     * {@inheritDoc}
     *
     * @param index
     * @throws IndexOutOfBoundsException {@inheritDoc}
     */
    @Override
    public T get(int index) {
        if (index > size())
            throw new IndexOutOfBoundsException(String.valueOf(index));
        return found;
    }

    @Override
    public int size() {
        return found == null ? 0 : 1;
    }
}
