package roj.asm;

import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.attr.Attribute;
import roj.asm.attr.AttributeList;
import roj.asm.attr.InnerClasses;
import roj.asm.attr.UnparsedAttribute;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.cp.CstUTF;
import roj.asm.insn.AttrCodeWriter;
import roj.asm.insn.CodeVisitor;
import roj.asm.insn.CodeWriter;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.ArrayList;
import roj.collect.HashSet;
import roj.compiler.library.PackedLibrary;
import roj.concurrent.Flow;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.io.IOException;
import java.io.InputStream;
import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public class ClassNode implements ClassDefinition {
	//region 解析工厂
	public static ClassNode fromType(Class<?> type) {
		String typeName = type.getName().replace('.', '/').concat(".class");
		ClassLoader cl = type.getClassLoader();
		try (InputStream in = cl==null?ClassLoader.getSystemResourceAsStream(typeName):cl.getResourceAsStream(typeName)) {
			if (in != null) return parseSkeleton(IOUtil.getSharedByteBuf().readStreamFully(in).toByteArray());
		} catch (IOException ignored) {}
		return null;
	}

	public static ClassNode parseAll(byte[] buf) {return parseAll(DynByteBuf.wrap(buf));}
	/**
	 * 全量解析（包括属性），并清空常量池
	 * 如果你的目的是压缩常量池，请用{@link roj.asmx.TransformUtil#compress(ClassNode)}而不是这个方法
	 * * 它的效果甚至更好！
	 *
	 * 这个方法很浪费内存，你只应该在特殊的时候使用它
	 */
	public static ClassNode parseAll(DynByteBuf buf) {
		if (buf.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = buf.readInt();

		ConstantPool cp = new ConstantPool();
		cp.read(buf, ConstantPool.CHAR_STRING);

		ClassNode klass = new ClassNode(version, cp, buf.readUnsignedShort(), cp.get(buf), (CstClass) cp.getNullable(buf));

		int len = buf.readUnsignedShort();
		if (len > 0) {
			var itf = klass.itfList();
			itf.ensureCapacity(len);
			while (len-- > 0) itf.add(cp.get(buf));
		}

		len = buf.readUnsignedShort();
		var fields = klass.fields;
		fields.ensureCapacity(len);
		while (len-- > 0) {
			var field = new FieldNode(buf.readShort(), ((CstUTF) cp.get(buf)).str(), ((CstUTF) cp.get(buf)).str());
			fields.add(field);
			pattr(cp, buf, field, Signature.FIELD);
		}

		len = buf.readUnsignedShort();
		var methods = klass.methods;
		methods.ensureCapacity(len);
		while (len-- > 0) {
			var method = new MethodNode(buf.readShort(), klass.name(), ((CstUTF) cp.get(buf)).str(), ((CstUTF) cp.get(buf)).str());
			methods.add(method);
			pattr(cp, buf, method, Signature.METHOD);
		}

		pattr(cp, buf, klass, Signature.CLASS);

		// 释放常量占用的空间
		klass.cp.clear();
		return klass;
	}

	public static ClassNode parseSkeleton(byte[] buf) {return parseSkeleton(new ByteList(buf));}
	/**
	 * 解析类骨架结构（仅处理常量池和类元数据，不处理属性）<br>
	 * 这是我的ASM比ObjectWeb快的关键之处
	 * 另外的好处是，所有的引用都只有一份，所以你可以直接修改{@link ConstantPool 常量池}里的常量，而不需要迭代每一个InsnNode
	 *
	 * @param buf 包含类文件数据的字节缓冲区（会移动读指针）
	 * @see Attributed#getAttribute(ConstantPool, TypedKey) 按需解析属性
	 * @see #parseAll(DynByteBuf)
	 * @return 包含基础结构的ClassNode对象
	 */
	public static ClassNode parseSkeleton(DynByteBuf buf) {
		if (buf.readInt() != 0xcafebabe) throw new IllegalArgumentException("Illegal header");
		int version = buf.readInt();
		var cp = new ConstantPool();
		cp.read(buf, ConstantPool.BYTE_STRING);
		return parseSkeletonWith(buf, version, cp);
	}
	/**
	 * 使用外部常量池进行骨架解析.
	 * 关于这个为什么会独立出来，请看@see，简而言之：方便压缩，共享字符串表
	 *
	 * @param buf    包含类文件数据的字节缓冲区（从name开始读取）
	 * @param version 类文件版本号（需与常量池来源版本一致）
	 * @param pool   预解析的常量池实例（必须与当前类数据匹配）
	 * @see PackedLibrary
	 * @return 包含基础结构的ClassNode对象
	 */
	@NotNull
	public static ClassNode parseSkeletonWith(DynByteBuf buf, int version, ConstantPool pool) {
		var klass = new ClassNode(version, pool, buf.readUnsignedShort(), pool.get(buf), (CstClass) pool.getNullable(buf));

		int len = buf.readUnsignedShort();
		if (len > 0) {
			var itf = klass.itfList();
			itf.ensureCapacity(len);
			while (len-- > 0) itf.add(pool.get(buf));
		}

		len = buf.readUnsignedShort();
		var fields = klass.fields;
		fields.ensureCapacity(len);
		while (len-- > 0) {
			var field = new FieldNode(buf.readShort(), pool.get(buf), pool.get(buf));
			fields.add(field);
			uattr(pool, buf, field);
		}

		len = buf.readUnsignedShort();
		var methods = klass.methods;
		methods.ensureCapacity(len);
		while (len-- > 0) {
			var method = new MethodNode(buf.readShort(), klass.name(), pool.get(buf), pool.get(buf));
			methods.add(method);
			uattr(pool, buf, method);
		}

		uattr(pool, buf, klass);
		return klass;
	}

	private static void pattr(ConstantPool pool, DynByteBuf buf, Attributed node, int position) {
		int len = buf.readUnsignedShort();
		if (len == 0) return;

		var attributes = node.attributes();
		attributes.ensureCapacity(len);

		int origEnd = buf.wIndex();
		while (len-- > 0) {
			String name = ((CstUTF) pool.get(buf)).str();
			int length = buf.readInt();

			int end = buf.rIndex + length;
			buf.wIndex(end);

			var parsedAttribute = Attribute.parse(node, pool, name, buf, position);
			attributes._add(parsedAttribute == null ? new UnparsedAttribute(name, buf.slice(length)) : parsedAttribute);

			// 忽略过长的属性.
			buf.rIndex = end;
			buf.wIndex(origEnd);
		}
	}
	private static void uattr(ConstantPool pool, DynByteBuf r, Attributed node) {
		int len = r.readUnsignedShort();
		if (len == 0) return;

		var attributes = node.attributes();
		attributes.ensureCapacity(len);
		while (len-- > 0) {
			CstUTF name = pool.get(r);
			int length = r.readInt();
			// ByteList$Slice consumes 40 bytes , byte[] array header consumes 24+length bytes
			attributes._add(new UnparsedAttribute(name, length == 0 ? null : length <= 16 ? r.readBytes(length) : r.slice(length)));
		}
	}
	//endregion

	public int version;
	public ConstantPool cp;
	@MagicConstant(flags = ACC_PUBLIC | ACC_FINAL | ACC_SUPER |
			ACC_ABSTRACT | ACC_INTERFACE | ACC_ANNOTATION | ACC_ENUM |
			ACC_SYNTHETIC | ACC_MODULE)
	public char modifier;
	private CstClass nameCst, parentCst;

	private ItfView itfView;
	protected List<CstClass> interfaces = Collections.emptyList();
	public final ArrayList<FieldNode> fields = new ArrayList<>();
	public final ArrayList<MethodNode> methods = new ArrayList<>();

	private AttributeList attributes;

	public ClassNode() {
		this.cp = new ConstantPool();
		//不放入ConstantPool，因为序列化的时候不一定还继承Object
		this.parentCst = new CstClass("java/lang/Object");
		this.modifier = ACC_PUBLIC | ACC_SUPER;
		this.version = 49;
	}
	public static int JavaVersion(int version) { return 44+version; }

	public ClassNode(int version, @NotNull ConstantPool cp, int modifier, @NotNull CstClass name, @Nullable CstClass parent) {
		this.cp = cp;
		this.version = version;
		this.modifier = (char) modifier;
		this.nameCst = name;
		this.parentCst = parent;
	}

	@Override
	public <T extends Attribute> T getAttribute(ConstantPool cp, @MagicConstant(valuesFromClass = Attribute.class) TypedKey<T> type) {return Attribute.parseSingle(this,this.cp,type,attributes,Signature.CLASS);}
	public AttributeList attributes() {return attributes == null ? attributes = new AttributeList() : attributes;}
	public AttributeList attributesNullable() {return attributes;}

	public void visitCodes(CodeVisitor cv) {
		List<MethodNode> methods = this.methods;
		ByteList tmp = new ByteList();
		for (int j = 0; j < methods.size(); j++) {
			methods.get(j).transform(cp, tmp, cv);
		}
		tmp._free();
	}

	public List<InnerClasses.Item> getInnerClasses() {
		var ic = getAttribute(cp, Attribute.InnerClasses);
		return ic == null ? Collections.emptyList() : ic.classes;
	}

	@Override @NotNull public ConstantPool cp() { return cp; }

	@Override
	public DynByteBuf toByteArray(DynByteBuf w) {
		int begin = w.wIndex();

		ConstantPool cw = this.cp;
		assert autoVerify(w, cw);

		getBytesNoCp(w, cw);

		int pos = w.wIndex();
		int cpl = cw.byteLength() + 10;
		w.preInsert(begin, cpl);

		w.wIndex(begin);
		cw.write(w.putInt(0xCAFEBABE).putInt(version), false);
		w.wIndex(pos + cpl);

		return w;
	}
	private boolean autoVerify(DynByteBuf w, ConstantPool cw) {
		cw.checkCollision(w);
		verify();
		return true;
	}
	public final void getBytesNoCp(DynByteBuf w, ConstantPool cw) {
		w.putShort(modifier)
		 .putShort(cw.fit(nameCst))
		 .putShort(parentCst == null ? 0 : cw.fit(parentCst))
		 .putShort(interfaces.size());

		var interfaces = this.interfaces;
		for (int i = 0; i < interfaces.size(); i++) {
			w.putShort(cw.fit(interfaces.get(i)));
		}

		var fields = this.fields;
		w.putShort(fields.size());
		for (int i = 0, l = fields.size(); i < l; i++) {
			fields.get(i).toByteArray(w, cw);
		}

		var methods = this.methods;
		w.putShort(methods.size());
		for (int i = 0, l = methods.size(); i < l; i++) {
			methods.get(i).toByteArray(w, cw);
		}

		var attr = attributes;
		if (attr == null) w.putShort(0);
		else attr.toByteArray(w, cw);
	}

	public final void parsed() {
		List<MethodNode> mm = methods;
		for (int i = 0; i < mm.size(); i++) mm.get(i).parsed(cp);
		List<FieldNode> ff = fields;
		for (int i = 0; i < ff.size(); i++) ff.get(i).parsed(cp);

		AttributeList list = attributes;
		if (list != null) Attribute.parseAll(this,cp,list,Signature.CLASS);

		cp.clear();
	}
	public final void unparsed() {
		List<MethodNode> mm = methods;
		for (int i = 0; i < mm.size(); i++) mm.get(i).unparsed(cp);
		List<FieldNode> ff = fields;
		for (int i = 0; i < ff.size(); i++) ff.get(i).unparsed(cp);

		AttributeList list = attributes;
		if (list != null) {
			var w = AsmCache.buf();
			for (int i = 0; i < list.size(); i++) list.set(i, UnparsedAttribute.serialize(cp, w, list.get(i)));
			AsmCache.buf(w);
		}
	}

	public final String name() {return nameCst.value().str();}
	@Override public void name(String name) {
		if (name == null) throw new NullPointerException("name");

		if (nameCst == null) nameCst = cp.getClazz(name);
		else cp.setUTFValue(nameCst.value(), name);

		for (int i = 0; i < methods.size(); i++) {
			methods.get(i).owner = name;
		}
	}
	public final String parent() {return parentCst == null ? Helpers.maybeNull() : parentCst.value().str();}
	@Override public final void parent(String name) {parentCst = name == null ? null : cp.getClazz(name);}

	public final void modifier(int flag) { modifier = (char) flag; }
	public final char modifier() { return modifier; }

	public final List<String> interfaces() { return itfView == null ? itfView = new ItfView() : itfView; }
	private class ItfView extends AbstractList<String> {
		@Override public String get(int index) { return interfaces.get(index).value().str(); }
		@Override public String set(int index, String obj) { return interfaces.set(index, cp.getClazz(obj)).value().str(); }
		@Override public void add(int index, String element) { itfList().add(index, cp.getClazz(element)); }
		@Override public String remove(int index) { return interfaces.remove(index).value().str(); }

		@Override
		public boolean remove(Object o) {
			int i = indexOf(o);
			if (i < 0) return false;
			remove(i);
			return true;
		}

		@Override public boolean contains(Object o) { return indexOf(o) >= 0; }
		@Override public int indexOf(Object o) {
			for (int i = 0; i < interfaces.size(); i++) {
				if (interfaces.get(i).value().str().equals(o)) {
					return i;
				}
			}
			return -1;
		}

		@Override public void clear() {interfaces.clear(); }
		@Override public int size() {return interfaces.size();}
	}

	public final void addInterface(String s) {itfList().add(cp.getClazz(s));}
	public ArrayList<CstClass> itfList() {
		if (interfaces instanceof ArrayList<CstClass> c) return c;
		var list = new ArrayList<CstClass>();
		interfaces = list;
		return list;
	}

	public final List<FieldNode> fields() { return fields; }
	public final List<MethodNode> methods() { return methods; }

	@SuppressWarnings("fallthrough")
	public boolean verify() {
		/*
		 * If the ACC_MODULE flag is set in the access_flags item, then no other flag in the access_flags item may be
		 * set, and the following rules apply to the rest of the ClassFile structure:
		 *
		 * major_version, minor_version: ≥ 53.0 (i.e., Java SE 9 and above)
		 *
		 * this_class: module-info
		 *
		 * super_class, interfaces_count, fields_count, methods_count: zero
		 */
		boolean module = (modifier & ACC_MODULE) != 0;
		if (module) {
			if (modifier != ACC_MODULE) throw new IllegalArgumentException("Module should only have 'module' flag");
			if (!interfaces.isEmpty()) throw new IllegalArgumentException("Module should not have interfaces");
			if (!fields.isEmpty()) throw new IllegalArgumentException("Module should not have fields");
			if (!methods.isEmpty()) throw new IllegalArgumentException("Module should not have methods");
		}

		if (parent() == null && !"java/lang/Object".equals(name()) && !module)
			throw new IllegalArgumentException("parent is null in " + name());

		int acc = modifier;
		if (Integer.bitCount(acc&(ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE)) > 1)
			throw new IllegalArgumentException("无效的描述符组合(Acc) "+this);
		if (Integer.bitCount(acc&(ACC_FINAL|ACC_ABSTRACT)) > 1)
			throw new IllegalArgumentException("无效的描述符组合(Fin) "+this);
		if (Integer.bitCount(acc&(ACC_INTERFACE|ACC_ENUM)) > 1)
			throw new IllegalArgumentException("无效的描述符组合(Itf) "+this);

		int v = modifier & (ACC_ANNOTATION|ACC_INTERFACE);
		if (v == ACC_ANNOTATION)
			throw new IllegalArgumentException("无效的描述符组合(ANN) "+this);

		// 如果设置了 ACC_INTERFACE 标志，还必须设置 ACC_ABSTRACT 标志，并且不得设置 ACC_FINAL、ACC_SUPER、ACC_ENUM 和 ACC_MODULE 标志。
		// 如果不设置 ACC_INTERFACE 标志，则可以设置表 4.1-B 中除 ACC_ANNOTATION 和 ACC_MODULE 以外的任何其他标志。
		// 但是，这样的类文件不能同时设置 ACC_FINAL 和 ACC_ABSTRACT 标志（JLS §8.1.1.2）。
		if (v != 0 && (modifier & (ACC_ABSTRACT|ACC_SUPER|ACC_ENUM)) != ACC_ABSTRACT)
			throw new IllegalArgumentException("无效的描述符组合(Itf) "+this);

		HashSet<String> descs = new HashSet<>();
		fastValidate(Helpers.cast(methods), descs);
		descs.clear();
		fastValidate(Helpers.cast(fields), descs);
		return true;
	}
	private void fastValidate(ArrayList<Member> nodes, HashSet<String> out) {
		for (int i = 0; i < nodes.size(); i++) {
			Member n = nodes.get(i);
			if (!out.add(n.name().concat(n.rawDesc())))
				throw new IllegalArgumentException("重复的方法或字段 "+n);

			int acc = n.modifier();
			if (Integer.bitCount(acc&(ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE)) > 1)
				throw new IllegalArgumentException("无效的描述符组合(Acc) "+n);
			if ((acc& ACC_ABSTRACT) != 0 && Integer.bitCount(acc&(ACC_FINAL|ACC_NATIVE|ACC_STATIC)) > 0)
				throw new IllegalArgumentException("无效的描述符组合(Fin) "+n);
		}
	}

	//region ASM Util
	public final MethodNode getMethodObj(String name) { return getMethodObj(name, null); }
	public final MethodNode getMethodObj(String name, String desc) {
		int i = getMethod(name, desc);
		return i < 0 ? null : methods.get(i);
	}

	public final int newField(
			@MagicConstant(flags = ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE | ACC_FINAL | ACC_STATIC | ACC_VOLATILE | ACC_TRANSIENT | ACC_SYNTHETIC) int acc,
			String name, Type type) {
		FieldNode f = new FieldNode(acc, name, type);
		int i = fields.size();
		fields.add(f);
		return i;
	}
	public final int newField(
			@MagicConstant(flags = ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE | ACC_FINAL | ACC_STATIC | ACC_VOLATILE | ACC_TRANSIENT | ACC_SYNTHETIC)
			int acc, String name, String type) {
		FieldNode f = new FieldNode(acc, name, type);
		int i = fields.size();
		fields.add(f);
		return i;
	}

	public final CodeWriter newMethod(
			@MagicConstant(flags = ACC_PUBLIC | ACC_PROTECTED | ACC_PRIVATE | ACC_FINAL | ACC_STATIC | ACC_SYNCHRONIZED | ACC_ABSTRACT | ACC_VARARGS | ACC_SYNTHETIC | ACC_NATIVE)
			int acc, String name, String desc) {
		MethodNode m = new MethodNode(acc, this.name(), name, desc);
		methods.add(m);
		if ((acc & (ACC_ABSTRACT|ACC_NATIVE)) != 0) return Helpers.maybeNull();
		AttrCodeWriter cw = new AttrCodeWriter(cp, m);
		m.addAttribute(cw);
		return cw.cw;
	}

	public void npConstructor() {
		if (getMethod("<init>", "()V") >= 0) return;

		var c = newMethod(ACC_PUBLIC, "<init>", "()V");
		c.visitSize(1, 1);
		c.insn(ALOAD_0);
		c.invoke(INVOKESPECIAL, parent(), "<init>", "()V");
		c.insn(RETURN);
		c.finish();
	}

	public final void cloneable() {cloneable(false);}
	public final void cloneable(boolean invokeSuper) {
		for (int i = 0; i < interfaces.size(); i++) {
			if (interfaces.get(i).value().str().equals("java/lang/Cloneable")) return;
		}
		addInterface("java/lang/Cloneable");

		var c = newMethod(ACC_PUBLIC, "clone", "()Ljava/lang/Object;");

		c.visitSize(1, 1);
		c.insn(Opcodes.ALOAD_0);
		c.invoke(Opcodes.INVOKESPECIAL, invokeSuper ? parent() : "java/lang/Object", "clone", "()Ljava/lang/Object;");
		c.clazz(Opcodes.CHECKCAST, name());
		c.insn(Opcodes.ARETURN);
		c.finish();
	}

	@NotNull
	public final Signature getSignature() {
		Signature signature = getAttribute(cp, Attribute.SIGNATURE);
		if (signature == null) {
			signature = new Signature(Signature.CLASS);
			signature.values = Flow.of(parentCst).append(Flow.of(interfaces)).map(type -> type.value().str().equals("java/lang/Object") ? Signature.placeholder() : Type.klass(type.value().str())).toList();
			addAttribute(signature);
		}
		return signature;
	}
	//endregion

	@Override
	public String toString() {
		CharList sb = new CharList(1000);

		var _a = getAttribute(cp, Attribute.RtAnnotations);
		if (_a != null) _a.toString(sb, 0);
		_a = getAttribute(cp, Attribute.ClAnnotations);
		if (_a != null) _a.toString(sb, 0);

		int acc = modifier;
		if ((acc&ACC_ANNOTATION) != 0) acc &= ~(ACC_ABSTRACT|ACC_INTERFACE);
		else if ((acc&ACC_INTERFACE) != 0) acc &= ~(ACC_ABSTRACT);
		showModifiers(acc, ACC_SHOW_CLASS, sb);

		var _seal = getAttribute(cp, Attribute.PermittedSubclasses);
		if (_seal != null) sb.append("sealed ");

		var _module = getAttribute(cp, Attribute.Module);
		if (_module != null) sb.append(_module.self.toString());
		else {
			if ((acc&(ACC_ENUM|ACC_INTERFACE|ACC_MODULE|ACC_ANNOTATION)) == 0) sb.append("class ");
			TypeHelper.toStringOptionalPackage(sb, name());
		}

		var _sign = getAttribute(cp, Attribute.SIGNATURE);
		if (_sign != null) sb.append(_sign);
		else {
			String parent = parent();
			if (!"java/lang/Object".equals(parent) && parent != null) {
				TypeHelper.toStringOptionalPackage(sb.append(" extends "), parent);
			}

			var _list = interfaces;
			if (!_list.isEmpty()) {
				sb.append(" implements ");
				for (int j = 0; j < _list.size();) {
					String i = _list.get(j).value().str();
					TypeHelper.toStringOptionalPackage(sb, i);
					if (++j == _list.size()) break;
					sb.append(", ");
				}
			}
		}
		if (_seal != null) {
			sb.append(" permits ");
			var _list = _seal.value;
			for (int j = 0; j < _list.size();) {
				String i = _list.get(j);
				TypeHelper.toStringOptionalPackage(sb, i);
				if (++j == _list.size()) break;
				sb.append(", ");
			}
		}

		sb.append(" {");
		if (_module != null) {
			_module.writeModuleInfo(sb);
		}
		if (!fields.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < fields.size(); i++) {
				fields.get(i).toString(sb, this, 4, true).append("\n");
			}
		}
		if (!methods.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < methods.size(); i++) {
				methods.get(i).toString(sb, this, 4).append('\n');
			}
		}

		return sb.append('}').toStringAndFree();
	}
}