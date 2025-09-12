package roj.config.mapper;

import roj.ci.annotation.Public;
import roj.ci.annotation.ReferenceByGeneratedClass;
import roj.collect.BitSet;
import roj.collect.IntBiMap;

/**
 * @author Roj234
 * @since 2023/3/21 12:50
 */
@Public
interface GA extends Cloneable {
	@ReferenceByGeneratedClass
	void init(IntBiMap<String> fieldId, BitSet optionalEx);
	@ReferenceByGeneratedClass
	default IntBiMap<String> fn() { return null; }
	@ReferenceByGeneratedClass
	default void init2(Factory man, Object par) {}
	Object clone();
}