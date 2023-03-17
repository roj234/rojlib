package roj.asm.tree.anno;

import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.type.Type;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Annotation {
	public String clazz;
	public Map<String, AnnVal> values;

	public Annotation(String type, Map<String, AnnVal> values) {
		this.clazz = type.substring(1, type.length() - 1);
		this.values = values;
	}

	public Annotation() {
		this.values = new MyHashMap<>();
	}

	public final boolean getBoolean(String name, boolean def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asInt() != 0;
	}

	public final int getInt(String name, int def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asInt();
	}

	public final float getFloat(String name, float def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asInt();
	}

	public final double getDouble(String name, double def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asInt();
	}

	public final long getLong(String name, long def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asInt();
	}

	public final String getString(String name) {
		AnnVal av = values.get(name);
		if (av == null) return Helpers.nonnull();
		return av.asString();
	}

	public final String getString(String name, String def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asString();
	}

	public final AnnValEnum getEnum(String name) {
		AnnVal av = values.get(name);
		if (av == null) return Helpers.nonnull();
		return av.asEnum();
	}

	public final String getEnumValue(String name, String def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asEnum().value;
	}

	public final Type getClass(String name) {
		AnnVal av = values.get(name);
		if (av == null) return Helpers.nonnull();
		return av.asClass();
	}

	public final Annotation getAnnotation(String name) {
		AnnVal av = values.get(name);
		if (av == null) return Helpers.nonnull();
		return av.asAnnotation();
	}

	public final List<AnnVal> getArray(String name) {
		AnnVal av = values.get(name);
		if (av == null) return Helpers.nonnull();
		return av.asArray();
	}

	public final boolean containsKey(String name) {
		return values.containsKey(name);
	}

	public final void put(String name, AnnVal av) {
		if (values == Collections.EMPTY_MAP) values = new LinkedMyHashMap<>();
		values.put(name, av);
	}

	public static Annotation deserialize(ConstantPool pool, DynByteBuf r) {
		String type = ((CstUTF) pool.get(r)).getString();
		int len = r.readUnsignedShort();

		Map<String, AnnVal> params;
		if (len > 0) {
			params = new LinkedMyHashMap<>(len);
			while (len-- > 0) {
				params.put(((CstUTF) pool.get(r)).getString(), AnnVal.parse(pool, r));
			}
		} else {
			params = Collections.emptyMap();
		}

		return new Annotation(type, params);
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		w.putShort(pool.getUtfId("L" + clazz + ';')).putShort(values.size());
		for (Map.Entry<String, AnnVal> e : values.entrySet()) {
			e.getValue().toByteArray(pool, w.putShort(pool.getUtfId(e.getKey())));
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder("@").append(clazz.substring(clazz.lastIndexOf('/') + 1));
		if (!values.isEmpty()) {
			sb.append('(');
			if (values.size() == 1 && values.containsKey("value")) {
				sb.append(values.get("value"));
			} else {
				for (Map.Entry<String, AnnVal> e : values.entrySet()) {
					sb.append(e.getKey()).append(" = ").append(e.getValue()).append(", ");
				}
				sb.delete(sb.length() - 2, sb.length());
			}
			sb.append(')');
		}
		return sb.toString();
	}
}