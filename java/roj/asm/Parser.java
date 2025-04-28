package roj.asm;

import org.jetbrains.annotations.NotNull;
import roj.asm.attr.*;
import roj.asm.cp.*;
import roj.asm.insn.AttrCode;
import roj.asm.type.Signature;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.io.IOUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.BiFunction;

/**
 * 字节码解析器
 * @author Roj234
 * @version 3.1
 * @since 2021/5/29 17:16
 */
public final class Parser {
	public static final int RECORD_ATTR = 8;

	private static final MyHashMap<String, BiFunction<ConstantPool, DynByteBuf, Attribute>> USER_DESERIALIZER = new MyHashMap<>();
	public static <T extends Attribute> void registerCustomAttribute(TypedKey<T> id, BiFunction<ConstantPool, DynByteBuf, T> o) {
		USER_DESERIALIZER.put(id.name, Helpers.cast(o));
	}

	// region CLAZZ parse LOD 2
	public static ClassNode parse(byte[] b) { return parse(new ByteList(b)); }
	public static ClassNode parse(DynByteBuf r) {
		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = r.readInt();

		ConstantPool pool = new ConstantPool();
		pool.read(r, ConstantPool.CHAR_STRING);

		ClassNode data = new ClassNode(version, pool, r.readUnsignedShort(), pool.get(r), (CstClass) pool.getNullable(r));

		int len = r.readUnsignedShort();

		if (len > 0) {
			SimpleList<CstClass> itf = data.itfList();
			itf.ensureCapacity(len);
			while (len-- > 0) itf.add(pool.get(r));
		}

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
			MethodNode m = new MethodNode(r.readShort(), data.name(), ((CstUTF) pool.get(r)).str(), ((CstUTF) pool.get(r)).str());
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
			list._add(null == attr ? new UnparsedAttribute(name, r.slice(length)) : attr);

			// 忽略过长的属性.
			r.rIndex = end;
			r.wIndex(origEnd);
		}
	}

	public static void parseAttributes(Attributed node, ConstantPool cp, AttributeList list, int origin) {
		for (int i = 0; i < list.size(); i++) {
			Attribute attr = list.get(i);
			if (attr.getClass() == UnparsedAttribute.class && attr.getRawData() != null) {
				DynByteBuf data = Parser.reader(attr);
				attr = attr(node, cp, attr.name(), data, origin);
				if (attr == null) continue;
				list.set(i, attr);
			}
		}
	}
	@SuppressWarnings("unchecked")
	public static <T extends Attribute> T parseAttribute(Attributed node, ConstantPool cp, TypedKey<T> type, AttributeList list, int origin) {
		Attribute attr = list == null ? null : (Attribute) list.getByName(type.name);
		if (attr == null) return null;
		if (attr.getClass() == UnparsedAttribute.class) {
			if (cp == null) return null;
			attr = attr(node, cp, type.name, Parser.reader(attr), origin);
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
				default:
					var deserializer = USER_DESERIALIZER.get(name);
					if (deserializer != null) return deserializer.apply(cp, data);
				break;
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
				case "Exceptions": limit(origin,Signature.METHOD); return new ClassListAttribute(name, data, cp);
				case "AnnotationDefault": limit(origin,Signature.METHOD); return new AnnotationDefault(data, cp);
				case "Code": limit(origin,Signature.METHOD); return new AttrCode(data, cp, (MethodNode)node);
				// field only
				case "ConstantValue": limit(origin,Signature.FIELD); return new ConstantValue(cp.get(data));
				// class only
				case "Record": limit(origin,Signature.CLASS); return new RecordAttribute(data, cp);
				case "InnerClasses": limit(origin,Signature.CLASS); return new InnerClasses(data, cp);
				case "Module": limit(origin,Signature.CLASS); return new ModuleAttribute(data, cp);
				case "ModulePackages":
				case "PermittedSubclasses":
				case "NestMembers": limit(origin,Signature.CLASS); return new ClassListAttribute(name, data, cp);
				case "ModuleMainClass":
				case "NestHost": limit(origin,Signature.CLASS); return new StringAttribute(name, cp.getRefName(data, Constant.CLASS));
				case "ModuleTarget":
				case "SourceFile": limit(origin,Signature.CLASS); return new StringAttribute(name, ((CstUTF) cp.get(data)).str());
				case "BootstrapMethods": limit(origin,Signature.CLASS); return new BootstrapMethods(data, cp);
				// 匿名类所属的方法
				case "EnclosingMethod": limit(origin,Signature.CLASS); return new EnclosingMethod(cp.get(data), (CstNameAndType) cp.getNullable(data));
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

	public static ClassNode parseConstants(Class<?> o) {
		String fn = o.getName().replace('.', '/').concat(".class");
		ClassLoader cl = o.getClassLoader();
		try (InputStream in = cl==null?ClassLoader.getSystemResourceAsStream(fn):cl.getResourceAsStream(fn)) {
			if (in != null) return parseConstants(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray());
		} catch (IOException ignored) {}
		return null;
	}
	public static ClassNode parseConstants(byte[] buf) {return parseConstants(new ByteList(buf));}
	public static ClassNode parseConstants(DynByteBuf r) {return parseConstants(r, null);}
	public static ClassNode parseConstants(DynByteBuf r, ConstantPool pool) {
		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = r.readInt();

		if (pool == null) {
			pool = new ConstantPool();
			pool.read(r, ConstantPool.BYTE_STRING);
		} else {
			r.rIndex += pool.byteLength()+2;
		}

		return parseConstantsNoCp(r, pool, version);
	}
	@NotNull
	public static ClassNode parseConstantsNoCp(DynByteBuf r, ConstantPool pool, int version) {
		ClassNode data = new ClassNode(version, pool, r.readUnsignedShort(), pool.get(r), (CstClass) pool.getNullable(r));

		int len = r.readUnsignedShort();

		if (len > 0) {
			SimpleList<CstClass> itf = data.itfList();
			itf.ensureCapacity(len);
			while (len-- > 0) itf.add(pool.get(r));
		}

		len = r.readUnsignedShort();
		SimpleList<FieldNode> fields = data.fields;
		fields.ensureCapacity(len);
		while (len-- > 0) {
			FieldNode f = new FieldNode(r.readShort(), pool.get(r), pool.get(r));
			fields.add(f);

			attrUnparsed(pool, r, f);
		}

		len = r.readUnsignedShort();
		SimpleList<MethodNode> methods = data.methods;
		methods.ensureCapacity(len);
		while (len-- > 0) {
			MethodNode m = new MethodNode(r.readShort(), data.name(), pool.get(r), pool.get(r));
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
			CstUTF name = pool.get(r);
			int length = r.readInt();
			// ByteList$Slice consumes 40 bytes , byte[] array header consumes 24+length bytes
			list._add(new UnparsedAttribute(name, length == 0 ? null : length <= 16 ? r.readBytes(length) : r.slice(length)));
		}
	}

	// endregion
	// region ACCESS parse LOD 0

	public static ClassView parseAccess(DynByteBuf r, boolean modifiable) {
		if (r.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");

		r.rIndex += 4; // ver

		var pool = AsmShared.local().constPool();
		pool.read(r, ConstantPool.ONLY_STRING);

		int cfo = r.rIndex; // acc
		char acc = r.readChar();

		ClassView data = new ClassView(modifiable?r.toByteArray():null, cfo, pool.getRefName(r, Constant.CLASS), pool.getRefName(r));
		data.modifier = acc;

		int len = r.readUnsignedShort();
		SimpleList<String> itf = new SimpleList<>(len);
		while (len-- > 0) itf.add(pool.getRefName(r, Constant.CLASS));

		data.interfaces = itf;

		for (int k = 0; k < 2; k++) {
			len = r.readUnsignedShort();
			List<ClassView.MOF> com = new SimpleList<>(len);
			while (len-- > 0) {
				int offset = r.rIndex;

				acc = r.readChar();

				ClassView.MOF d = data.new MOF(((CstUTF) pool.get(r)).str(), ((CstUTF) pool.get(r)).str(), offset);
				d.modifier = acc;
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

		AsmShared.local().constPool(pool);
		return data;
	}

	// endregion

	public static byte[] toByteArray(IClass c) {
		var buf = AsmShared.buf();
		byte[] array = c.toByteArray(buf).toByteArray();
		AsmShared.buf(buf);
		return array;
	}
	public static ByteList toByteArrayShared(IClass c) {
		ByteList buf = AsmShared.buf();
		c.toByteArray(buf);
		AsmShared.buf(buf);
		return buf;
	}

	public static DynByteBuf reader(Attribute attr) { return AsmShared.local().copy(attr.getRawData()); }
}