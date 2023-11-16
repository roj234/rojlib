package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstUTF;
import roj.asm.tree.attr.*;
import roj.asm.tree.attr.MethodParameters.MethodParam;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.visitor.CodeVisitor;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.Label;
import roj.asm.visitor.XAttrCode;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.TypedName;

import java.util.List;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class MethodNode extends CNode {
	public MethodNode(int acc, String owner, String name, String desc) { this(acc, owner, (Object) name, desc); }
	public MethodNode(int acc, String owner, CstUTF name, CstUTF desc) { this(acc, owner, (Object) name, desc); }
	private MethodNode(int acc, String owner, Object name, Object type) {
		this.access = (char) acc;
		this.owner = owner;
		this.name = name;
		this.desc = type;
	}
	public MethodNode copyDesc() { return new MethodNode(access, owner, name, desc); }
	public MethodNode copy() {
		MethodNode inst = copyDesc();
		if (attributes != null) inst.attributes = new AttributeList(attributes);
		return inst;
	}

	public MethodNode(java.lang.reflect.Method m) {
		name = m.getName();
		access = (char) m.getModifiers();
		desc = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());
		owner = m.getDeclaringClass().getName().replace('.', '/');
	}

	// todo move to parameter
	@Deprecated
	public String owner;

	private List<Type> in;
	private Type out;

	public MethodNode parsed(ConstantPool cp) {
		if (attributes == null) return this;
		Parser.parseAttributes(this, cp, attributes, Signature.METHOD);

		if ((attrByName("Code") == null) == (0 == (access & (AccessFlag.ABSTRACT | AccessFlag.NATIVE)))) {
			System.err.println("Method.java:63: Non-abstract " + owner + '.' + name + desc + " missing Code.");
		}

		return this;
	}
	public MethodNode unparsed(ConstantPool cp) {
		if (attributes == null) return this;

		DynByteBuf w = AsmShared.getBuf();
		for (int i = 0; i < attributes.size(); i++) {
			attributes.set(i, AttrUnknown.downgrade(cp, w, attributes.get(i)));
		}
		return this;
	}

	public boolean forEachCode(CodeVisitor cv, ConstantPool cp) {
		Attribute code = attrByName("Code");
		if (code == null) return false;
		if (!(code instanceof AttrUnknown)) {
			code = AttrUnknown.downgrade(cp, new ByteList(), code);
			putAttr(code);
		}

		if (cv instanceof CodeWriter) {
			ByteList b = new ByteList();
			((CodeWriter) cv).init(b, cp, this, (byte) 0);
			cv.visit(cp, Parser.reader(code));
			((CodeWriter) cv).finish();
			((AttrUnknown) code).setRawData(b);
		} else {
			cv.visit(cp, Parser.reader(code));
		}
		return true;
	}

	@Override
	public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedName<T> type) { return Parser.parseAttribute(this, cp, type, attributes, Signature.METHOD); }

	@Deprecated
	public String ownerClass() { return owner; }

	public String rawDesc() {
		if (in != null) {
			in.add(out);
			desc = TypeHelper.getMethod(in, desc.getClass() == String.class ? desc.toString() : null);
			in.remove(in.size()-1);
			return desc.toString();
		}
		return desc.getClass() == CstUTF.class ? ((CstUTF) desc).str() : desc.toString();
	}
	public void rawDesc(String desc) {
		this.desc = Objects.requireNonNull(desc);
		in = null;
		out = null;
	}

	public List<Type> parameters() {
		if (in == null) {
			in = TypeHelper.parseMethod(rawDesc());
			out = in.remove(in.size()-1);
		}
		return in;
	}
	public Type returnType() { return out == null ? out = TypeHelper.parseReturn(rawDesc()) : out; }
	public void setReturnType(Type ret) { parameters(); out = ret; }

	public String toString() { return toString(new CharList(), null).toStringAndFree(); }
	public CharList toString(CharList sb, ConstantData owner) {
		ConstantPool cp = owner == null ? null : owner.cp;

		Attribute a;

		if (attrByName("Deprecated") != null) sb.append("@Deprecated").append('\n');
		a = parsedAttr(cp, Attribute.RtAnnotations);
		if (a != null) sb.append(a).append('\n');
		a = parsedAttr(cp, Attribute.ClAnnotations);
		if (a != null) sb.append(a).append('\n');

		if ((access&AccessFlag.ABSTRACT) == 0 && owner != null && (owner.access&AccessFlag.INTERFACE) != 0) sb.append("default ");
		AccessFlag.toString(access, AccessFlag.TS_METHOD, sb);
		if (attrByName("Synthetic") != null) sb.append("/*synthetic*/ ");

		if (!name().equals("<clinit>")) {
			parameters();

			Signature sig = parsedAttr(cp, Attribute.SIGNATURE);
			if (owner != null && name().equals("<init>")) {
				assert out.type == Type.VOID;
				TypeHelper.toStringOptionalPackage(sb, owner.name);
			} else {
				if (sig != null) sb.append(sig.values.get(sig.values.size() - 1));
				else sb.append(out);

				sb.append(' ').append(name());
			}
			sb.append('(');

			if (!in.isEmpty()) {
				MethodParameters acc = parsedAttr(cp, Attribute.MethodParameters);
				XAttrCode code = parsedAttr(cp, Attribute.Code);

				LocalVariableTable lvt = code != null ? (LocalVariableTable) code.attrByName("LocalVariableTable") : null;
				Label ZERO_READONLY = new Label(0);

				int slot = 0;
				for (int j = 0;;) {
					IType type = sig != null && sig.values.size() > j ? sig.values.get(j) : in.get(j);

					MethodParam name_a = acc == null || acc.flags.size() <= j ? null : acc.flags.get(j);
					LocalVariableTable.Item name_b = lvt == null ? null : lvt.getItem(ZERO_READONLY, slot);

					String name = null;
					if (name_a != null) {
						name = name_a.name;
						AccessFlag.toString(name_a.flag, AccessFlag.TS_PARAM, sb);
					}
					if (name_b != null) name = name_b.name;

					sb.append(type);
					if (name != null) sb.append(' ').append(name);
					if (++j == in.size()) break;
					sb.append(", ");
					slot += type.rawType().length();
				}
			}
			sb.append(')');
		} else {
			sb.setLength(sb.length()-1);
		}

		AttrClassList exceptions = parsedAttr(cp, Attribute.Exceptions);
		if (exceptions != null) {
			sb.append(" throws ");
			List<String> classes = exceptions.value;
			for (int i = 0; i < classes.size(); i++) {
				String clz = classes.get(i);
				sb.append(clz.substring(clz.lastIndexOf('/') + 1)).append(", ");
			}
			sb.delete(sb.length() - 2, sb.length());
		}

		AnnotationDefault def = parsedAttr(cp, Attribute.AnnotationDefault);
		if (def != null) sb.append(" default ").append(def.val);

		Object code = parsedAttr(cp, Attribute.Code);
		if (code != null) sb.append(" {\n").append(code).append('}');
		else sb.append(';');
		return sb;
	}
}