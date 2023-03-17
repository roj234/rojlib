package roj.config.serial;

import roj.config.data.CEntry;

/**
 * @author Roj234
 * @since 2021/4/21 22:51
 */
public interface Serializer<O> {
	O deserializeRc(CEntry o);
	CEntry serializeRc(O t);
}
