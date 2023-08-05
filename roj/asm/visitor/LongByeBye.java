package roj.asm.visitor;

import roj.asm.OpcodeUtil;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.Constant;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstUTF;
import roj.asm.tree.ConstantData;
import roj.asm.tree.FieldNode;
import roj.asm.tree.MethodNode;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.InsnHelper;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/3/19 0019 20:37
 */
public class LongByeBye extends CodeWriter {
	private final int templateType;
	private final Type internalType, methodType, callingType;
	private final boolean replaceCallingName;

	public LongByeBye(int templateType, Type internalType, Type methodType, Type callingType, boolean replaceCallingName) {
		this.templateType = templateType;
		this.internalType = internalType;
		this.methodType = methodType;
		this.callingType = callingType;
		this.replaceCallingName = replaceCallingName;
	}

	private ConstantData curr;
	public ConstantData generate(DynByteBuf data) {
		ConstantData d = curr = Parser.parseConstants(data);
		d.version = 49<<16;

		List<Constant> list = d.cp.array();
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

			Attribute buf = mn.attrByName("Code");
			if (buf != null) {
				tmp.clear();
				init(tmp, d.cp);
				visitCopied(d.cp, buf.getRawData());
				mn.putAttr(new AttrUnknown("Code", tmp.toByteArray()));
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
		byte b = InsnHelper.FromPrimitiveArrayId(arrayType);
		if (b == templateType) {
			if (internalType.isPrimitive()) {
				arrayType = InsnHelper.ToPrimitiveArrayId(internalType.type);
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
		if (owner.equals(curr.name)) {
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
		String frn = Type.toString(from);
		String ton = Type.toString(to.type);
		sb.replace(frn,ton);
		sb.replace(Character.toUpperCase(frn.charAt(0))+frn.substring(1),Character.toUpperCase(ton.charAt(0))+ton.substring(1));
		sb.replace(frn.toUpperCase(), ton.toUpperCase());
		return sb.toString();
	}

	private String replaceArrayClass(String clz) {
		if (clz.startsWith("[")) {
			Type type1 = TypeHelper.parseField(clz);
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
			List<Type> types = TypeHelper.parseMethod(desc);
			for (int i = 0; i < types.size(); i++) {
				Type t = types.get(i);
				if (t.type == templateType) {
					dirty = true;
					types.set(i, replaceType(t, target));
				}
			}
			if (dirty) desc = TypeHelper.getMethod(types);
		} else {
			Type t = TypeHelper.parseField(desc);
			if (t.type == templateType) {
				desc = TypeHelper.getField(replaceType(t, target));
			}
		}
		return desc;
	}
	private static Type replaceType(Type type1, Type target) {
		return target.owner == null ? new Type(target.type, type1.array()) : new Type(target.owner, type1.array());
	}

	private byte replaceCode(byte code) {
		switch (OpcodeUtil.category(code)) {
			case OpcodeUtil.CATE_MATH_CAST:
			case OpcodeUtil.CATE_LOAD_STORE:
			case OpcodeUtil.CATE_LOAD_STORE_LEN:
			case OpcodeUtil.CATE_MATH:
			case OpcodeUtil.CATE_ARRAY_SL:
				code = InsnHelper.changeCodeType(code, Type.std(templateType), internalType);
				break;
		}
		return code;
	}

	public void one(byte code) { super.one(replaceCode(code)); }
	public void vars(byte code, int value) { super.vars(replaceCode(code), value); }

	@Override
	protected void visitAttribute(ConstantPool cp, String name, int len, DynByteBuf b) {
		if (!name.equals("LineNumberTable")) return;
		super.visitAttribute(cp, name, len, b);
	}
}
