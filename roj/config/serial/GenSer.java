package roj.config.serial;

import roj.config.data.CEntry;
import roj.config.data.CMapping;
import roj.config.data.CNull;

/**
 * @author Roj233
 * @since 2022/1/12 1:07
 */
abstract class GenSer extends WrapSerializer implements Serializer<Object> {
	GenSer() {}

	abstract void serialize0(CMapping map, Object o);

	public abstract Object deserializeRc(CEntry e);

	public CEntry serializeRc(Object o) {
		if (o == null) return CNull.NULL;
		CMapping map = new CMapping();
		serialize0(map, o);
		return map;
	}
}
