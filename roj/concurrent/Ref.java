package roj.concurrent;

/**
 * 引用
 *
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public final class Ref<T> {
	private T t;

	public Ref(T t) {
		this.t = t;
	}

	public T get() {
		return t;
	}

	public void set(T t) {
		this.t = t;
	}

	public static <X> Ref<X> from() {
		return new Ref<>(null);
	}
}
