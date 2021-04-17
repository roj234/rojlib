package roj.collect;

import java.util.Iterator;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/14 17:16
 */
public class EntryItr<E extends EntryIterable<E>> extends MapItr<E> implements Iterator<E> {
    public EntryItr(EntryIterable<?>[] entries, CItrMap<E> map) {
        super(entries, map);
    }

    @Override
    public E next() {
        return nextT();
    }
}