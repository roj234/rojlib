package roj.config.auto;

import roj.ReferenceByGeneratedClass;
import roj.collect.IntBiMap;
import roj.collect.MyBitSet;

/**
 * @author Roj234
 * @since 2023/3/21 0021 12:50
 */
interface GA extends Cloneable {
	@ReferenceByGeneratedClass
	void init(IntBiMap<String> fieldId, MyBitSet optionalEx);
	@ReferenceByGeneratedClass
	default IntBiMap<String> fn() { return null; }
	@ReferenceByGeneratedClass
	default void init2(SerializerFactoryImpl man, Object par) {}
	Object clone();
}