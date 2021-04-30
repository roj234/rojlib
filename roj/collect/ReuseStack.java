package roj.collect;

import roj.util.Helpers;

import javax.annotation.Nonnull;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author Roj233
 * @since 2020/9/28 12:29
 */
public class ReuseStack<K> implements Iterable<K> {
    protected static final Entry<?> head = new Entry<>();

    protected Entry<K> tail;

    protected int size;

    public ReuseStack() {
        clear();
    }

    public boolean isEmpty() {
        return size == 0;
    }

    @Nonnull
    public K last() {
        check();
        return tail.value;
    }

    @Nonnull
    public K pop() {
        check();
        K v = tail.value;

        tail = tail.prev;
        size--;
        return v;
    }

    private void check() {
        if (tail == head) throw new IllegalStateException("NodeStack is empty.");
    }

    public void setLast(@Nonnull K base) {
        check();
        tail.value = base;
    }

    public void push(@Nonnull K base) {
        Entry<K> entry = new Entry<>(base);

        entry.prev = tail;
        tail = entry;

        size++;
    }

    public void clear() {
        tail = Helpers.cast(head);
        size = 0;
    }

    public int size() {
        return size;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("[");
        if (tail != head) {
            Entry<?> entry = tail;
            while (entry != head) {
                sb.append(entry.value).append(", ");
                entry = entry.prev;
            }
            sb.delete(sb.length() - 2, sb.length());
        }
        return sb.append(']').toString();
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<K> iterator() {
        return new EntryIterator<>(this);
    }

    protected static final class Entry<k> {
        public k value;
        public Entry<k> prev;

        private Entry() {
        }

        public Entry(k base) {
            this.value = base;
        }
    }

    private static final class EntryIterator<K> implements Iterator<K> {
        Entry<K> last, curr;
        ReuseStack<K> stack;

        public EntryIterator(ReuseStack<K> stack) {
            last = Helpers.cast(head);
            curr = stack.tail;
            this.stack = stack;
        }

        @Override
        public boolean hasNext() {
            return curr != head;
        }

        @Override
        public K next() {
            if (curr == head)
                throw new NoSuchElementException();
            K k = curr.value;


            last = curr;


            curr = curr.prev;

            return k;
        }

        @Override
        public void remove() {
            if (last == null)
                throw new IllegalStateException();
            last.prev = last.prev.prev; // curr = next.prev
            stack.size--;
            last = null;
        }
    }
}
