package roj.config.serial;

import roj.collect.IntBiMap;
import roj.collect.MyBitSet;

/**
 * @author Roj234
 * @since 2023/3/21 0021 12:50
 */
interface GenAdapter {
	void init(IntBiMap<String> fieldId, MyBitSet optionalEx);
	default IntBiMap<String> fieldNames() { return null; }
	void secondaryInit(SerializerFactory man, Object par);
	Object clone();
}
