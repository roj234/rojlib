package roj.collect;

/**
 * @author solo6975
 * @since 2022/3/31 14:32
 */
public final class IdentitySet<T> extends MyHashSet<T> {
	protected int indexFor(T id) {
		int v = System.identityHashCode(id) * -1640531527;
		return (v ^ (v >>> 16));
	}

	@Override
	protected boolean eq(T id, Object k) {
		return id == k;
	}
}
