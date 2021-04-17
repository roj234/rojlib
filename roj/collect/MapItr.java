package roj.collect;

import roj.util.Helpers;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/14 17:06
 */
class MapItr<T extends EntryIterable<T>> {
    public T obj;
    int stage = INITIAL;

    public static final int INITIAL = 0;
    public static final int GOTTEN = 1;
    public static final int CHECKED = 2;
    public static final int ENDED = 3;

    public final boolean hasNext() {
        check();
        return stage != ENDED;
    }

    public final T nextT() {
        check();
        if (stage == ENDED) {
            throw new NoSuchElementException();
        }
        stage = GOTTEN;
        return obj;
    }

    private void check() {
        if (stage <= GOTTEN) {
            if (!computeNext()) {
                stage = ENDED;
            } else {
                stage = CHECKED;
            }
        }
    }

    public final void remove() {
        if (stage != GOTTEN)
            throw new IllegalStateException();
        if (customItr != null) {
            customItr.remove();
        } else {
            remover.removeEntry0(obj);
        }
        stage = INITIAL;
    }

    private final T[] entries;
    private final CItrMap<T> remover;
    private final Iterator<T> customItr;
    private T entry;
    private int i;

    public MapItr(EntryIterable<?>[] entries, CItrMap<T> remover) {
        this.entries = Helpers.cast(entries);
        this.customItr = remover.entryIterator();
        this.remover = remover;

        if (entries == null)
            stage = ENDED;
    }

    private boolean computeNext() {
        if (customItr != null) {
            boolean flag = customItr.hasNext();
            if (flag) {
                obj = customItr.next();
            }
            return flag;
        }

        while (true) {
            if (entry != null) {
                obj = entry;
                entry = entry.nextEntry();
                return true;
            } else if (i < entries.length) {
                this.entry = entries[i++];
            } else {
                return false;
            }
        }
    }
}
