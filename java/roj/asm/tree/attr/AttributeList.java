package roj.asm.tree.attr;

import roj.asm.cp.ConstantPool;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import java.util.Map;

/**
 * @author Roj234
 * @since 2020/10/25 16:50
 */
public final class AttributeList extends SimpleList<Attribute> {
	private static final int THE_SIZE = 4;
	private Map<String, Attribute> byName;

	public AttributeList() {}
	public AttributeList(int cap) { super(cap); }

	public AttributeList(AttributeList l) {
		super(l);
		if (l.size > 0 && l.byName != null) byName = new MyHashMap<>(l.byName);
	}

	public Object getByName(String name) {
		if (byName == null) {
			if (size < THE_SIZE) {
				int o = _indexOf(name);
				return o<0?null:list[o];
			}
			byName = new MyHashMap<>(size);
			return _find(name);
		}
		Object o = byName.get(name);
		if (o == null) return _find(name);
		return o;
	}

	private Attribute _find(String name) {
		for (int i = 0; i < size; i++) {
			Attribute attr = (Attribute) list[i];
			byName.put(attr.name(), attr);
		}
		return byName.get(name);
	}
	private int _indexOf(String name) {
		for (int i = 0; i < size; i++) {
			if (((Attribute) list[i]).name().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	public boolean removeByName(String name) {
		for (int i = 0; i < size; i++) {
			if (((Attribute) list[i]).name().equals(name)) {
				remove(i);
				return true;
			}
		}
		return false;
	}

	@Override
	public Attribute remove(int i) {
		Attribute attr = super.remove(i);
		if (byName != null) byName.remove(attr.name());
		return attr;
	}

	@Override
	public void clear() {
		super.clear();
		if (byName != null) byName.clear();
	}

	public void i_direct_add(Attribute attr) {
		super.add(attr);
		if (byName != null) byName.clear();
	}

	@Override
	public boolean add(Attribute attr) {
		if (attr == null) throw new NullPointerException("attr");
		if (byName == null) {
			if (size > THE_SIZE) getByName("");

			Object[] o = list;
			for (int i = 0; i < size; i++) {
				if (((Attribute) o[i]).name().equals(attr.name())) {
					o[i] = attr;
					return true;
				}
			}
		} else {
			Attribute a1 = byName.put(attr.name(), attr);
			if (a1 != null) {
				list[indexOfAddress(a1)] = attr;
				return true;
			}
		}
		return super.add(attr);
	}

	@Override
	public Attribute set(int i, Attribute now) {
		Attribute prev = super.set(i, now);
		if (byName != null) {
			byName.remove(prev.name());
			byName.put(now.name(), now);
		}
		return prev;
	}

	public void toByteArray(DynByteBuf w, ConstantPool cw) {
		int pos = w.wIndex();
		int size = this.size;
		w.putShort(size);
		var o = list;

		for (int i = 0; i < this.size; i++) {
			var attr = (Attribute) o[i];
			if (attr.isEmpty()) size--;
			else attr.toByteArray(w, cw);
		}

		w.putShort(pos, size);
	}
}