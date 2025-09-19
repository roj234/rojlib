package roj.concurrent;

/**
 * @author Roj233
 * @since 2021/9/13 12:48
 */
public class LazyThreadLocal<T> extends ThreadLocal<T> {
	private final T initialValue;

	public LazyThreadLocal() {this(null);}
	public LazyThreadLocal(T initialValue) {this.initialValue = initialValue;}

	@Override
	@SuppressWarnings("unchecked")
	public T get() {
		Object map = ThreadLocalAccess.INSTANCE.getMap(this, Thread.currentThread());
		if (map != null) {
			Object entry = ThreadLocalAccess.INSTANCE.getEntry(map, this);
			if (entry != null) {
				return (T) ThreadLocalAccess.INSTANCE.getValue(entry);
			}
		}

		return initialValue;
	}

	@Override
	protected T initialValue() {return initialValue;}
}