package roj.kscript.util.opm;

import roj.collect.MyHashMap;
import roj.kscript.api.IObject;
import roj.kscript.type.KBool;
import roj.kscript.type.KObject;
import roj.kscript.type.KType;
import roj.kscript.util.UnmodifiableException;

import java.util.Objects;

import static roj.collect.IntMap.UNDEFINED;

/**
 * @author Roj234
 * @since 2021/4/25 23:26
 */
public class ObjectPropMap extends MyHashMap<String, KType> {
	public ObjectPropMap() {
		super(2);
	}

	public ObjectPropMap(int initCap) {
		super(initCap);
	}

	public ObjectPropMap(MyHashMap<String, KType> map) {
		super(map);
	}

	@Override
	protected Entry<String, KType> createEntry(String id) {
		return new KOEntry(id, null);
	}

	@Override
	public KType get(Object id) {
		Entry<String, KType> entry = getEntry((String) id);
		return entry == null ? null : entry.getValue();
	}

	@Override
	public KType getOrDefault(Object key, KType def) {
		Entry<String, KType> entry = getEntry((String) key);
		if (entry == null || entry.v == UNDEFINED) return def;
		return entry.getValue();
	}

	@Override
	public KType put(String key, KType e) {
		if (size > length * loadFactor) {
			length <<= 1;
			resize();
		}

		Entry<String, KType> entry = getOrCreateEntry(key);
		Object old = entry.setValue(e);
		if (old == UNDEFINED) {
			size++;
			return null;
		}
		return (KType) old;
	}

	public static void Object_defineProperty(KObject src, String id, IObject prop) {
		ObjectPropMap map = (ObjectPropMap) src.getInternal();

		KOEntry entry;
		if (prop.containsKey("get") || prop.containsKey("set")) {
			Entry<String, KType> chk = map.getEntryFirst(id, true);
			entry = new SGEntry(id, null);

			if (prop.containsKey("get")) {
				entry.v = prop.get("get").asFunction();
			}

			if (prop.containsKey("set")) {
				((SGEntry) entry).set = prop.get("set").asFunction();
			}

			if (chk.v == UNDEFINED || Objects.equals(id, chk.k)) {
				byte flags = ((KOEntry) chk).flags;
				if ((flags & 1) == 1) {
					throw new UnmodifiableException();
				}
				entry.flags = flags;

				map.entries[map.hash(id)] = entry;
			} else {
				Entry<String, KType> prev;
				while (chk.next != null) {
					prev = chk;
					chk = chk.next;
					if (Objects.equals(id, chk.k)) {
						byte flags = ((KOEntry) chk).flags;
						if ((flags & 1) == 1) {
							throw new UnmodifiableException();
						}
						entry.flags = flags;

						prev.next = entry;
						entry.next = chk.next;
					}
				}
				chk.next = entry;
			}
		} else {
			entry = (KOEntry) map.getEntry(id);

			if ((entry.flags & 1) == 0) {
				KType v = prop.getOrNull("value");
				if (v != null) entry.v = v;
			} else {
				throw new UnmodifiableException();
			}
		}

		if (!prop.getOr("configurable", KBool.FALSE).asBool()) {
			entry.flags |= 1;
		} else {
			entry.flags &= ~1;
		}

		if (!prop.getOr("enumerable", KBool.TRUE).asBool()) {
			entry.flags |= 2;
		} else {
			entry.flags &= ~2;
		}
	}
}
