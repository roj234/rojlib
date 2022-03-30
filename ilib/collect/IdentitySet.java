package ilib.collect;

import roj.collect.MyHashSet;

/**
 * @author solo6975
 * @since 2022/3/31 14:32
 */
public final class IdentitySet<T> extends MyHashSet<T> {
    protected int indexFor(T id) {
        int v;
        return ((v = System.identityHashCode(id)) ^ (v >>> 16)) & (length - 1);
    }
}
