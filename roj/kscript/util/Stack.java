package roj.kscript.util;

import roj.kscript.type.KType;

import javax.annotation.Nonnull;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/27 22:18
 */
public final class Stack {
    static final Entry head = new Entry();

    static {
        head.prev = head;
    }

    Entry tail = head;
    Entry dummied;

    int size, dc;

    public Stack() {
    }

    @Nonnull
    /**
     * = last(0)
     */
    public KType last() {
        ce();
        return tail.value;
    }

    @Nonnull
    public KType pop() {
        ce();
        KType v = tail.value;

        final Entry t = this.tail;
        tail = t.prev;

        if (dc < 64) { // what is best?
            Entry d = dummied;
            if (d != null)
                d.prev = t;
            dummied = t;
            t.prev = null;
            t.value = null;
            dc++;
        }

        size--;
        return v;
    }

    private void ce() {
        if (tail == head) throw new IllegalStateException("Stack is empty.");
    }

    public void setLast(@Nonnull KType base) {
        ce();
        tail.value = base;
    }

    public void push(@Nonnull KType base) {
        Entry entry;
        if (dummied != null) {
            dc--;
            entry = dummied;
            entry.value = base;
            dummied = dummied.prev;
        } else {
            entry = new Entry(base);
        }

        entry.prev = tail;
        tail = entry;

        if (++size > 2048)
            throw new IllegalStateException("Stack overflow 2048: " + this);
    }

    public void clear() {
        tail = head;
        size = 0;
    }

    @Nonnull
    public KType last(int i) {
        if (i >= size)
            throw new ArrayIndexOutOfBoundsException(i);

        Entry entry = tail;
        while (i-- > 0) {
            entry = entry.prev;
        }
        return entry.value;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Stack{");
        if (tail != head) {
            Entry entry = tail;
            while (entry != head) {
                sb.append(entry.value).append(", ");
                entry = entry.prev;
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append('}').toString();
    }

    public int size() {
        return size;
    }

    private static final class Entry {
        KType value;
        Entry prev;

        public Entry() {
        }

        public Entry(KType base) {
            this.value = base;
        }
    }
}
