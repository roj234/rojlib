package roj.kscript.util.opm;

import roj.collect.MyHashMap;
import roj.kscript.type.KType;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/26 23:16
 */
public class ConstMap extends MyHashMap<String, KType> {
	public ConstMap() {
		super(8);
	}

	@Override
	protected MyHashMap.Entry<String, KType> createEntry(String id) {
		return new KOEntry(id, null);
	}

	public boolean isConst(String key) {
		KOEntry entry = (KOEntry) getEntry(key);
		return entry != null && (entry.flags & 1) != 0;
	}

	public void markConst(String key) {
		KOEntry entry = (KOEntry) getEntry(key);

		if (entry != null) entry.flags |= 1;
	}
}
