package roj.asm;

import roj.asm.cst.*;
import roj.asm.tree.*;
import roj.asm.tree.attr.*;
import roj.asm.type.Signature;
import roj.asm.util.AttributeList;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.TypedName;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * 字节码解析器
 * @author Roj234
 * @version 2.4
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

	public static ConstantData parse(Class<?> o) {
		String fn = o.getName().replace('.', '/').concat(".class");
		ClassLoader cl = o.getClassLoader();
		try (InputStream in = cl==null?ClassLoader.getSystemResourceAsStream(fn):cl.getResourceAsStream(fn)) {
			return parse(IOUtil.getSharedByteBuf().readStreamFully(in));
		} catch (Exception ignored) {}
		return null;
	}

	public static ConstantData parse(byte[] buf) {
		return parse(new ByteList(buf));
	}

	@SuppressWarnings("fallthrough")
	public static ConstantData parse(DynByteBuf buf) {
		DynByteBuf r = AsmShared.local().copy(buf);

		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);

		ConstantPool pool = new ConstantPool(r.readUnsignedShort());
		pool.read(r, true);

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

			attr(pool, r, field, Signature.FIELD);
		}

		len = r.readUnsignedShort();
		SimpleList<MethodNode> methods = data.methods;
		methods.ensureCapacity(len);
		while (len-- > 0) {
			Method method = new Method(r.readShort(), data, ((CstUTF) pool.get(r)).getString(), ((CstUTF) pool.get(r)).getString());
			methods.add(method);

			attr(pool, r, method, Signature.METHOD);
		}

		attr(pool, r, data, Signature.CLASS);
		// in order to compress
		data.cp.clear();
		return data;
	}

	private static void attr(ConstantPool pool, DynByteBuf r, Attributed node, int origin) {
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

			Attribute attr = attr(node, pool, name, r, origin);
			list.i_direct_add(null == attr ? new AttrUnknown(name, r.slice(length)) : attr);

			// 忽略过长的属性.
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
				AttributeList list = node.attributesNullable();
				if (list != null) parseAttributes(node,pool,list,Signature.FIELD);
			}
		}

		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			MethodNode node = methods.get(i);
			if (node instanceof RawMethod) {
				methods.set(i, new Method(data, (RawMethod) node));
			} else {
				AttributeList list = node.attributesNullable();
				if (list != null) parseAttributes(node,pool, list,Signature.METHOD);
			}
		}

		AttributeList list = data.attributesNullable();
		if (list != null) parseAttributes(data,pool, list,Signature.CLASS);

		pool.clear();
	}

	public static void parseAttributes(Attributed node, ConstantPool cp, AttributeList list, int origin) {
		AsmShared as = AsmShared.local();

		for (int i = 0; i < list.size(); i++) {
			Attribute attr = list.get(i);
			if (attr.getClass() == AttrUnknown.class) {
				DynByteBuf data = as.copy(attr.getRawData());
				attr = attr(node, cp, attr.name(), data, origin);
				if (attr == null) continue;
				list.set(i, attr);
			}
		}
	}
	@SuppressWarnings("unchecked")
	public static <T extends Attribute> T parseAttribute(Attributed node, ConstantPool cp, TypedName<T> type, AttributeList list, int origin) {
		Attribute attr = list == null ? null : (Attribute) list.getByName(type.name);
		if (attr == null) return null;
		if (attr.getClass() == AttrUnknown.class) {
			attr = attr(node, cp, type.name, AsmShared.local().copy(attr.getRawData()), origin);
			if (attr == null) {
				if (skipToStringParse) return null;
				throw new UnsupportedOperationException("不支持的属性");
			}
			list.add(attr);
		}
		return (T) attr;
	}
	private static boolean skipToStringParse;
	private static Attribute attr(Attributed node, ConstantPool cp, String name, DynByteBuf r, int origin) {
		if (skipToStringParse) return null;

		int len = r.rIndex;
		try {
			switch (name) {
				case "RuntimeVisibleTypeAnnotations":
				case "RuntimeInvisibleTypeAnnotations": return new TypeAnnotations(name, r, cp);
				case "RuntimeVisibleAnnotations":
				case "RuntimeInvisibleAnnotations": return new Annotations(name, r, cp);
				case "RuntimeVisibleParameterAnnotations":
				case "RuntimeInvisibleParameterAnnotations": return new ParameterAnnotations(name, r, cp);
				case "Signature": return Signature.parse(((CstUTF) cp.get(r)).getString(), origin);
				case "Synthetic": case "Deprecated": break;
				// method only
				case "MethodParameters": limit(origin,Signature.METHOD); return new MethodParameters(r, cp);
				case "Exceptions": limit(origin,Signature.METHOD); return new AttrStringList(name, r, cp, 0);
				case "AnnotationDefault": limit(origin,Signature.METHOD); return new AnnotationDefault(r, cp);
				case "Code": limit(origin,Signature.METHOD); return new AttrCode((MethodNode) node, r, cp);
				// field only
				case "ConstantValue": limit(origin,Signature.FIELD); return new ConstantValue(cp.get(r));
				// class only
				case "Record": limit(origin,Signature.CLASS); return new AttrRecord(r, cp);
				case "InnerClasses": limit(origin,Signature.CLASS); return new InnerClasses(r, cp);
				case "Module": limit(origin,Signature.CLASS); return new AttrModule(r, cp);
				case "ModulePackages": limit(origin,Signature.CLASS); return new AttrModulePackages(r, cp);
				case "ModuleMainClass":
				case "NestHost": limit(origin,Signature.CLASS); return new AttrClassRef(name, r, cp);
				case "PermittedSubclasses":
				case "NestMembers": limit(origin,Signature.CLASS); return new AttrStringList(name, r, cp, 0);
				case "SourceFile": limit(origin,Signature.CLASS); return new AttrUTF(name, ((CstUTF) cp.get(r)).getString());
				case "BootstrapMethods": limit(origin,Signature.CLASS); return new BootstrapMethods(r, cp);
				// 匿名类所属的方法
				case "EnclosingMethod": limit(origin,Signature.CLASS); return new EnclosingMethod((CstClass) cp.get(r), (CstNameAndType) cp.get(r));
				case "SourceDebugExtension": break;
			}
		} /*catch (OperationDone e) {
			// slightly ignore
		} */catch (Throwable e) {
			skipToStringParse = true;
			String s = node.toString();
			skipToStringParse = false;
			r.rIndex = len;
			throw new IllegalStateException("无法读取"+s+"的属性'"+name+"',长度为"+r.readableBytes()+",数据:"+r.dump(), e);
		}
		return null;
	}
	private static void limit(int type, int except) {
		if (type != except) throw new IllegalStateException("意料之外的属性,仅能在"+except+"中出现,却在"+type);
	}

	// endregion
	// region CONSTANT DATA parse LOD 1

	public static ConstantData parseConstants(Class<?> o) {
		String fn = o.getName().replace('.', '/').concat(".class");
		ClassLoader cl = o.getClassLoader();
		try (InputStream in = cl==null?ClassLoader.getSystemResourceAsStream(fn):cl.getResourceAsStream(fn)) {
			if (in != null) return parseConstants(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray());
		} catch (IOException ignored) {}
		return null;
	}

	public static ConstantData parseConstants(byte[] buf) {
		return parseConstants(new ByteList(buf));
	}

	@Nonnull
	public static ConstantData parseConstants(DynByteBuf buf) {
		DynByteBuf r = AsmShared.local().copy(buf);

		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);

		ConstantPool pool = new ConstantPool(r.readUnsignedShort());
		pool.read(r, false);

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
			CstUTF name = (CstUTF) pool.get(r);
			list.i_direct_add(new AttrUnknown(name, r.slice(r.readInt())));
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
		cp.read(r, false);
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