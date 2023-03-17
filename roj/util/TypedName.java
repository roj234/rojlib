package roj.util;

/**
 * @author Roj233
 * @since 2022/5/17 14:47
 */
public class TypedName<T> {
	public final String name;

	public TypedName(String name) {
		this.name = name;
	}

	@SuppressWarnings("unchecked")
	public T cast(Object o) {
		return (T) o;
	}

	@Override
	public String toString() {
		return name;
	}
}
