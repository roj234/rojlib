package roj.asm;

import roj.asm.cst.Constant;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstClass;
import roj.asm.cst.CstUTF;
import roj.asm.tree.*;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.util.AttributeList;
import roj.collect.SimpleList;
import roj.io.FastFailException;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

/**
 * Roj ASM parser (字节码解析器) <br>
 * 基于Java 8 构造 <br>
 * 现已支持 Java 15 (需要测试)
 *
 * @author Roj234
 * @version 2.0
 * @since 2021/5/29 17:16
 */
public final class Parser {
	public static final int CTYPE_ACCESS = 0;
	public static final int CTYPE_PARSED = 1;
	public static final int CTYPE_REFLECT = 2;

	public static final int FTYPE_SIMPLE = 0;
	public static final int FTYPE_FULL = 1;
	public static final int FTYPE_REFLECT = 2;

	public static final int MTYPE_SIMPLE = 3;
	public static final int MTYPE_FULL = 4;
	public static final int MTYPE_REFLECT = 5;

	public static final int MFTYPE_LOD1 = 6;

	public static final int CODE_ATTR = 7;
	public static final int RECORD_ATTR = 8;

	// region CLAZZ parse LOD 2

	public static ConstantData parse(byte[] buf) {
		return parse(new ByteList(buf));
	}

	@SuppressWarnings("fallthrough")
	public static ConstantData parse(DynByteBuf buf) {
		DynByteBuf r = AsmShared.local().copy(buf);

		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);

		ConstantPool pool = new ConstantPool(r.readUnsignedShort());
		pool.read(r);

		ConstantData data = new ConstantData(version, pool, r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort());

		int len = r.readUnsignedShort();

		SimpleList<CstClass> itf = data.interfaces;
		itf.ensureCapacity(len);
		while (len-- > 0) itf.add((CstClass) pool.get(r));

		len = r.readUnsignedShort();
		SimpleList<FieldNode> fields = data.fields;
		fields.ensureCapacity(len);
		while (len-- > 0) {
			Field field = new Field(r.readShort(), ((CstUTF) pool.get(r)).getString(), ((CstUTF) pool.get(r)).getString());
			fields.add(field);

			parseAttribute(pool, r, field);
		}

		len = r.readUnsignedShort();
		SimpleList<MethodNode> methods = data.methods;
		methods.ensureCapacity(len);
		while (len-- > 0) {
			Method method = new Method(r.readShort(), data, ((CstUTF) pool.get(r)).getString(), ((CstUTF) pool.get(r)).getString());
			methods.add(method);

			parseAttribute(pool, r, method);
		}

