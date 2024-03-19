package roj.collect;

/**
 * @author Roj234
 * @since 2023/5/10 0010 16:30
 */
public class ObjectPool<T> {
	public ObjectPool(int maxSize) {
		this.max = maxSize;
		this.array = new Object[Math.min(maxSize,10)];
	}

	private Object[] array;
	private int size;
	private final int max;

	@SuppressWarnings("unchecked")
	public final T get() {
		if (size == 0) return null;
		return (T) array[--size];
	}

	public final boolean reserve(T t) {
		if (size == max) return false;
		if (size == array.length) {
			Object[] o = new Object[Math.min(max,array.length+10)];
			System.arraycopy(array,0,o,0,size);
			array = o;
		}
		array[size++] = t;
		return true;
	}
}