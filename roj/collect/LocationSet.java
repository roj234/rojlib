package roj.collect;

import org.jetbrains.annotations.NotNull;
import roj.util.ArrayUtil;
import roj.util.Helpers;

import java.util.Collections;
import java.util.Iterator;

/**
 * @author Roj233
 * @since 2022/1/18 3:13
 */
public class LocationSet<V> implements Iterable<V> {
	public LocationSet() {}

	public LocationSet(int cap) {values = new Object[cap];}

	public LocationSet<V> setNearest() {
		nearest = true;
		min_dist2 = Integer.MAX_VALUE;
		max_dist2 = Integer.MIN_VALUE;
		if (values == null) values = new Object[1];
		return this;
	}

	public LocationSet<V> setAll() {
		nearest = false;
		min_dist2 = Integer.MAX_VALUE;
		max_dist2 = Integer.MIN_VALUE;
		if (values == null) values = new Object[10];
		return this;
	}

	public boolean isNearest() {
		return nearest;
	}

	public LocationSet<V> setLimit(int limit) {
		this.limit = limit;
		return this;
	}

	public int getLimit() {
		return limit;
	}

	public void clear() {
		size = 0;
		min_dist2 = Integer.MAX_VALUE;
		max_dist2 = Integer.MIN_VALUE;
	}

	public int size() {
		return size;
	}

	public Object[] getValues() {
		return values;
	}

	public int getMaxDistanceSq() {
		return max_dist2;
	}

	public int getMinDistanceSq() {
		return min_dist2;
	}

	@NotNull
	@Override
	public Iterator<V> iterator() {
		return size == 0 ? Collections.emptyIterator() : Helpers.cast(new ArrayIterator<>(values, 0, size));
	}

	protected boolean nearest;
	protected int min_dist2, max_dist2;
	protected int limit;

	protected Object[] values;
	protected int size;

	int cx, cy, cz;

	public boolean add(int dist2, V v) {
		if (nearest) {
			if (dist2 < min_dist2) {
				min_dist2 = dist2;
				values[0] = v;
				size = 1;
			}
		} else {
			if (size >= limit) return false;

			min_dist2 = Math.min(dist2, min_dist2);
			max_dist2 = Math.max(dist2, max_dist2);
			if (values.length == size) {
				Object[] a1 = new Object[values.length + 10];
				System.arraycopy(values, 0, a1, 0, size);
				values = a1;
			}
			values[size++] = v;
		}
		return true;
	}

	@Override
	public String toString() {
		return "LocationSet{min=" + min_dist2 + ", max=" + max_dist2 + ", " + ArrayUtil.toString(values, 0, size) + '}';
	}
}