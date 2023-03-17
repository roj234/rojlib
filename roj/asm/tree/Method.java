package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.MethodParameters.MethodParam;
import roj.asm.type.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;

import javax.annotation.Nullable;
import java.util.List;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Method implements MethodNode, AttributeReader {
	public Method(int access, IClass owner, String name, String desc) {
		this.access = (char) access;
		this.owner = owner.name();
		this.name = name;
		this.rawDesc = desc;
	}

	public Method(int access, String owner, String name, String desc) {
		this.access = (char) access;
		this.owner = owner;
		this.name = name;
		this.rawDesc = desc;
	}

	public Method(ConstantData data, RawMethod method) {
		this.access = method.accesses;
		this.owner = data.name;
		this.name = method.name.getString();
		this.rawDesc = method.type.getString();

		boolean hasCode = false;

		AttributeList al = method.attributesNullable();
		if (al != null && !al.isEmpty()) {
			attributes = new AttributeList(al);
			hasCode = attributes.getByName("Code") != null;

			Parser.withParsedAttribute(data.cp, this);
		}

		if (!hasCode && 0 == (access & (AccessFlag.ABSTRACT | AccessFlag.NATIVE))) {
			System.err.println("Method.java:52: Non-abstract " + data.name + '.' + name + rawDesc + " missing Code.");
		}
	}

	public Method(java.lang.reflect.Method m) {
		name = m.getName();
		access = (char) m.getModifiers();
		rawDesc = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());
		owner = m.getDeclaringClass().getName().replace('.', '/');
	}

	@Override
	public Attribute parseAttribute(ConstantPool pool, DynByteBuf r, String name, int length) {
		switch (name) {
			case "RuntimeVisibleTypeAnnotations":
			case "RuntimeInvisibleTypeAnnotations":
				return new TypeAnnotations(name, r, pool);
			// 方法注解
			case "RuntimeVisibleAnnotations":
			case "RuntimeInvisibleAnnotations":
				return new Annotations(name, r, pool);
			// 参数注解
			case "RuntimeVisibleParameterAnnotations":
			case "RuntimeInvisibleParameterAnnotations":
				return new ParameterAnnotations(name, r, pool);
			// 泛型签名
			case "Signature":
				return Signature.parse(((CstUTF) pool.get(r)).getString(), Signature.METHOD);
			// 显示方法参数的标识符
			case "MethodParameters":
				return new MethodParameters(r, pool);
			case "Exceptions":
				return new AttrStringList(name, r, pool, 0);
			case "AnnotationDefault":
				return new AnnotationDefault(r, pool);
			case "Code":
				return code = new AttrCode(this, r, pool);
			case "Synthetic":
			case "Deprecated":
			default: return length < 0 ? null : new AttrUnknown(name, r.slice(length));
		}
	}

	public String owner;

	public String name;
	public char access;
	private String rawDesc;
	private List<Type> params;
	private Type returnType;

	private AttributeList attributes;

	public AttrCode code;

	RawMethod i_downgrade(ConstantPool cw) {
		if (params != null) {
			params.add(returnType);
			rawDesc = TypeHelper.getMethod(params, rawDesc);
			params.remove(params.size() - 1);
		}
		RawMethod m = new RawMethod(access, cw.getUtf(name), cw.getUtf(rawDesc));
		m.owner = owner;
		if (params != null) m.params = params;

		if (attributes == null && code == null) return m;

		AttributeList lowAttrs = m.attributes();

		AttributeList attrs = attributes();
		if (code != null) attrs.add(code);

		lowAttrs.ensureCapacity(attrs.size());

		DynByteBuf w = AsmShared.getBuf();
		for (int i = 0; i < attrs.size(); i++) {
			lowAttrs.add(AttrUnknown.downgrade(cw, w, attrs.get(i)));
		}
		return m;
	}

	@Override
	public Attribute attrByName(String name) {
		return attributes == null ? null : (Attribute) attributes.getByName(name);
	}

	@Override
	public AttributeList attributes() {
		return attributes == null ? attributes = new AttributeList() : attributes;
	}

	@Nullable
	@Override
	public AttributeList attributesNullable() {
		return attributes;
	}

	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		if (params != null) {
			params.add(returnType);
			rawDesc = TypeHelper.getMethod(params, rawDesc);
			params.remove(params.size() - 1);
		}
		w.putShort(access).putShort(pool.getUtfId(name)).putShort(pool.getUtfId(rawDesc));

		if (code != null) attributes().add(code);

		if (attributes == null) {
			w.putShort(0);
			return;
		}
		attributes.toByteArray(w, pool);
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		if (getAnnotations() != null) sb.append("    ").append(getAnnotations()).append('\n');
		if (getInvisibleAnnotations() != null) sb.append("    ").append(getInvisibleAnnotations()).append('\n');

		sb.append("    ");
		AccessFlag.toString(access, AccessFlag.TS_METHOD, sb);
		initPar();
		Attribute a = attrByName("Signature");
		Signature signature = a instanceof Signature ? (Signature) a : null;
		if (signature != null) {
			sb.append(signature.values.get(signature.values.size() - 1));
		} else {
			sb.append(returnType);
		}
		sb.append(' ').append(name).append('(');

		if (params.size() > 0) {
			MethodParameters acc = getParameterAccesses();
			if (acc != null) {
				List<LocalVariable> list = code != null && code.getLVT() != null ? code.getLVT().list : null;
				for (int j = 0; j < params.size(); j++) {
					IType p = signature != null && signature.values.size() > j ? signature.values.get(j) : params.get(j);
					MethodParam e = acc.flags.size() <= j ? null : acc.flags.get(j);
					String name = list == null ? e == null ? "<unknown>" : e.name : list.get(j).name;

					if (e != null) {
						AccessFlag.toString(e.flag, AccessFlag.TS_PARAM, sb);
					}
					sb.append(p).append(' ').append(name).append(", ");
				}
			} else {
				for (int i = 0; i < params.size(); i++) {
					Type p = params.get(i);
					sb.append(p).append(", ");
				}
			}

			sb.delete(sb.length() - 2, sb.length());
		}

		sb.append(')');

		AttrStringList throwsEx = getThrows();
		if (throwsEx != null) {
			sb.append(" throws ");
			List<String> classes = throwsEx.classes;
			for (int i = 0; i < classes.size(); i++) {
				String clz = classes.get(i);
				sb.append(clz.substring(clz.lastIndexOf('/') + 1)).append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
		}
		if (code != null && !code.instructions.isEmpty()) sb.append("\n      Code: \n").append(code);
		if (attributes != null) {
			boolean pr = false;
			for (int i = 0; i < attributes.size(); i++) {
				Attribute attr = attributes.get(i);
				switch (attr.name) {
					case "Code":
					case "Signature":
					case MethodParameters.NAME:
					case Annotations.INVISIBLE:
					case Annotations.VISIBLE:
					case "Exceptions": continue;
				}
				if (!pr) {
					sb.append("\n      Attributes: \n");
					pr = true;
				}
				sb.append("         ").append(attr).append('\n');
			}
		}
		return sb.toString();
	}

	@Override
	public String ownerClass() {
		return owner;
	}

	@Override
	public String name() {
		return name;
	}

	@Override
	public List<Type> parameters() {
		initPar();
		return params;
	}

	@Override
	public Type getReturnType() {
		if (returnType == null) {
			if (rawDesc == null) {initPar();} else return returnType = TypeHelper.parseReturn(rawDesc);
		}
		return returnType;
	}

	public void setReturnType(Type returnType) {
		initPar();
		this.returnType = returnType;
	}

	private void initPar() {
		if (params == null) {
			if (rawDesc == null) { // fallback
				params = new SimpleList<>();
				returnType = Type.std(Type.VOID);
				return;
			}
			params = TypeHelper.parseMethod(rawDesc);
			returnType = params.remove(params.size() - 1);
		}
	}

	public String rawDesc() {
		if (this.rawDesc == null) {
			if (params != null) {
				params.add(returnType);
				String v = TypeHelper.getMethod(params);
				params.remove(params.size() - 1);
				return v;
			}
		}
		return this.rawDesc;
	}

	@Override
	public void name(ConstantPool cp, String name) {
		this.name = name;
	}

	public void rawDesc(ConstantPool cp, String param) {
		this.rawDesc = param;
		if (params != null) {
			params.clear();
			TypeHelper.parseMethod(param, params);
			returnType = params.remove(params.size() - 1);
		}
	}

	@Override
	public int type() {
		return Parser.MTYPE_FULL;
	}

	@Override
	public void accessFlag(int flag) {
		this.access = (char) flag;
	}

	@Override
	public char accessFlag() {
		return access;
	}

	public Annotations getAnnotations() {
		return attributes == null ? null : (Annotations) attributes.getByName(Annotations.VISIBLE);
	}

	public Annotations getInvisibleAnnotations() {
		return attributes == null ? null : (Annotations) attributes.getByName(Annotations.INVISIBLE);
	}

	public MethodParameters getParameterAccesses() {
		return attributes == null ? null : (MethodParameters) attributes.getByName(MethodParameters.NAME);
	}

	public AttrStringList getThrows() {
		return attributes == null ? null : (AttrStringList) attributes.getByName(AttrStringList.EXCEPTIONS);
	}
}