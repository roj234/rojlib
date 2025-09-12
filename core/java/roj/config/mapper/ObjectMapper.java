package roj.config.mapper;

import roj.config.ValueEmitter;

/**
 * @author Roj234
 * @since 2023/3/19 18:53
 */
public interface ObjectMapper<T> extends ValueEmitter {
	/**
	 * 简单创建ObjectMapper
	 * @see ObjectMapperFactory
	 */
	static <U> ObjectMapper<U> forClass(Class<U> type) {return ObjectMapperFactory.SAFE.serializer(type);}

	T get();
	boolean finished();
	ObjectMapper<T> reset();

	/**
	 * 将值通过emitter序列化
	 * 这个方法是线程安全的
	 */
	void write(ValueEmitter emitter, T value);

	default T deepcopy(T obj) {
		reset();
		write(this, obj);
		return get();
	}
}