package roj.asm;

import org.jetbrains.annotations.NotNull;
import roj.RojLib;
import roj.asm.attr.*;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstUTF;
import roj.asm.insn.*;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.util.function.Flow;
import roj.text.CharList;
import roj.text.logging.Logger;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.util.List;
import java.util.Objects;

import static roj.asm.Opcodes.ACC_ABSTRACT;
import static roj.asm.Opcodes.ACC_NATIVE;

/**
 * @author Roj234
 * @since 2021/6/18 9:51
 */
public final class MethodNode extends MemberNode {
	public MethodNode(int acc, String owner, String name, String desc) { this(acc, owner, (Object) name, desc); }
	public MethodNode(int acc, String owner, CstUTF name, CstUTF desc) { this(acc, owner, (Object) name, desc); }
	private MethodNode(int acc, String owner, Object name, Object type) {
		this.modifier = (char) acc;
		this.owner = owner;
		this.name = name;
		this.desc = type;
	}
	public MethodNode(Member m) {
		modifier = m.modifier();
		owner = m.owner();
		name = m.name();
		desc = m.rawDesc();
	}

	public MethodNode(java.lang.reflect.Method m) {
		modifier = (char) m.getModifiers();
		owner = m.getDeclaringClass().getName().replace('.', '/');
		name = m.getName();
		desc = TypeHelper.class2asm(m.getParameterTypes(), m.getReturnType());
	}

	public MethodNode copyDesc() { return new MethodNode(modifier, owner, name, in != null ? rawDesc() : desc); }
	public MethodNode copy() {
		MethodNode inst = copyDesc();
		if (attributes != null) inst.attributes = new AttributeList(attributes);
		return inst;
	}

	// to [ConstantData | String] later
	String owner;
	public String owner() { return owner; }
	@Deprecated
	public void __setOwner(String owner) {this.owner = owner;}

	private List<Type> in;
	private Type out;

	public MethodNode parsed(ConstantPool cp) {
		if (RojLib.ASM_DEBUG && (getAttribute("Code") == null) == (0 == (modifier & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)))) {
			Logger.FALLBACK.warn("方法 "+owner+'.'+name+desc+" 缺少Code属性.");
		}

