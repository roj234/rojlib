package roj.config.mapper;

import roj.ci.annotation.IndirectReference;
import roj.ci.annotation.Public;
import roj.collect.BitSet;
import roj.collect.IntBiMap;

/**
 * @author Roj234
 * @since 2023/3/21 12:50
 */
@Public
interface GA extends Cloneable {
	@IndirectReference
	void init(IntBiMap<String> fieldId, BitSet optionalEx);
	@IndirectReference
	default IntBiMap<String> fn() { return null; }
	@IndirectReference
	default void init2(Factory man, Object par) {}
	Object clone();
}