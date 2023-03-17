package roj.collect;

import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2023/5/10 0010 16:30
 */
public class ObjectPool<T> {
	public ObjectPool(Supplier<T> creater, int maxSize) {
		this.creater = creater;
		this.max = maxSize;
		this.array = new Object[Math.min(maxSize,10)];
	}

	private Supplier<T> creater;
	private Object[] array;
	private int size, max;

	public final void clear() {
		for (int i = 0; i < size; i++) {
			array[i] = 0;
		}
		size = 0;
	}

	@SuppressWarnings("unchecked")
	public final T get() {
		if (size == 0) return newInstance();
		return (T) array[--size];
	}

	public final boolean reserve(T t) {
		if (size == max) return false;
		if (size == array.length) {
			Object[] o = new Object[Math.min(max,array.length+10)];
			System.arraycopy(array,0,o,0,o.length);
			array = o;
		}
		array[size++] = t;
		return true;
	}

	protected T newInstance() { return creater == null ? null : creater.get(); }
}
