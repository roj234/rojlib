package roj.collect;

import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.Iterator;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/11/9 23:10
 */
public abstract class TrieEntry<T extends TrieEntry<T>> implements Iterable<T> {
    protected final char c;
    protected CharMap<T> children;

    protected TrieEntry(char ch) {
        this.c = ch;
        this.children = new CharMap<>(1, 1.5f);
    }

    public int childrenCount() {
        return children.size();
    }

    public void putChild(T e) {
        children.put(e.c, e);
    }

    public boolean removeChild(T e) {
        return children.remove(e.c) != null;
    }

    public T getChild(char c) {
        return children.get(c);
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        return children.values().iterator();
    }

    public abstract int copyFrom(T node);

    protected abstract T newInstance();

    protected abstract int recursionSum();

    CharSequence text() {
        return null;
    }

    protected void append(CharList sb) {
        sb.append(c);
    }

    protected int length() {
        return 1;
    }

    @Override
    public String toString() {
        return "CE{" + c + "}";
    }
}
