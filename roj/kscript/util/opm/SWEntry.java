package roj.kscript.util.opm;

import roj.collect.MyHashMap;
import roj.kscript.asm.Node;
import roj.kscript.type.KType;
import roj.kscript.util.VInfo;

/**
 * @author Roj234
 * @since 2021/4/25 23:13
 */
public class SWEntry extends MyHashMap.Entry<KType, Node> {
	public VInfo diff;

	public SWEntry(KType k, Node v) {
		super(k, v);
	}
}
