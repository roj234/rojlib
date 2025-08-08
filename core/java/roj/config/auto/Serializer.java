package roj.config.auto;

import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2023/3/19 18:53
 */
public interface Serializer<T> extends CVisitor {
	T get();
	boolean finished();
	Serializer<T> reset();

	void write(CVisitor c, T t);

	default T deepcopy(T t) {
		reset();
		write(this, t);
		return get();
	}
}