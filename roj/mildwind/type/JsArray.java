package roj.mildwind.type;

import roj.collect.IntMap;
import roj.math.MathUtils;
import roj.mildwind.JsContext;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/6/12 0012 14:48
 */
public final class JsArray extends JsMap {
	public JsObject[] list;
	public IntMap<JsObject> hash;
	public int length;

	public Type type() { return Type.ARRAY; }

	public JsArray() {
		super(JsContext.context().ARRAY_PROTOTYPE);
		list = new JsObject[5];
	}
	public JsArray(List<JsObject> val) {
		super(JsContext.context().ARRAY_PROTOTYPE);
		list = val.toArray(new JsObject[val.size()]);
	}

	public JsArray(int len, boolean unusedRealCap) { list = new JsObject[len]; }

	public JsArray(int len) {
		this();
		length = len;
	}

	public int asInt() { return length == 1 ? getByInt(0).asInt() : 0; }
	public String toString() {
		CharList sb = new CharList();

		for (int i = 0; i < length;) {
			JsObject o;
			if (list == null) o = hash.get(i);
			else if (i < list.length) o = list[i];
			else o = null;

			if (o == null) {
				sb.append("<empty>");
			} else {
				o._ref();
				sb.append(o);
			}

			if (++i == length) break;
			sb.append(',');
		}

		return sb.toStringAndFree();
	}

	public JsObject length() { return JsContext.getInt(length); }

	public JsObject get(String name) {
		if (TextUtil.isNumber(name) == 0) return getByInt(Integer.parseInt(name));
		if (name.equals("length")) return length();
		return super.get(name);
	}
	public void put(String name, JsObject value) {
		if (TextUtil.isNumber(name) == 0) putByInt(Integer.parseInt(name), value);
		if (name.equals("length")) {
			// todo _unref
			// length = value.asInt();
		}
		else super.put(name, value);
	}
	public boolean del(String name) {
		if (TextUtil.isNumber(name) == 0) return this.delByInt(Integer.parseInt(name));
		return super.del(name);
	}

	public JsObject getByInt(int i) {
		if (i < 0) return super.get(Integer.toString(i));

		JsObject o;
		if (list == null) o = hash.get(i);
		else if (i < list.length) o = list[i];
		else o = null;

		if (o == null) return super.get(Integer.toString(i));
		o._ref();

		return o;
	}
	public void putByInt(int i, JsObject value) {
		if (i < 0) {
			super.putByInt(i, value);
			return;
		}

		if (length <= i) length = i+1;

		if (list == null) {
			JsObject r = hash.put(i, value);
			if (r != null) r._unref();
		} else {
			if (convertToHash(i, value)) return;

			JsObject r = list[i];
			if (r != null) r._unref();

			list[i] = value;
		}
		value._ref();
	}

	private boolean convertToHash(int i, JsObject value) {
		i++;
		if (i < list.length) return false;

		float pct = length / (float)i;
		if (pct < 0.33f) {
			hash = new IntMap<>(length+1);
			for (int j = 0; j < list.length; j++) {
				JsObject o = list[j];
				if (o != null) hash.putInt(j, o);
			}
			list = null;
			return true;
		}

		JsObject[] arr = new JsObject[MathUtils.getMin2PowerOf(i)];
		System.arraycopy(list, 0, arr, 0, list.length);
		list = arr;

		return false;
	}

	public boolean delByInt(int i) {
		if (i < 0 || i >= length) return super.delByInt(i);

		if (list == null) {
			JsObject r = hash.remove(i);
			if (r != null) r._unref();
		} else {
			JsObject r = list[i];
			if (r != null) r._unref();

			list[i] = null;
		}
		return true;
	}

	public void push(JsObject obj) { putByInt(length, obj); }
	public void pushAll(JsObject arrayLike) {
		int length = arrayLike.length().asInt();
		for (int i = 0; i < length; i++) push(arrayLike.getByInt(i));
	}
}
