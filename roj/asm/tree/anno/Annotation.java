package roj.asm.tree.anno;

import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;


/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Annotation {
	public String type;
	public Map<String, AnnVal> values;

	public Annotation(String type, Map<String, AnnVal> values) {
		this.type = type.substring(1, type.length() - 1);
		this.values = values;
	}

	public Annotation() {
		this.values = new MyHashMap<>();
	}

	public final boolean getBoolean(String name) { return getBoolean(name, false); }
	public final boolean getBoolean(String name, boolean def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asInt() != 0;
	}

	public final int getInt(String name) { return getInt(name, 0); }
	public final int getInt(String name, int def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asInt();
	}

	public final float getFloat(String name) { return getFloat(name, 0); }
	public final float getFloat(String name, float def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asFloat();
	}

	public final double getDouble(String name) { return getDouble(name, 0); }
	public final double getDouble(String name, double def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asDouble();
	}

	public final long getLong(String name) { return getLong(name, 0); }
	public final long getLong(String name, long def) {
		AnnVal av = values.get(name);
		if (av == null) return def;
		return av.asLong();
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
		if (av == null) return Collections.emptyList();
		return av.asArray();
	}

	public int[] getIntArray(String name) {
		AnnVal av = values.get(name);
		if (av == null) return null;
		List<AnnVal> vals = av.asArray();
		int[] arr = new int[vals.size()];
		for (int i = 0; i < vals.size(); i++)
			arr[i] = vals.get(i).asInt();
		return arr;
	}

	public String[] getStringArray(String name) {
		AnnVal av = values.get(name);
		if (av == null) return null;
		List<AnnVal> vals = av.asArray();
		String[] arr = new String[vals.size()];
		for (int i = 0; i < vals.size(); i++)
			arr[i] = vals.get(i).asString();
		return arr;
	}

	public final boolean containsKey(String name) {
		return values.containsKey(name);
	}

	public final void put(String name, AnnVal av) {
		if (values == Collections.EMPTY_MAP) values = new LinkedMyHashMap<>();
		values.put(name, av);
	}

	public static Annotation parse(ConstantPool pool, DynByteBuf r) {
		String type = ((CstUTF) pool.get(r)).str();
		int len = r.readUnsignedShort();

		Map<String, AnnVal> params;
		if (len > 0) {
			params = new LinkedMyHashMap<>(len);
			while (len-- > 0) {
				params.put(((CstUTF) pool.get(r)).str(), AnnVal.parse(pool, r));
			}
		} else {
			params = Collections.emptyMap();
		}

		return new Annotation(type, params);
	}

	public void toByteArray(ConstantPool pool, DynByteBuf w) {
		CharList sb = IOUtil.ddLayeredCharBuf().append('L').append(type).append(';');
		w.putShort(pool.getUtfId(sb)).putShort(values.size());
		sb._free();
		for (Map.Entry<String, AnnVal> e : values.entrySet()) {
			e.getValue().toByteArray(pool, w.putShort(pool.getUtfId(e.getKey())));
		}
	}

	public String toString() {
		CharList sb = new CharList().append('@');
		TypeHelper.toStringOptionalPackage(sb, type);
		if (!values.isEmpty()) {
			sb.append('(');
			if (values.size() == 1 && values.containsKey("value")) {
				sb.append(values.get("value"));
			} else {
				for (Iterator<Map.Entry<String, AnnVal>> itr = values.entrySet().iterator(); itr.hasNext(); ) {
					Map.Entry<String, AnnVal> e = itr.next();
					sb.append(e.getKey()).append(" = ").append(e.getValue());
					if (!itr.hasNext()) break;
					sb.append(", ");
				}
			}
			sb.append(')');
		}
		return sb.toStringAndFree();
	}
}