		parseAttribute(pool, r, data);
		// in order to compress
		data.cp.clear();
		return data;
	}

	private static void parseAttribute(ConstantPool pool, DynByteBuf r, AttributeReader node) {
		int len = r.readUnsignedShort();
		if (len == 0) return;

		int origEnd = r.wIndex();

		AttributeList list = node.attributes();
		list.ensureCapacity(len);
		while (len-- > 0) {
			String name = ((CstUTF) pool.get(r)).getString();
			int length = r.readInt();
			int end = r.rIndex + length;
			r.wIndex(end);

			list.add(_guarded_PA(pool, r, node, name, length));

			r.rIndex = end;
			r.wIndex(origEnd);
		}
	}

	public static void withParsedAttribute(ConstantData data) {
		ConstantPool pool = data.cp;

		SimpleList<FieldNode> fields = data.fields;
		for (int i = 0; i < fields.size(); i++) {
			FieldNode node = fields.get(i);
			if (node instanceof RawField) {
				fields.set(i, new Field(data, (RawField) node));
			} else {
				withParsedAttribute(pool, (AttributeReader) node);
			}
		}

		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode node = methods.get(i);
			if (node instanceof RawMethod) {
				methods.set(i, new Method(data, (RawMethod) node));
			} else {
				withParsedAttribute(pool, (AttributeReader) node);
			}
		}

		withParsedAttribute(pool, data);
		pool.clear();
	}
	public static void withParsedAttribute(ConstantPool pool, AttributeReader node) {
		AsmShared U = AsmShared.local();

		AttributeList list = node.attributesNullable();
		if (list == null) return;
		for (int i = 0; i < list.size(); i++) {
			Attribute attr = list.get(i);
			if (attr.getClass() == AttrUnknown.class) {
				DynByteBuf r = U.copy(attr.getRawData());

				attr = _guarded_PA(pool, r, node, attr.name, -1);
				if (attr == null) continue;
				list.set(i, attr);
			}
		}
	}

	private static Attribute _guarded_PA(ConstantPool pool, DynByteBuf r, AttributeReader node, String name, int length) {
		Attribute attr;
		try {
			attr = node.parseAttribute(pool, r, name, length);
		} catch (Exception e) {
			throw new IllegalStateException("Error deserializing '" + name + "' in " + safeToString(node) + "\n" +
				"data_remain=" + r, e);
		}
		if (r.isReadable()) {
			System.err.println("Parser.java:170: Attr '" + name + "' left " + r.readableBytes() + " bytes (except: " + length + "): \n" +
				attr + "\n" + "At type " + node.type() + "\n" + r);
		}
		return attr;
	}


	private static boolean throwFlag;
	private synchronized static String safeToString(Object o) {
		if (throwFlag) throw new FastFailException(1);
		throwFlag = true;
		try {
			return o.toString();
		} catch (FastFailException e) {
			try {
				return o.getClass().getName() + "@" + o.hashCode();
			} catch (FastFailException e1) {
				return "A " + o.getClass().getName();
			}
		} finally {
			throwFlag = false;
		}
	}

	// endregion
	// region CONSTANT DATA parse LOD 1

	public static ConstantData parseConstants(byte[] buf) {
		return parseConstants(new ByteList(buf));
	}

	@Nonnull
	public static ConstantData parseConstants(DynByteBuf buf) {
		DynByteBuf r = AsmShared.local().copy(buf);

		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);

		ConstantPool pool = new ConstantPool(r.readUnsignedShort());
		pool.read(r);

		ConstantData data = new ConstantData(version, pool, r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort());

		int len = r.readUnsignedShort();

		SimpleList<CstClass> itf = data.interfaces;
		itf.ensureCapacity(len);
		while (len-- > 0) itf.add((CstClass) pool.get(r));

		len = r.readUnsignedShort();
		SimpleList<FieldNode> fields = data.fields;
		fields.ensureCapacity(len);
		while (len-- > 0) {
			RawField field = new RawField(r.readShort(), (CstUTF) pool.get(r), (CstUTF) pool.get(r));
			fields.add(field);

			withUnparsedAttribute(pool, r, field);
		}

		len = r.readUnsignedShort();
		SimpleList<MethodNode> methods = data.methods;
		methods.ensureCapacity(len);
		while (len-- > 0) {
			RawMethod method = new RawMethod(r.readShort(), (CstUTF) pool.get(r), (CstUTF) pool.get(r));
			method.cn(data.name);
			methods.add(method);

			withUnparsedAttribute(pool, r, method);
		}

		withUnparsedAttribute(pool, r, data);
		return data;
	}

	private static void withUnparsedAttribute(ConstantPool pool, DynByteBuf r, Attributed node) {
		int len = r.readUnsignedShort();
		if (len == 0) return;

		AttributeList list = node.attributes();
		list.ensureCapacity(len);
		while (len-- > 0) {
			String name = ((CstUTF) pool.get(r)).getString();
			list.add(new AttrUnknown(name, r.slice(r.readInt())));
		}
	}

	// endregion
	// region ACCESS parse LOD 0

	public static AccessData parseAccess(byte[] buf) {
		return parseAcc0(buf, new ByteList(buf));
	}

	@Nonnull
	public static AccessData parseAcc0(byte[] dst, DynByteBuf buf) {
		DynByteBuf r = AsmShared.local().copy(buf);

		if (r.readInt() != 0xcafebabe) {
			throw new IllegalArgumentException("Illegal header");
		}
		r.rIndex += 4; // ver

		ConstantPool pool = AsmShared.local().constPool();
		pool.init(r.readUnsignedShort());
		pool.readName(r);

		int cfo = r.rIndex; // acc
		r.rIndex += 2;

		AccessData data = new AccessData(dst, cfo, pool.getName(r), pool.getName(r));

		int len = r.readUnsignedShort();
		SimpleList<String> itf = new SimpleList<>(len);
		while (len-- > 0) itf.add(pool.getName(r));

		data.itf = itf;

		for (int k = 0; k < 2; k++) {
			len = r.readUnsignedShort();
			List<AccessData.MOF> com = new SimpleList<>(len);
			while (len-- > 0) {
				int offset = r.rIndex;

				char acc = r.readChar();

				AccessData.MOF d = data.new MOF(((CstUTF) pool.get(r)).getString(), ((CstUTF) pool.get(r)).getString(), offset);
				d.acc = acc;
				com.add(d);

				int attrs = r.readUnsignedShort();
				for (int j = 0; j < attrs; j++) {
					r.rIndex += 2;
					int ol = r.readInt();
					r.rIndex += ol;
				}
			}
			if (k == 0) data.fields = com;
			else data.methods = com;
		}
		return data;
	}

	// endregion
	// region FOREACH CONSTANT LOD 0

	public static void forEachConstant(DynByteBuf buf, Consumer<Constant> c) {
		DynByteBuf r = AsmShared.local().copy(buf);
		if (r.readInt() != 0xcafebabe) {
			throw new IllegalArgumentException("Illegal header");
		}

		r.rIndex += 4; // ver

		ConstantPool cp = AsmShared.local().constPool();
		cp.init(r.readUnsignedShort());
		cp.setAddListener(c);
		cp.read(r);
	}

	// endregion

	public static byte[] toByteArray(IClass c) {
		return c.getBytes(AsmShared.getBuf()).toByteArray();
	}

	public static ByteList toByteArrayShared(IClass c) {
		return (ByteList) c.getBytes(AsmShared.getBuf());
	}

	public static DynByteBuf reader(Attribute attr) {
		return AsmShared.local().copy(attr.getRawData());
	}
}