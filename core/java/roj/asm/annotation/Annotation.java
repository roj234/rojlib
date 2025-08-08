package roj.asm.annotation;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import roj.asm.Attributed;
import roj.asm.attr.Attribute;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.LinkedHashMap;
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

	public Annotation() {super(new LinkedHashMap<>());}
	public Annotation(String type) {
		this();
		this.type = type;
	}
	public Annotation(String type, Map<String, CEntry> values) {
		super(values);
		this.type = type;
	}

	@Contract(pure = true)
	public String type() {
		if (type.endsWith(";")) type = type.substring(1, type.length()-1);
		return type;
	}
	public void setType(String type) { this.type = type; }

	public final String getEnumValue(String name, String def) {
		var av = getNullable(name);
		if (av == null || av.dataType() != AnnVal.ENUM) return def;
		return ((AEnum) av).field;
	}

	public final Type getClass(String name) {
		var av = getNullable(name);
		if (av == null || av.dataType() != AnnVal.ANNOTATION_CLASS) return Helpers.maybeNull();
		return ((AClass) av).value;
	}

	public final Annotation getAnnotation(String name) {
		var av = getNullable(name);
		if (av == null) return Helpers.maybeNull();
		return ((Annotation) av);
	}

	@NotNull
	public final AList getList(String name) {return (AList) properties.getOrDefault(name, AList.EMPTY);}

	@Deprecated public int[] getIntArray(String name) {return getList(name).toIntArray();}
	@Deprecated public String[] getStringArray(String name) {return getList(name).toStringArray();}

	@Override
	protected CEntry put(String k, CEntry v, int flag) {
		if (properties == Collections.EMPTY_MAP) properties = new LinkedHashMap<>();
		return super.put(k, v, flag);
	}

	public static Annotation parse(ConstantPool pool, DynByteBuf r) {
		String type = ((CstUTF) pool.get(r)).str();
		if (!type.endsWith(";")) throw new IllegalArgumentException("无效的注解类型:"+type);
		int len = r.readUnsignedShort();

		Map<String, CEntry> params;
		if (len > 0) {
			params = new LinkedHashMap<>(len);
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
	public void accept(CVisitor visitor) {
		((ToJVMAnnotation) visitor).valueAnnotation(type);
		super.accept(visitor);
	}

	public String toString() {
		CharList sb = new CharList().append('@');
		TypeHelper.toStringOptionalPackage(sb, type());
		if (!properties.isEmpty()) {
			sb.append('(');
			if (properties.size() == 1 && properties.containsKey("value")) {
				sb.append(properties.get("value"));
			} else {
				for (var itr = properties.entrySet().iterator(); itr.hasNext(); ) {
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
		return properties.equals(that.properties);
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + properties.hashCode();
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
		var attr = node.getAttribute(cp, Attribute.ClAnnotations);
		if (attr != null) {
			var list = attr.annotations;
			for (int i = 0; i < list.size(); i++) {
				var a = list.get(i);
				if (a.type().equals(type)) return a;
			}
		}
		return null;
	}

	public static Annotation findVisible(ConstantPool cp, Attributed node, String type) {
		var attr = node.getAttribute(cp, Attribute.RtAnnotations);
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