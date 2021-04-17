package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.MethodParameters.MethodParam;
import roj.asm.type.*;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.collect.SimpleList;
import roj.util.DynByteBuf;
import roj.util.TypedName;

import java.util.List;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class Method implements MethodNode {
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
		this.access = method.access;
		this.owner = data.name;
		this.name = method.name.str();
		this.rawDesc = method.type.str();

		AttributeList al = method.attributesNullable();
		if (al != null && !al.isEmpty()) {
			attributes = new AttributeList(al);
			Parser.parseAttributes(this, data.cp, attributes, Signature.METHOD);
		}

		if ((attrByName("Code") == null) == (0 == (access & (AccessFlag.ABSTRACT | AccessFlag.NATIVE)))) {
			System.err.println("Method.java:52: Non-abstract " + data.name + '.' + name + rawDesc + " missing Code.");
		}
	}

	public Method(java.lang.reflect.Method m) {
		name = m.getName();
		access = (char) m.getModifiers();
		rawDesc = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());
		owner = m.getDeclaringClass().getName().replace('.', '/');
	}

	public String owner;

	public String name;
	public char access;
	private String rawDesc;
	private SimpleList<Type> params;
	private Type returnType;

	private AttributeList attributes;

	public void toByteArray(DynByteBuf w, ConstantPool pool) {
		w.putShort(access).putShort(pool.getUtfId(name)).putShort(pool.getUtfId(rawDesc()));

		if (attributes == null) {
			w.putShort(0);
			return;
		}
		attributes.toByteArray(w, pool);
	}

	RawMethod i_downgrade(ConstantPool cw) {
		RawMethod m = new RawMethod(access, cw.getUtf(name), cw.getUtf(rawDesc()));
		m.owner = owner;
		if (params != null) m.params = params;

		if (attributes == null) return m;

		AttributeList lowAttrs = m.attributes();

		AttributeList attrs = attributes();
		lowAttrs.ensureCapacity(attrs.size());

		DynByteBuf w = AsmShared.getBuf();
		for (int i = 0; i < attrs.size(); i++) {
			lowAttrs.add(AttrUnknown.downgrade(cw, w, attrs.get(i)));
		}
		return m;
	}

	@Override
	public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedName<T> type) {
		return Parser.parseAttribute(this, cp, type, attributes, Signature.METHOD);
	}

	public Attribute attrByName(String name) { return attributes == null ? null : (Attribute) attributes.getByName(name); }
	public AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	public AttributeList attributesNullable() { return attributes; }

	@Override
	public String ownerClass() { return owner; }

	public String name() { return name; }
	public void name(ConstantPool cp, String name) { this.name = Objects.requireNonNull(name); }

	public String rawDesc() {
		if (params != null) {
			params.add(returnType);
			rawDesc = TypeHelper.getMethod(params, rawDesc);
			params.remove(params.size() - 1);
		} else {
			assert rawDesc != null;
		}
		return rawDesc;
	}
	public void rawDesc(ConstantPool cp, String desc) {
		rawDesc = Objects.requireNonNull(desc);
		if (params != null) {
			params.clear();
			TypeHelper.parseMethod(desc,params);
			returnType = params.remove(params.size()-1);
		}
	}

	public List<Type> parameters() { initPar(); return params; }
	public Type returnType() {
		return returnType == null ? returnType = TypeHelper.parseReturn(rawDesc) : returnType;
	}
	public void setReturnType(Type ret) { initPar(); returnType = ret; }

	public char modifier() { return access; }
	public void modifier(int flag) { access = (char) flag; }

	public int type() { return Parser.MTYPE_FULL; }

	private void initPar() {
		if (params == null) {
			if (rawDesc == null) throw new IllegalStateException("rawDesc和desc均为null");
			params = (SimpleList<Type>) TypeHelper.parseMethod(rawDesc);
			returnType = params.remove(params.size()-1);
		}
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		Attribute a;

		a = parsedAttr(null, Attribute.RtAnnotations);
		if (a != null) sb.append("    ").append(a).append('\n');
		a = parsedAttr(null, Attribute.ClAnnotations);
		if (a != null) sb.append("    ").append(a).append('\n');

		AccessFlag.toString(access, AccessFlag.TS_METHOD, sb.append("    "));
		initPar();

		Signature sig = parsedAttr(null, Attribute.SIGNATURE);
		if (sig != null) {
			sb.append(sig.values.get(sig.values.size() - 1));
		} else {
			sb.append(returnType);
		}
		sb.append(' ').append(name).append('(');

		if (params.size() > 0) {
			MethodParameters acc = parsedAttr(null, Attribute.MethodParameters);
			if (acc != null) {
				AttrCode code = getCode();
				List<LocalVariable> list = code != null && code.getLVT() != null ? code.getLVT().list : null;
				for (int j = 0; j < params.size(); j++) {
					IType p = sig != null && sig.values.size() > j ? sig.values.get(j) : params.get(j);
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

		AttrStringList exceptions = parsedAttr(null, Attribute.Exceptions);
		if (exceptions != null) {
			sb.append(" throws ");
			List<String> classes = exceptions.classes;
			for (int i = 0; i < classes.size(); i++) {
				String clz = classes.get(i);
				sb.append(clz.substring(clz.lastIndexOf('/') + 1)).append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
		}

		Object code = attrByName("Code");
		if (code != null && (!(code instanceof AttrCode) || !((AttrCode) code).instructions.isEmpty()))
			sb.append("\n      代码:\n").append(code);

		return sb.toString();
	}

	@Deprecated
	public AttrCode getCode() {
		Object code = attrByName("Code");
		if (!(code instanceof AttrCode) && code != null) {
			new IllegalStateException(code.toString()).printStackTrace();
			return null;
		}
		return (AttrCode) code;
	}

	@Deprecated
	public AttrCode setCode(AttrCode code) {
		putAttr(code);
		return code;
	}
}