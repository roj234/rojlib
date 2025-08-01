package roj.config.auto;

import roj.ReferenceByGeneratedClass;
import roj.collect.BitSet;
import roj.collect.IntBiMap;
import roj.reflect.Java22Workaround;

/**
 * @author Roj234
 * @since 2023/3/21 12:50
 */
@Java22Workaround
interface GA extends Cloneable {
	@ReferenceByGeneratedClass
	void init(IntBiMap<String> fieldId, BitSet optionalEx);
	@ReferenceByGeneratedClass
	default IntBiMap<String> fn() { return null; }
	@ReferenceByGeneratedClass
	default void init2(SerializerFactoryImpl man, Object par) {}
	Object clone();
}