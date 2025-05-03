package roj.asmx;

import roj.asm.ClassNode;
import roj.asm.FieldNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.attr.Attribute;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.Constant;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstUTF;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Type;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * 泛型模板v1·Legacy
 * @author Roj234
 * @since 2023/3/19 20:37
 */
public class GenericTemplate extends CodeWriter {
	private final int templateType;
	private final Type internalType, methodType, callingType;
	private final boolean replaceCallingName;

	public static byte changeCodeType(int code, Type from, Type to) {
		int flag = flag(code);
		if ((flag&TRAIT_ILFDA) == 0) return (byte) code;

		String name = showOpcode(code);
		if ((flag&0xF) == CATE_ARRAY_SL)
			return (byte) ((name.endsWith("Store") ? 33 : 0)+ArrayLoad(to));

		if (from != null && name.charAt(0) != from.opcodePrefix().charAt(0)) return (byte) code;

		CharList sb = IOUtil.getSharedCharBuf().append(name);
		sb.list[0] = to.opcodePrefix().charAt(0);

		int v = Opcodes.opcodeByName().getOrDefault(sb, -1);
		if (v < 0) throw new IllegalStateException("找不到"+sb);
		return (byte) v;
	}

	public GenericTemplate(int templateType, Type internalType, Type methodType, Type callingType, boolean replaceCallingName) {
		this.templateType = templateType;
		this.internalType = internalType;
		this.methodType = methodType;
		this.callingType = callingType;
		this.replaceCallingName = replaceCallingName;
	}

	private ClassNode curr;
	public ClassNode generate(DynByteBuf data) {
		ClassNode d = curr = ClassNode.parseSkeleton(data);
		d.version = 49;

		List<Constant> list = d.cp.data();
		for (int i = 0; i < list.size(); i++) {
			Constant c = list.get(i);
			if (c.type() == Constant.CLASS) {
				CstUTF ref = ((CstClass) c).name();
				d.cp.setUTFValue(ref, replaceArrayClass(ref.str()));
			}/* else if (c.type() == Constant.NAME_AND_TYPE) {
				CstUTF ref = ((CstNameAndType) c).getType();
				d.cp.setUTFValue(ref, replaceDesc(ref.getString()));
			}*/
		}

		ByteList tmp = IOUtil.getSharedByteBuf();

		SimpleList<MethodNode> methods = d.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			mn.rawDesc(replaceDesc(mn.rawDesc(), methodType));

			Attribute buf = mn.getRawAttribute("Code");
			if (buf != null) {
				tmp.clear();
				init(tmp, d.cp, mn);
				visitCopied(d.cp, buf.getRawData());
				mn.addAttribute(new UnparsedAttribute("Code", tmp.toByteArray()));
			}
		}

		SimpleList<FieldNode> fields = d.fields;
		for (int i = 0; i < fields.size(); i++) {
			FieldNode fn = fields.get(i);
			fn.rawDesc(replaceDesc(fn.rawDesc(), internalType));
		}
		return d;
	}

	@Override
	public void newArray(byte arrayType) {
		var b = FromPrimitiveArrayId(arrayType);
		if (b == templateType) {
			if (internalType.isPrimitive()) {
				arrayType = ToPrimitiveArrayId(internalType.type);
			} else {
				super.clazz(Opcodes.ANEWARRAY, internalType.getActualClass());
				return;
			}
		}
		super.newArray(arrayType);
	}

	public void multiArray(String clz, int dimension) { super.multiArray(replaceArrayClass(clz), dimension); }
	public void clazz(byte code, String clz) { super.clazz(code, replaceArrayClass(clz)); }
	public void field(byte code, String owner, String name, String type) { super.field(code, owner, name, replaceDesc(type, internalType)); }
	public void invoke(byte code, String owner, String name, String desc, boolean isInterfaceMethod) {
		if (owner.equals(curr.name())) {
			desc = replaceDesc(desc, internalType);
		} else {
			String desc1 = replaceDesc(desc, callingType);
			if (!desc1.equals(desc) && replaceCallingName) {
				name = replaceName(name,templateType,callingType);
			}
			desc = desc1;
		}
		super.invoke(code, owner, name, desc, isInterfaceMethod);
	}
	public void invokeItf(String owner, String name, String desc) {
		String desc1 = replaceDesc(desc, callingType);
		if (!desc1.equals(desc) && replaceCallingName) {
			name = replaceName(name,templateType,callingType);
		}
		super.invokeItf(owner, name, desc1);
	}

	protected String replaceName(String name, int from, Type to) {
		CharList sb = IOUtil.getSharedCharBuf().append(name);
		String frn = Type.getName(from);
		String ton = Type.getName(to.type);
		sb.replace(frn,ton);
		sb.replace(Character.toUpperCase(frn.charAt(0))+frn.substring(1),Character.toUpperCase(ton.charAt(0))+ton.substring(1));
		sb.replace(frn.toUpperCase(), ton.toUpperCase());
		return sb.toString();
	}

	private String replaceArrayClass(String clz) {
		if (clz.startsWith("[")) {
			Type type1 = Type.fieldDesc(clz);
			if (type1.type == templateType) {
				type1 = replaceType(type1, internalType);
				clz = type1.getActualClass();
			}
		}
		return clz;
	}
	private String replaceDesc(String desc, Type target) {
		boolean dirty = false;
		if (desc.startsWith("(")) {
			List<Type> types = Type.methodDesc(desc);
			for (int i = 0; i < types.size(); i++) {
				Type t = types.get(i);
				if (t.type == templateType) {
					dirty = true;
					types.set(i, replaceType(t, target));
				}
			}
			if (dirty) desc = Type.toMethodDesc(types);
		} else {
			Type t = Type.fieldDesc(desc);
			if (t.type == templateType) {
                desc = replaceType(t, target).toDesc();
			}
		}
		return desc;
	}
	private static Type replaceType(Type type1, Type target) {
		return target.owner == null ? Type.primitive(target.type, type1.array()) : Type.klass(target.owner, type1.array());
	}

	private byte replaceCode(byte code) {
		switch (Opcodes.category(code)) {
			case Opcodes.CATE_MATH_CAST:
			case Opcodes.CATE_LOAD_STORE:
			case Opcodes.CATE_LOAD_STORE_LEN:
			case Opcodes.CATE_MATH:
			case Opcodes.CATE_ARRAY_SL:
				code = changeCodeType(code, Type.primitive(templateType), internalType);
				break;
		}
		return code;
	}

	public void insn(byte code) { super.insn(replaceCode(code)); }
	public void vars(byte code, int value) { super.vars(replaceCode(code), value); }

	@Override
	protected void visitAttribute(ConstantPool cp, String name, int len, DynByteBuf b) {
		if (!name.equals("LineNumberTable")) return;
		super.visitAttribute(cp, name, len, b);
	}
}