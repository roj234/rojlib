package roj.concurrent.collect;

import roj.collect.FindSet;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Roj234
 * @since 2020/8/20 14:03
 */
public class ConcurrentFindHashSet<T> implements FindSet<T> {
	private final ConcurrentHashMap<T, T> set = new ConcurrentHashMap<>();

	public ConcurrentFindHashSet() {}

	public ConcurrentFindHashSet(Collection<T> list) {
		addAll(list);
	}

	@Override
	public T find(T t) {
		return set.getOrDefault(t, t);
	}

	@Override
	public T intern(T t) {
		T t1 = set.putIfAbsent(t, t);
		return t1 == null ? t : t1;
	}

	@Override
	public int size() {
		return set.size();
	}

	@Override
	public boolean isEmpty() {
		return size() == 0;
	}

	@Override
	public boolean contains(Object key) {
		return set.containsKey(key);
	}

	@Nonnull
	@Override
	public Iterator<T> iterator() {
		return set.keySet().iterator();
	}

	@Nonnull
	@Override
	public Object[] toArray() {
		return set.keySet().toArray();
	}

	@Nonnull
	@Override
	public <T1> T1[] toArray(@Nonnull T1[] a) {
		return set.keySet().toArray(a);
	}

	@Override
	public boolean add(T key) {
		return null == set.putIfAbsent(key, key);
	}

	@Override
	public boolean remove(Object key) {
		return set.remove(key) != null;
	}

	@Override
	public boolean containsAll(@Nonnull Collection<?> c) {
		return set.keySet().containsAll(c);
	}

	@Override
	public boolean addAll(@Nonnull Collection<? extends T> m) {
		boolean flag = false;
		for (T t : m) flag |= null != set.putIfAbsent(t, t);
		return flag;
	}

	@Override
	public boolean retainAll(@Nonnull Collection<?> c) {
		return set.keySet().retainAll(c);
	}

	@Override
	public boolean removeAll(@Nonnull Collection<?> c) {
		for (Object o : c) set.remove(o);
		return true;
	}

	@Override
	public void clear() {
		set.clear();
	}
}
