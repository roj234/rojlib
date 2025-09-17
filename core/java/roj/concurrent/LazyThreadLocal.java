package roj.concurrent;

import roj.ci.annotation.Public;
import roj.reflect.Bypass;

/**
 * @author Roj233
 * @since 2021/9/13 12:48
 */
public class LazyThreadLocal<T> extends ThreadLocal<T> {
	private final T initialValue;

	public LazyThreadLocal() {this(null);}
	public LazyThreadLocal(T initialValue) {this.initialValue = initialValue;}

	@Public
	interface H {
		Object getMap(Object threadLocal, Thread thread);
		Object getEntry(Object threadLocalMap, Object threadLocal);
		Object getValue(Object threadLocalMapEntry);
		void remove(Object threadLocalMap, Object threadLocal);
	}
	static final H ACCESS;
	static {
		try {
			ACCESS = Bypass.builder(H.class).delegate_o(ThreadLocal.class, "getMap").delegate_o(Class.forName("java.lang.ThreadLocal$ThreadLocalMap"), new String[]{"remove", "getEntry"}).access(Class.forName("java.lang.ThreadLocal$ThreadLocalMap$Entry"), "value", "getValue", null).build();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public T get() {
		Object map = ACCESS.getMap(this, Thread.currentThread());
		if (map != null) {
			Object entry = ACCESS.getEntry(map, this);
			if (entry != null) {
				return (T) ACCESS.getValue(entry);
			}
		}

		return initialValue;
	}

	@Override
	protected T initialValue() {return initialValue;}
}