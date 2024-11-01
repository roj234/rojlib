package roj.asm.tree.anno;

import org.jetbrains.annotations.NotNull;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.tree.Attributed;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.LinkedMyHashMap;
import roj.collect.MyHashMap;
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
	private String type;
	public Map<String, AnnVal> values;

	public Annotation() { values = new MyHashMap<>(); }
	public Annotation(String type, Map<String, AnnVal> values) {
		this.type = type;
		this.values = values;
	}

	public String type() {
		if (type.endsWith(";")) type = type.substring(1, type.length()-1);
		return type;
	}
	public void setType(String type) { this.type = type; }

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
		return av.asEnum().field;
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

	public final boolean containsKey(String name) { return values.containsKey(name); }
	public final void put(String name, AnnVal av) {
		if (values == Collections.EMPTY_MAP) values = new LinkedMyHashMap<>();
		values.put(name, av);
	}

	public static Annotation parse(ConstantPool pool, DynByteBuf r) {
		String type = ((CstUTF) pool.get(r)).str();
		if (!type.endsWith(";")) throw new IllegalArgumentException("无效的注解类型:"+type);
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
		int id;
		if (type.endsWith(";")) {
			id = pool.getUtfId(type);
		} else {
			CharList sb = new CharList().append('L').append(type).append(';');
			id = pool.getUtfId(sb);
			sb._free();
		}

		w.putShort(id).putShort(values.size());
		for (Map.Entry<String, AnnVal> e : values.entrySet())
			e.getValue().toByteArray(pool, w.putShort(pool.getUtfId(e.getKey())));
	}

	public String toString() {
		CharList sb = new CharList().append('@');
		TypeHelper.toStringOptionalPackage(sb, type());
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

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Annotation that)) return false;

		if (!type.equals(that.type)) return false;
		return values.equals(that.values);
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + values.hashCode();
		return result;
	}

	@NotNull
	public static List<Annotation> getAnnotations(ConstantPool cp, Attributed node, boolean vis) {
		var attr = node.parsedAttr(cp, vis?Attribute.RtAnnotations:Attribute.ClAnnotations);
		return attr == null ? Collections.emptyList() : attr.annotations;
	}

	public static Annotation find(List<Annotation> list, String type) {
		for (int i = 0; i < list.size(); i++) {
			var a = list.get(i);
			if (a.type().equals(type)) return a;
		}
		return null;
	}

	public static Annotation findInvisible(ConstantPool cp, Attributed node, String type) {
		var attr = node.parsedAttr(cp, Attribute.ClAnnotations);
		if (attr != null) {
			var list = attr.annotations;
			for (int i = 0; i < list.size(); i++) {
				var a = list.get(i);
				if (a.type().equals(type)) return a;
			}
		}
		return null;
	}
}