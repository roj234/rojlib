package roj.asm.annotation;

import org.jetbrains.annotations.NotNull;
import roj.asm.Attributed;
import roj.asm.attr.Attribute;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.LinkedMyHashMap;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.config.serial.CVisitor;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Map;


/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public class Annotation extends CMap {
	private String type;

	public Annotation() {super(new LinkedMyHashMap<>());}
	public Annotation(String type) {
		this();
		this.type = type;
	}
	public Annotation(String type, Map<String, CEntry> values) {
		super(values);
		this.type = type;
	}

	public String type() {
		if (type.endsWith(";")) type = type.substring(1, type.length()-1);
		return type;
	}
	public void setType(String type) { this.type = type; }

	public final String getEnumValue(String name, String def) {
		var av = getOr(name);
		if (av == null || av.dataType() != AnnVal.ENUM) return def;
		return ((AEnum) av).field;
	}

	public final Type getClass(String name) {
		var av = getOr(name);
		if (av == null || av.dataType() != AnnVal.ANNOTATION_CLASS) return Helpers.nonnull();
		return ((AClass) av).value;
	}

	public final Annotation getAnnotation(String name) {
		var av = getOr(name);
		if (av == null) return Helpers.nonnull();
		return ((Annotation) av);
	}

	@NotNull
	public final AList getArray(String name) {return (AList) map.getOrDefault(name, AList.EMPTY);}

	@Deprecated public int[] getIntArray(String name) {return getArray(name).toIntArray();}
	@Deprecated public String[] getStringArray(String name) {return getArray(name).toStringArray();}

	@Override
	protected CEntry put1(String k, CEntry v, int f) {
		if (map == Collections.EMPTY_MAP) map = new LinkedMyHashMap<>();
		return super.put1(k, v, f);
	}

	public static Annotation parse(ConstantPool pool, DynByteBuf r) {
		String type = ((CstUTF) pool.get(r)).str();
		if (!type.endsWith(";")) throw new IllegalArgumentException("无效的注解类型:"+type);
		int len = r.readUnsignedShort();

		Map<String, CEntry> params;
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

	public char dataType() { return AnnVal.ANNOTATION; }
	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		var serializer = new ToJVMAnnotation();
		serializer.init(pool, w);
		w.putShort(serializer.getTypeId(type));
		super.accept(serializer);
	}

	@Override
	public void accept(CVisitor ser) {
		((ToJVMAnnotation) ser).valueAnnotation(type);
		super.accept(ser);
	}

	public String toString() {
		CharList sb = new CharList().append('@');
		TypeHelper.toStringOptionalPackage(sb, type());
		if (!map.isEmpty()) {
			sb.append('(');
			if (map.size() == 1 && map.containsKey("value")) {
				sb.append(map.get("value"));
			} else {
				for (var itr = map.entrySet().iterator(); itr.hasNext(); ) {
					var entry = itr.next();
					sb.append(entry.getKey()).append(" = ").append(entry.getValue());
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
		return map.equals(that.map);
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + map.hashCode();
		return result;
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