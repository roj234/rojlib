package roj.collect;

import java.util.Set;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/8/11 14:59
 */
public class FastSetList<T> extends SimpleList<T> implements Set<T> {
    protected ToIntMap<T> indexer;
    int refOps, slowOps, lastChk;
    byte enable;

    public FastSetList() {
        this(16);
        indexer = new ToIntMap<>(16);
    }

    public FastSetList(int size) {
        ensureCapacity(size);
        indexer = new ToIntMap<>(size);
    }

    public boolean remove(Object o) {
        int v = indexer.getOrDefault(o, -1);
        if (v == -1)
            return super.remove(o);

        super.remove(v);
        return true;
    }

    @Override
    public int indexOf(Object o) {
        if((slowOps++ & 31) == 0)
            update();
        if(!initFastChk())
            return super.indexOf(o);
        return indexer.getOrDefault(o, -1);
    }

    @Override
    public int lastIndexOf(Object o) {
        if((slowOps++ & 31) == 0)
            update();
        if(!initFastChk())
            return super.lastIndexOf(o);
        return indexer.getOrDefault(o, -1);
    }

    public boolean contains(Object e) {
        return indexer.containsKey(e);
    }

    public void clear() {
        super.clear();
        indexer.clear();
    }

    @Override
    protected void handleAdd(int pos, T element) {
        if((refOps++ & 31) == 0)
            update();
        enable &= ~2;
    }

    @SuppressWarnings("unchecked")
    private boolean initFastChk() {
        if((enable & 3) == 1) {
            for (int i = 0; i < size; i++) {
                indexer.putInt((T) list[i], i);
            }
            return true;
        }
        return false;
    }

    private void update() {
            int ct = (int) System.currentTimeMillis();
            if (ct - lastChk > 10000) {
                lastChk = ct;
                enable = (byte) ((enable & 254) | (refOps < slowOps / 100 ? 1 : 0));
                refOps = slowOps = 0;
            }
    }

    @Override
    protected void handleAdd(int pos, T[] elements, int offset, int length) {
        for (int i = offset; i < length; i++) {
            handleAdd(pos + i - offset, elements[i]);
        }
    }

    @Override
    protected void handleRemove(int pos, T element) {
        if((refOps++ & 31) == 0)
            update();
        enable &= ~2;

        indexer.remove(element);

    }

    @Override
    public void fill(T t) {
        throw new UnsupportedOperationException();
    }
}