		if (attributes != null) {
			Attribute.parseAll(this, cp, attributes, Signature.METHOD);
		}
		return this;
	}

	public boolean transform(ConstantPool cp, ByteList tmp, CodeVisitor cv) {
		var code = getAttribute(cp, "Code");
		if (code == null) return false;

		if (cv instanceof CodeWriter cw) {
			tmp.clear();
			cw.init(tmp, cp, this);
			cw.visit(cp, code.getRawData());
			cw.finish();
			code.setRawData(DynByteBuf.wrap(tmp.toByteArray()));
		} else {
			cv.visit(cp, code.getRawData().slice());
		}
		return true;
	}

	public CodeWriter overwrite(ConstantPool cp) {
		if ((modifier & (ACC_ABSTRACT|ACC_NATIVE)) != 0) throw new IllegalStateException(this+" cannot have Code attribute");
		var code = new AttrCodeWriter(cp, this);
		addAttribute(code);
		return code.cw;
	}

	@Override
	public <T extends Attribute> T getAttribute(ConstantPool cp, TypedKey<T> type) { return Attribute.parseSingle(this, cp, type, attributes, Signature.METHOD); }

	@NotNull
	public final Signature getSignature(ConstantPool cp) {
		Signature signature = getAttribute(cp, Attribute.SIGNATURE);
		if (signature == null) {
			signature = new Signature(Signature.METHOD);
			ClassListAttribute exceptions = getAttribute(cp, Attribute.Exceptions);
			if (exceptions != null)
				signature.exceptions = Helpers.cast(Flow.of(exceptions.value).map(Type::klass).toList());
			signature.values = Helpers.cast(Type.methodDesc(rawDesc()));
			addAttribute(signature);
		}
		return signature;
	}

	public String rawDesc() {
		if (in != null) {
			desc = Type.toMethodDesc(in, out, desc.getClass() == String.class ? desc.toString() : null);
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
			ArrayList<Type> in = AsmCache.getInstance().methodTypeTmp();
			out = Type.methodDesc(rawDesc(), in);
			this.in = new ArrayList<>(in);
		}
		return in;
	}
	public Type returnType() { return out == null ? out = Type.methodDescReturn(rawDesc()) : out; }
	public void setReturnType(Type ret) { parameters(); out = ret; }

	public String toString() { return toString(new CharList(), null, 0).toStringAndFree(); }
	public CharList toString(CharList sb, ClassNode owner, int prefix) {
		ConstantPool cp = owner == null ? null : owner.cp;

		Annotations a;

		if (getAttribute("Deprecated") != null) sb.padEnd(' ', prefix).append("@Deprecated").append('\n');
		a = getAttribute(cp, Attribute.RtAnnotations);
		if (a != null) a.toString(sb, prefix);
		a = getAttribute(cp, Attribute.ClAnnotations);
		if (a != null) a.toString(sb, prefix);

		sb.padEnd(' ', prefix);
		if ((modifier&Opcodes.ACC_ABSTRACT) == 0 && owner != null && (owner.modifier&(Opcodes.ACC_INTERFACE|Opcodes.ACC_ANNOTATION)) == Opcodes.ACC_INTERFACE) sb.append("default ");
		Opcodes.showModifiers(modifier, Opcodes.ACC_SHOW_METHOD, sb);
		if (getAttribute("Synthetic") != null) sb.append("/*synthetic*/ ");

		if (!name().equals("<clinit>")) {
			parameters();

			Signature sig = getAttribute(cp, Attribute.SIGNATURE);
			if (owner != null && name().equals("<init>")) {
				assert out.type == Type.VOID;
				TypeHelper.toStringOptionalPackage(sb, owner.name());
			} else {
				if (sig != null) sb.append(sig.values.get(sig.values.size() - 1));
				else sb.append(out);

				sb.append(' ').append(name());
			}
			sb.append('(');

			if (!in.isEmpty()) {
				var modifiers = getAttribute(cp, Attribute.MethodParameters);
				// 警告: 规范不要求注解必须按descriptor排序，编译器可以让第0个表示用户显式提供的参数，而不是标识符的第0个
				//https://docs.oracle.com/javase/specs/jvms/se17/html/jvms-4.html#jvms-4.7.18
				var a1 = getAttribute(cp, Attribute.ClParameterAnnotations);
				var a2 = getAttribute(cp, Attribute.RtParameterAnnotations);
				Code code;
				try {
					code = getAttribute(cp, Attribute.Code);
				} catch (ClassCastException e) {
					code = null;
				}

				LocalVariableTable lvt = code != null ? (LocalVariableTable) code.getAttribute("LocalVariableTable") : null;
				Label ZERO_READONLY = Label.atZero();

				int slot = (modifier&Opcodes.ACC_STATIC) == 0 ? 1 : 0;
				for (int j = 0;;) {
					IType type = sig != null && sig.values.size() > j ? sig.values.get(j) : in.get(j);

					String name = null;

					var name_a = modifiers == null || modifiers.flags.size() <= j ? null : modifiers.flags.get(j);
					if (name_a != null) {
						name = name_a.name;
						Opcodes.showModifiers(name_a.flag, Opcodes.ACC_SHOW_PARAM, sb);
					}

					var name_b = lvt == null ? null : lvt.getItem(ZERO_READONLY, slot);
					if (name_b != null) name = name_b.name;

					if (a1 != null && a1.annotations.size() > j) {
						var list = a1.annotations.get(j);
						for (int i = 0; i < list.size(); i++) {
							sb.append(list.get(i)).append(' ');
						}
					}
					if (a2 != null && a2.annotations.size() > j) {
						var list = a2.annotations.get(j);
						for (int i = 0; i < list.size(); i++) {
							sb.append(list.get(i)).append(' ');
						}
					}

					sb.append(type);
					if (name != null) sb.append(' ').append(name);
					if (++j == in.size()) break;
					sb.append(", ");
					slot += type.genericType() == IType.TYPE_PARAMETER_TYPE ? 1 : type.rawType().length();
				}
			}
			sb.append(')');
		} else {
			sb.setLength(sb.length()-1);
		}

		ClassListAttribute exceptions = getAttribute(cp, Attribute.Exceptions);
		if (exceptions != null) {
			sb.append(" throws ");
			List<String> classes = exceptions.value;
			if (!classes.isEmpty()) for (int i = 0;;) {
				String clz = classes.get(i);
				TypeHelper.toStringOptionalPackage(sb, clz);
				if (++i == classes.size()) break;
				sb.append(", ");
			}
		}

		AnnotationDefault def = getAttribute(cp, Attribute.AnnotationDefault);
		if (def != null) sb.append(" default ").append(def.val);

		try {
			if (getAttribute("Code") instanceof AttrCodeWriter cw) {
				sb.append(" {\n").padEnd(' ', prefix+4).append("<complex ").append(cw.cw).append(">\n").padEnd(' ', prefix).append('}');
			} else {
				Code code = getAttribute(cp, Attribute.Code);
				if (code != null) code.toString(sb.append(" {\n"), prefix+4).padEnd(' ', prefix).append('}');
				else sb.append(';');
			}
		} catch (Exception e) {
			sb.append(e);
		}
		return sb;
	}

	public Code getCode(ConstantPool cp) {
		AttributeList list = attributes;
		if (list == null) return null;
		Object code = list.getByName("Code");
		if (code instanceof AttrCodeWriter acw) {
			var attr = UnparsedAttribute.serialize(cp, AsmCache.buf(), acw);
			list.add(attr);
			code = attr;
		}
		if (code instanceof Code code1) return code1;
		return getAttribute(cp, Attribute.Code);
	}
}