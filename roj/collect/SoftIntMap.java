package roj.collect;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/9/5 19:31
 */
public class SoftIntMap<T> extends IntMap<T> {
    public class MyEntry<V> extends IntMap.Entry<V> {
        SoftReference<Object> ref;

        @SuppressWarnings("unchecked")
        protected MyEntry(int k, Object v) {
            super(k, null);
            setValue((V) v);
        }

        @Override
        @SuppressWarnings("unchecked")
        public V setValue(V now) {
            if (now == null)
                throw new NullPointerException("Null value is not supported by SoftIntMap.");
            Object old = NOT_USING;
            if (ref != null) {
                old = ref.get();
                if (old != now) {
                    ref.clear();
                    if (now == NOT_USING)
                        ref = null;
                    else
                        ref = new SoftReference<>(now, queue);
                }
            }
            return (V) old;
        }

        @Override
        @SuppressWarnings("unchecked")
        public V getValue() {
            if (ref == null)
                return (V) NOT_USING;
            Object o = ref.get();
            if (o == null) {
                ref = null;
                o = NOT_USING;
            }
            return (V) o;
        }
    }

    private final ReferenceQueue<Object> queue = new ReferenceQueue<>();

    public void removeDummiedEntries() {
        while (queue.poll() != null) {
            size--;
        }
    }

    @Override
    protected Entry<T> createEntry(int id, T value) {
        return new MyEntry<>(id, value);
    }
}
