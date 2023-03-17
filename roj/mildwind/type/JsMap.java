package roj.mildwind.type;

import roj.math.MathUtils;
import roj.mildwind.JsContext;
import roj.mildwind.api.Arguments;
import roj.mildwind.util.ObjectShape;
import roj.mildwind.util.ScriptException;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

import static roj.collect.IntMap.MAX_NOT_USING;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public class JsMap extends AbstractJsMap implements JsObject {
	JsMap __proto__;
	ObjectShape shape;

	public JsMap() {
		this(16);
		JsContext ctx = JsContext.context();
		__proto__ = ctx.OBJECT_PROTOTYPE;
		shape = ctx.shapeFor(ctx.OBJECT_PROTOTYPE); }
	public JsMap(JsMap prototype) { this(4);
		__proto__ = prototype;
		if (prototype != null) shape = JsContext.context().shapeFor(prototype); }
	private JsMap(int size) { length = size; }

	public String toString() {
		Entry ts = entry("toString");
		return ts == null ? "[object "+type()+"]" : ts.getValue(this).toString();
	}

	public final JsObject __proto__() { return __proto__ == null ? JsNull.NULL : __proto__; }

	public void defineGS(Object name, JsObject getter, JsObject setter, int flag) {

	}
	public void defineVal(Object name, JsObject value, int flag) {

	}

	public JsObject get(String name) {
		if (name.equals("__proto__")) return __proto__;

		Entry entry = entry(name);
		if (entry != null) {
			JsObject v = entry.getValue(this);
			v._ref();
			return v;
		}
		return JsNull.UNDEFINED;
	}
	public void put(String name, JsObject value) {
		if (name.equals("__proto__")) {
			if (value == JsNull.NULL) __proto__ = null;
			else if (value instanceof JsMap) __proto__ = (JsMap) value;
			return;
		}

		putInternal(name, value);
	}
	private void putInternal(Object name, JsObject value) {
		Entry entry = getOrCreateEntry(name);
		if (entry.v == null) {
			size++;
			entry.v = value;
			value._ref();
		} else {
			if ((entry.flag() & CONFIGURABLE) == 0) return;
			entry.setValue(this, value);
		}
	}
	public boolean del(String name) {
		Entry entry = getEntry(name);
		if (entry == null) return true;
		if ((entry.flag()&CONFIGURABLE) != 0) {
			remove(name);
			return true;
		}
		return false;
	}

	final Entry entry(String name) {
		Entry entry;
		JsMap map = this;
		do {
			entry = map.getEntry(name);
			if (entry != null) break;
			map = map.__proto__;
		} while (map != null);
		return entry;
	}

	public boolean op_in(JsObject toFind) { return getEntry(toFind.toString()) != null; }
	public boolean op_instanceof(JsObject object) { throw new ScriptException("Right-hand side of 'instanceof' is not callable"); }

	// todo implement
	public Iterator<JsObject> _keyItr() {
		return super._keyItr();
	}
	public Iterator<JsObject> _valItr() {
		return super._valItr();
	}

	// region hashmap impl

	static final int CONFIGURABLE = 1, ENUMERABLE = 2;
	static class Entry {
		Object k;
		JsObject v;

		Entry next;

		Entry(Object k, JsObject v) {
			this.k = k;
			this.v = v;
		}

		int flag() { return CONFIGURABLE|ENUMERABLE; }
		JsObject getValue(JsMap map) { return v; }
		void setValue(JsMap map, JsObject value) { v._unref(); v = value; }
		public Entry nextEntry() {
			// todo self next?
			Entry n = next;
			while (n instanceof ValProp && (((ValProp) n).flag&ENUMERABLE) == 0) n = n.next;
			return n;
		}

		void unref() { v._unref(); }
	}
	static class ValProp extends Entry {
		ValProp(String k, JsObject v) { super(k, v); }
		byte flag;

		int flag() { return flag; }
		void setValue(JsMap map, JsObject value) { if ((flag&CONFIGURABLE) != 0) super.setValue(map, value); }
	}
	static final class FnProp extends ValProp {
		JsObject setter;

		FnProp(String k, JsObject getter) { super(k, getter); }

		JsObject getValue(JsMap map) { return v._invoke(map, Arguments.EMPTY); }
		void setValue(JsMap map, JsObject value) { setter._invoke(map, JsContext.getArguments(1).push(value)); }

		void unref() { v._unref(); setter._unref(); }
	}

	protected Entry[] entries;
	private int size = 0;

	protected int length, mask;
	static final float LOAD_FACTOR = 1;

	public void ensureCapacity(int size) {
		if (size < length) return;
		length = MathUtils.getMin2PowerOf(size);

		if (this.entries != null) resize();
		else this.mask = length-1;
	}

	protected final void resize() {
		Entry[] newEntries = new Entry[length];
		int i = 0, j = entries.length;
		int mask1 = length-1;
		for (; i < j; i++) {
			Entry entry = entries[i];
			while (entry != null) {
				Entry next = entry.next;
				int newKey = hash(entry.k)&mask1;
				Entry old = newEntries[newKey];
				newEntries[newKey] = entry;
				entry.next = old;
				entry = next;
			}
		}

		this.entries = newEntries;
		this.mask = mask1;
	}

	public void putAll(@Nonnull Map<?, ? extends JsObject> map) {
		ensureCapacity(size + map.size());
		for (Map.Entry<?, ? extends JsObject> entry : map.entrySet()) {
			putInternal(entry.getKey(), entry.getValue());
		}
	}
	public void putAll(JsObject map) {
		if (map instanceof JsMap) {
			int size1 = ((JsMap) map).size;
			if (size1 == 0 || map == this) return;

			ensureCapacity(size+size1);
			Entry[] entries = ((JsMap) map).entries;
			for (Entry entry : entries) {
				while (entry != null) {
					putInternal(entry.k, entry.v);
					entry = entry.next;
				}
			}
		} else {
			Iterator<JsObject> kit = map._keyItr();
			Iterator<JsObject> vit = map._valItr();
			throw new UnsupportedOperationException("not implemented yet!");
		}
	}

	protected final boolean remove(Object id) {
		Entry prevEntry = null;
		Entry entry = getEntryFirst(id, false);
		while (entry != null) {
			if (id.equals(entry.k)) {
				size--;

				if (prevEntry != null) prevEntry.next = entry.next;
				else entries[hash(id)&mask] = entry.next;

				entry.unref();
				if (entry.getClass() == Entry.class) addCache(entry);
				return true;
			}
			prevEntry = entry;
			entry = entry.next;
		}
		return false;
	}

	protected final Entry getEntry(Object id) {
		Entry entry = getEntryFirst(id, false);
		while (entry != null) {
			if (id.equals(entry.k)) return entry;
			entry = entry.next;
		}
		return null;
	}
	protected final Entry getOrCreateEntry(Object id) {
		if (size > length * LOAD_FACTOR) {
			length <<= 1;
			resize();
		}

		Entry entry = getEntryFirst(id, true);
		// noinspection all
		if (entry.v == null) return entry;
		while (true) {
			if (id.equals(entry.k)) return entry;
			if (entry.next == null) break;
			entry = entry.next;
		}
		Entry unused = remCache(id);
		entry.next = unused;
		return unused;
	}
	private Entry getEntryFirst(Object id, boolean create) {
		int i = hash(id)&mask;
		if (entries == null) {
			if (!create) return null;
			entries = new Entry[length];
		}
		Entry entry;
		if ((entry = entries[i]) == null) {
			if (!create) return null;
			entries[i] = entry = remCache(id);
		}
		return entry;
	}

	private int hash(Object id) {
		int v = id.hashCode();
		return v ^ (v >>> 16);
	}

	private Entry cache;
	private int cacheLen;

	private void addCache(Entry entry) {
		if (cache != null && cacheLen > MAX_NOT_USING) {
			return;
		}
		entry.k = null;
		entry.v = null;
		entry.next = cache;
		cacheLen++;
		cache = entry;
	}
	private Entry remCache(Object id) {
		Entry et = this.cache;

		if (et != null) {
			et.k = id;
			this.cache = et.next;
			et.next = null;
			cacheLen--;
			et.v = null;
		} else {
			et = new Entry(id, null);
		}
		return et;
	}

	public void clear() {
		if (size == 0) return;
		size = 0;
		if (entries == null) return;
		if (cacheLen < MAX_NOT_USING) {
			for (int i = 0; i < length; i++) {
				Entry entry = entries[i];
				if (entry != null) {
					entry.unref();
					if (entry.getClass() == Entry.class) addCache(entry);
					entries[i] = null;
				}
			}
		} else Arrays.fill(entries, null);
	}
	// endregion
}
