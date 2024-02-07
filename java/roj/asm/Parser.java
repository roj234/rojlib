package roj.asm;

import roj.asm.cp.*;
import roj.asm.tree.*;
import roj.asm.tree.attr.*;
import roj.asm.type.Signature;
import roj.asm.visitor.CodeVisitor;
import roj.asm.visitor.CodeWriter;
import roj.asm.visitor.XAttrCode;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.AttributeKey;
import roj.util.ByteList;
import roj.util.DynByteBuf;

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

	public static ConstantData parse(byte[] b) { return parse(new ByteList(b)); }
	public static ConstantData parse(DynByteBuf buf) {
		DynByteBuf r = AsmShared.local().copy(buf);

		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);

		ConstantPool pool = new ConstantPool();
		pool.read(r, ConstantPool.CHAR_STRING);

		ConstantData data = new ConstantData(version, pool, r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort());

		int len = r.readUnsignedShort();

		SimpleList<CstClass> itf = data.interfaces;
		itf.ensureCapacity(len);
		while (len-- > 0) itf.add((CstClass) pool.get(r));

		len = r.readUnsignedShort();
		SimpleList<FieldNode> fields = data.fields;
		fields.ensureCapacity(len);
		while (len-- > 0) {
			FieldNode field = new FieldNode(r.readShort(), ((CstUTF) pool.get(r)).str(), ((CstUTF) pool.get(r)).str());
			fields.add(field);

			attr(pool, r, field, Signature.FIELD);
		}

		len = r.readUnsignedShort();
		SimpleList<MethodNode> methods = data.methods;
		methods.ensureCapacity(len);
		while (len-- > 0) {
			MethodNode m = new MethodNode(r.readShort(), data.name, ((CstUTF) pool.get(r)).str(), ((CstUTF) pool.get(r)).str());
			methods.add(m);

			attr(pool, r, m, Signature.METHOD);
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
			String name = ((CstUTF) pool.get(r)).str();
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

	public static void parseAttributes(Attributed node, ConstantPool cp, AttributeList list, int origin) {
		AsmShared as = AsmShared.local();

		for (int i = 0; i < list.size(); i++) {
			Attribute attr = list.get(i);
			if (attr.getClass() == AttrUnknown.class && attr.getRawData() != null) {
				DynByteBuf data = as.copy(attr.getRawData());
				attr = attr(node, cp, attr.name(), data, origin);
				if (attr == null) continue;
				list.set(i, attr);
			}
		}
	}
	@SuppressWarnings("unchecked")
	public static <T extends Attribute> T parseAttribute(Attributed node, ConstantPool cp, AttributeKey<T> type, AttributeList list, int origin) {
		Attribute attr = list == null ? null : (Attribute) list.getByName(type.name);
		if (attr == null) return null;
		if (attr.getClass() == AttrUnknown.class) {
			if (cp == null) return null;
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
	public static Attribute attr(Attributed node, ConstantPool cp, String name, DynByteBuf data, int origin) {
		if (skipToStringParse) return null;

		int len = data.rIndex;
		try {
			switch (name) {
				case "RuntimeVisibleTypeAnnotations":
				case "RuntimeInvisibleTypeAnnotations": return new TypeAnnotations(name, data, cp);
				case "RuntimeVisibleAnnotations":
				case "RuntimeInvisibleAnnotations": return new Annotations(name, data, cp);
				case "RuntimeVisibleParameterAnnotations":
				case "RuntimeInvisibleParameterAnnotations": return new ParameterAnnotations(name, data, cp);
				case "Signature": return Signature.parse(((CstUTF) cp.get(data)).str(), origin);
				case "Synthetic": case "Deprecated": break;
				// method only
				case "MethodParameters": limit(origin,Signature.METHOD); return new MethodParameters(data, cp);
				case "Exceptions": limit(origin,Signature.METHOD); return new AttrClassList(name, data, cp);
				case "AnnotationDefault": limit(origin,Signature.METHOD); return new AnnotationDefault(data, cp);
				case "Code": limit(origin,Signature.METHOD); return new XAttrCode(data, cp, (MethodNode)node);
				// field only
				case "ConstantValue": limit(origin,Signature.FIELD); return new ConstantValue(cp.get(data));
				// class only
				case "Record": limit(origin,Signature.CLASS); return new AttrRecord(data, cp);
				case "InnerClasses": limit(origin,Signature.CLASS); return new InnerClasses(data, cp);
				case "Module": limit(origin,Signature.CLASS); return new AttrModule(data, cp);
				case "ModulePackages":
				case "PermittedSubclasses":
				case "NestMembers": limit(origin,Signature.CLASS); return new AttrClassList(name, data, cp);
				case "ModuleMainClass":
				case "NestHost": limit(origin,Signature.CLASS); return new AttrString(name, ((CstClass) cp.get(data)).name().str());
				case "SourceFile": limit(origin,Signature.CLASS); return new AttrString(name, ((CstUTF) cp.get(data)).str());
				case "BootstrapMethods": limit(origin,Signature.CLASS); return new BootstrapMethods(data, cp);
				// 匿名类所属的方法
				case "EnclosingMethod": limit(origin,Signature.CLASS); return new EnclosingMethod((CstClass) cp.get(data), (CstNameAndType) cp.get(data));
				case "SourceDebugExtension": break;
			}
		} /*catch (OperationDone e) {
			// slightly ignore
		} */catch (Throwable e) {
			skipToStringParse = true;
			String s;
			try {
				s = node.toString();
			} catch (Throwable ex) {
				s = ex.toString();
			}
			skipToStringParse = false;
			data.rIndex = len;
			throw new IllegalStateException("无法读取"+s+"的属性'"+name+"',长度为"+data.readableBytes()+",数据:"+data.dump(), e);
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
	public static ConstantData parseConstants(byte[] buf) { return parseConstants(new ByteList(buf)); }
	public static ConstantData parseConstants(DynByteBuf buf) {
		DynByteBuf r = AsmShared.local().copy(buf);

		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = r.readUnsignedShort() | (r.readUnsignedShort() << 16);

		ConstantPool pool = new ConstantPool();
		pool.read(r, ConstantPool.BYTE_STRING);

		ConstantData data = new ConstantData(version, pool, r.readUnsignedShort(), r.readUnsignedShort(), r.readUnsignedShort());

		int len = r.readUnsignedShort();

		SimpleList<CstClass> itf = data.interfaces;
		itf.ensureCapacity(len);
		while (len-- > 0) itf.add((CstClass) pool.get(r));

		len = r.readUnsignedShort();
		SimpleList<FieldNode> fields = data.fields;
		fields.ensureCapacity(len);
		while (len-- > 0) {
			FieldNode f = new FieldNode(r.readShort(), (CstUTF) pool.get(r), (CstUTF) pool.get(r));
			fields.add(f);

			attrUnparsed(pool, r, f);
		}

		len = r.readUnsignedShort();
		SimpleList<MethodNode> methods = data.methods;
		methods.ensureCapacity(len);
		while (len-- > 0) {
			MethodNode m = new MethodNode(r.readShort(), data.name, (CstUTF) pool.get(r), (CstUTF) pool.get(r));
			methods.add(m);

			attrUnparsed(pool, r, m);
		}

		attrUnparsed(pool, r, data);
		return data;
	}

	private static void attrUnparsed(ConstantPool pool, DynByteBuf r, Attributed node) {
		int len = r.readUnsignedShort();
		if (len == 0) return;

		AttributeList list = node.attributes();
		list.ensureCapacity(len);
		while (len-- > 0) {
			CstUTF name = (CstUTF) pool.get(r);
			int length = r.readInt();
			// ByteList$Slice consumes 40 bytes , byte[] array header consumes 24+length bytes
			list.i_direct_add(new AttrUnknown(name, length == 0 ? null : length <= 16 ? r.readBytes(length) : r.slice(length)));
		}
	}

	// endregion
	// region ACCESS parse LOD 0

	public static AccessData parseAccess(DynByteBuf buf, boolean modifiable) {
		DynByteBuf r = AsmShared.local().copy(buf);

		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");

		r.rIndex += 4; // ver

		ConstantPool pool = AsmShared.local().constPool();
		pool.read(r, ConstantPool.ONLY_STRING);

		int cfo = r.rIndex; // acc
		char acc = r.readChar();

		AccessData data = new AccessData(modifiable?buf.toByteArray():null, cfo, pool.getRefName(r), pool.getRefName(r));
		data.acc = acc;

		int len = r.readUnsignedShort();
		SimpleList<String> itf = new SimpleList<>(len);
		while (len-- > 0) itf.add(pool.getRefName(r));

		data.itf = itf;

		for (int k = 0; k < 2; k++) {
			len = r.readUnsignedShort();
			List<AccessData.MOF> com = new SimpleList<>(len);
			while (len-- > 0) {
				int offset = r.rIndex;

				acc = r.readChar();

				AccessData.MOF d = data.new MOF(((CstUTF) pool.get(r)).str(), ((CstUTF) pool.get(r)).str(), offset);
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
		cp.setAddListener(c);
		try {
			cp.read(r, ConstantPool.CHAR_STRING);
		} finally {
			cp.setAddListener(null);
			cp.clear();
		}
	}

	// endregion

	public static byte[] toByteArray(IClass c) { return c.getBytes(AsmShared.getBuf()).toByteArray(); }
	public static ByteList toByteArrayShared(IClass c) { return (ByteList) c.getBytes(AsmShared.getBuf()); }

	public static DynByteBuf reader(Attribute attr) { return AsmShared.local().copy(attr.getRawData()); }

	public static void compress(ConstantData data) {
		ConstantPool cpw = new ConstantPool(data.cp.array().size());
		CodeVisitor smallerLdc = new CodeVisitor() {
			protected void ldc(byte code, Constant c) { cpw.reset(c); }
		};

		SimpleList<MethodNode> methods = data.methods;
		for (int i = 0; i < methods.size(); i++) {
			Attribute code = methods.get(i).attrByName("Code");
			if (code != null) smallerLdc.visit(data.cp, reader(code));
		}

		CodeWriter cw = new CodeWriter();
		ByteList bw = new ByteList();
		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.attrByName("Code");
			if (code != null) {
				cw.init(bw, cpw);
				cw.mn = mn; // for UNINITIAL_THIS
				cw.visit(data.cp, code.getRawData());
				cw.finish();

				byte[] array = bw.toByteArray();
				// will not parse this
				mn.putAttr(new Attribute() {
					@Override
					public String name() { return "Code"; }
					@Override
					public DynByteBuf getRawData() { return ByteList.wrap(array); }
					@Override
					public String toString() { return "Tmpcw"; }
				});
				bw.clear();
			}
		}

		data.parsed();
		data.cp = cpw;

		for (int i = 0; i < methods.size(); i++) {
			MethodNode mn = methods.get(i);
			Attribute code = mn.attrByName("Code");
			if (code != null) mn.putAttr(new AttrUnknown("Code", code.getRawData()));
		}
	}
}