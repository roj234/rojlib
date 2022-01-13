/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package roj.collect;

import roj.util.Helpers;

import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since  2020/8/14 17:06
 */
class MapItr<T extends MapLikeEntry<T>> {
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

    private final T[]         entries;
    private final MapLike<T>  remover;
    private final Iterator<T> customItr;
    private T entry;
    private int i;

    public void reset() {
        if (entries == null)
            stage = ENDED;
        else {
            entry = null;
            i = 0;
            stage = INITIAL;
        }
    }

    public MapItr(MapLikeEntry<?>[] entries, MapLike<T> remover) {
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
