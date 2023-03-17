package roj.kscript.util.opm;

import roj.collect.MyHashMap;
import roj.kscript.type.KType;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/25 23:13
 */
public class KOEntry extends MyHashMap.Entry<String, KType> {
	public byte flags;

	public KOEntry(String k, KType v) {
		super(k, v);
	}

	@Override
	public MyHashMap.Entry<String, KType> nextEntry() {
		KOEntry next = (KOEntry) this.next;
		if (next == null || (next.flags & 2) == 0) return next;
		return next.next;
	}
}
