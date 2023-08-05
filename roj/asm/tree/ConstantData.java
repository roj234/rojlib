package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cst.ConstantPool;
import roj.asm.cst.CstClass;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.util.AccessFlag;
import roj.asm.util.AttributeList;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeVisitor;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.util.AttributeKey;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.FileOutputStream;
import java.util.AbstractList;
import java.util.List;

import static roj.asm.Opcodes.*;
import static roj.asm.util.AccessFlag.*;

/**
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public class ConstantData implements IClass {
	private static final long
		N = ReflectionUtils.fieldOffset(ConstantData.class, "name"),
		P = ReflectionUtils.fieldOffset(ConstantData.class, "parent");

	public int version;

	public char access;

	private CstClass nameCst, parentCst;
	private ItfView itfView;

	public final String name, parent;

	public ConstantPool cp;

	public final SimpleList<CstClass> interfaces = new SimpleList<>();
	public final SimpleList<FieldNode> fields = new SimpleList<>();
	public final SimpleList<MethodNode> methods = new SimpleList<>();

	private AttributeList attributes;

	@SuppressWarnings("fallthrough")
	public void verify() {
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
		boolean module = (access & AccessFlag.MODULE) != 0;
		if (module) {
			if (access != AccessFlag.MODULE) throw new IllegalArgumentException("Module should only have 'module' flag");
			if (!interfaces.isEmpty()) throw new IllegalArgumentException("Module should not have interfaces");
			if (!fields.isEmpty()) throw new IllegalArgumentException("Module should not have fields");
			if (!methods.isEmpty()) throw new IllegalArgumentException("Module should not have methods");
		}

		if (parent == null && !"java/lang/Object".equals(name) && !module)
			throw new IllegalArgumentException("parent is null in " + name);

		int acc = access;
		if (Integer.bitCount(acc&(PUBLIC|PROTECTED|PRIVATE)) > 1)
			throw new IllegalArgumentException("无效的描述符组合(Acc) "+this);
		if (Integer.bitCount(acc&(FINAL|ABSTRACT)) > 1)
			throw new IllegalArgumentException("无效的描述符组合(Fin) "+this);
		if (Integer.bitCount(acc&(INTERFACE|ENUM)) > 1)
			throw new IllegalArgumentException("无效的描述符组合(Itf) "+this);

		int v = access & (ANNOTATION | INTERFACE);
		if (v == ANNOTATION)
			throw new IllegalArgumentException("无效的描述符组合(ANN) "+this);

		// 如果设置了 ACC_INTERFACE 标志，还必须设置 ACC_ABSTRACT 标志，并且不得设置 ACC_FINAL、ACC_SUPER、ACC_ENUM 和 ACC_MODULE 标志。
		// 如果不设置 ACC_INTERFACE 标志，则可以设置表 4.1-B 中除 ACC_ANNOTATION 和 ACC_MODULE 以外的任何其他标志。
		// 但是，这样的类文件不能同时设置 ACC_FINAL 和 ACC_ABSTRACT 标志（JLS §8.1.1.2）。
		if (v != 0 && (access & (ABSTRACT|SUPER|ENUM)) != ABSTRACT)
			throw new IllegalArgumentException("无效的描述符组合(Itf) "+this);

		MyHashSet<String> descs = new MyHashSet<>();
		fastValidate(Helpers.cast(methods), descs);
		descs.clear();
		fastValidate(Helpers.cast(fields), descs);
	}

	private void fastValidate(SimpleList<RawNode> nodes, MyHashSet<String> out) {
		for (int i = 0; i < nodes.size(); i++) {
			RawNode n = nodes.get(i);
			if (!out.add(n.name().concat(n.rawDesc())))
				throw new IllegalArgumentException("重复的方法或字段 "+n);

			int acc = n.modifier();
			if (Integer.bitCount(acc&(PUBLIC|PROTECTED|PRIVATE)) > 1)
				throw new IllegalArgumentException("无效的描述符组合(Acc) "+n);
			if ((acc&ABSTRACT) != 0 && Integer.bitCount(acc&(FINAL|NATIVE|STATIC)) > 0)
				throw new IllegalArgumentException("无效的描述符组合(Fin) "+n);
		}
	}

	public ConstantData() {
		this.cp = new ConstantPool();
		this.name = Helpers.nonnull();
		this.parent = "java/lang/Object";
		this.access = AccessFlag.PUBLIC | AccessFlag.SUPER;
		this.version = 49 << 16;
	}

	public ConstantData(int version, ConstantPool cp, int access, int nameIndex, int parentIndex) {
		this.cp = cp;
		this.version = version;
		this.access = (char) access;
		this.nameCst = ((CstClass) cp.array(nameIndex));
		this.name = nameCst.name().str();
		if (parentIndex == 0) {
			this.parentCst = null;
			this.parent = Helpers.nonnull();
		} else {
			this.parentCst = ((CstClass) cp.array(parentIndex));
			this.parent = parentCst.name().str();
		}
	}

	@Override
	public <T extends Attribute> T parsedAttr(ConstantPool cp, AttributeKey<T> type) { return Parser.parseAttribute(this,this.cp,type,attributes,Signature.CLASS); }
	public Attribute attrByName(String name) {
		return attributes == null ? null : (Attribute) attributes.getByName(name);
	}
	public AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	public AttributeList attributesNullable() { return attributes; }

	public void forEachCode(CodeVisitor cv, String name, String desc) {
		int i = getMethod(name, desc);
		if (i < 0) throw new IllegalArgumentException("No such method");

		methods.get(i).forEachCode(cv, cp);
	}
	public void forEachCode(CodeVisitor cv) {
		List<MethodNode> methods = this.methods;
		for (int j = 0; j < methods.size(); j++)
			methods.get(j).forEachCode(cv, cp);
	}

	@Override
	public ConstantPool cp() { return cp; }

	@Override
	public DynByteBuf getBytes(DynByteBuf w) {
		ConstantPool cw = this.cp;

		w.putShort(access)
		 .putShort(cw.reset(nameCst).getIndex())
		 .putShort(parent == null ? 0 : parentCst == null ? (parentCst = cw.getClazz(parent)).getIndex() : cw.reset(parentCst).getIndex())
		 .putShort(interfaces.size());

		List<CstClass> interfaces = this.interfaces;
		for (int i = 0; i < interfaces.size(); i++) {
			w.putShort(cw.reset(interfaces.get(i)).getIndex());
		}

		List<FieldNode> fields = this.fields;
		w.putShort(fields.size());
		for (int i = 0, l = fields.size(); i < l; i++) {
			fields.get(i).toByteArray(w, cw);
		}

		List<MethodNode> methods = this.methods;
		w.putShort(methods.size());
		for (int i = 0, l = methods.size(); i < l; i++) {
			methods.get(i).toByteArray(w, cw);
		}

		AttributeList attr = attributes;
		if (attr == null) w.putShort(0);
		else attr.toByteArray(w, cw);

		int pos = w.wIndex();
		int cpl = cw.byteLength() + 10;
		w.preInsert(0, cpl);

		w.wIndex(0);
		cw.write(w.putInt(0xCAFEBABE).putShort(version).putShort(version >> 16));
		assert w.wIndex() == cpl;
		w.wIndex(pos + cpl);

		return w;
	}

	public final void parsed() {
		List<MethodNode> mm = methods;
		for (int i = 0; i < mm.size(); i++) mm.get(i).parsed(cp);
		List<FieldNode> ff = fields;
		for (int i = 0; i < ff.size(); i++) ff.get(i).parsed(cp);

		AttributeList list = attributes;
		if (list != null) Parser.parseAttributes(this,cp,list,Signature.CLASS);

		cp.clear();
	}
	public final void unparsed() {
		List<MethodNode> mm = methods;
		for (int i = 0; i < mm.size(); i++) mm.get(i).unparsed(cp);
		List<FieldNode> ff = fields;
		for (int i = 0; i < ff.size(); i++) ff.get(i).unparsed(cp);

		AttributeList list = attributes;
		if (list != null) {
			DynByteBuf w = AsmShared.getBuf();
			for (int i = 0; i < list.size(); i++) list.set(i, AttrUnknown.downgrade(cp, w, list.get(i)));
		}
	}

	@Override
	public String toString() {
		CharList sb = new CharList(1000);

		Attribute a = parsedAttr(cp, Attribute.RtAnnotations);
		if (a != null) sb.append(a).append('\n');
		a = parsedAttr(cp, Attribute.ClAnnotations);
		if (a != null) sb.append(a).append('\n');

		int acc = access;
		if ((acc&ANNOTATION) != 0) acc &= ~(ABSTRACT|INTERFACE);
		else if ((acc&INTERFACE) != 0) acc &= ~(ABSTRACT);

		AccessFlag.toString(acc, AccessFlag.TS_CLASS, sb);
		if ((acc&(ENUM|INTERFACE|MODULE|ANNOTATION)) == 0) sb.append("class ");

		TypeHelper.toStringOptionalPackage(sb, name);
		a = parsedAttr(cp, Attribute.SIGNATURE);
		if (a != null) {
			sb.append(a);
		} else {
			if (!"java/lang/Object".equals(parent) && parent != null) {
				TypeHelper.toStringOptionalPackage(sb.append(" extends "), parent);
			}
			if (interfaces.size() > 0) {
				sb.append(" implements ");
				for (int j = 0; j < interfaces.size(); j++) {
					String i = interfaces.get(j).name().str();
					TypeHelper.toStringOptionalPackage(sb, i);
					if (++j == interfaces.size()) break;
					sb.append(", ");
				}
			}
		}
		sb.append(" {\n");
		if (!fields.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < fields.size(); i++) {
				sb.append(fields.get(i)).append("\n");
			}
		}
		if (!methods.isEmpty()) {
			sb.append('\n');
			for (int i = 0; i < methods.size(); i++) {
				methods.get(i).toString(sb, this).append('\n');
			}
		}

		return sb.append('}').toStringAndFree();
	}

	public final String name() { return nameCst.name().str(); }
	public final String parent() { return parentCst == null ? null : parentCst.name().str(); }

	public final void modifier(int flag) { access = (char) flag; }
	public final char modifier() { return access; }

	public final List<String> interfaces() { return itfView == null ? itfView = new ItfView() : itfView; }
	public final List<MethodNode> methods() { return methods; }
	public final List<FieldNode> fields() { return fields; }

	public final void dump() {
		try (FileOutputStream fos = new FileOutputStream(name.replace('/', '.') + ".class")) {
			Parser.toByteArrayShared(this).writeToStream(fos);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public final MethodNode getMethodObj(String name) { return getMethodObj(name, null); }
	public final MethodNode getMethodObj(String name, String desc) {
		int i = getMethod(name, desc);
		return i < 0 ? null : methods.get(i);
	}

	@Override
	public final void name(String name) {
		if (name == null) throw new NullPointerException("name");

		if (nameCst == null) nameCst = cp.getClazz(name);
		else {
			for (int i = 0; i < methods.size(); i++) {
				methods.get(i).owner = name;
			}
			cp.setUTFValue(nameCst.name(), name);
		}

		ReflectionUtils.u.putObject(this, N, name);
	}
	@Override
	public final void parent(String name) {
		parentCst = name == null ? null : cp.getClazz(name);

		ReflectionUtils.u.putObject(this, P, name);
	}

	public final int newField(int acc, String name, Type type) {
		FieldNode f = new FieldNode(acc, name, type);
		int i = fields.size();
		fields.add(f);
		return i;
	}
	public final int newField(int acc, String name, String type) {
		FieldNode f = new FieldNode(acc, name, type);
		int i = fields.size();
		fields.add(f);
		return i;
	}

	public final CodeWriter newMethod(int acc, String name, String desc) {
		MethodNode m = new MethodNode(acc, this.name, name, desc);
		methods.add(m);
		if ((acc & (ABSTRACT|NATIVE)) != 0) return Helpers.nonnull();
		AttrCodeWriter cw = new AttrCodeWriter(cp, m);
		m.putAttr(cw);
		return cw.cw;
	}

	public void npConstructor() {
		if (getMethod("<init>", "()V") >= 0) return;

		CodeWriter c = newMethod(AccessFlag.PUBLIC, "<init>", "()V");
		c.visitSize(1, 1);
		c.one(ALOAD_0);
		c.invoke(INVOKESPECIAL, parent, "<init>", "()V");
		c.one(RETURN);
		c.finish();
	}

	public final void cloneable() { cloneable(false); }
	public final void cloneable(boolean invokeSuper) {
		for (int i = 0; i < interfaces.size(); i++) {
			if (interfaces.get(i).name().str().equals("java/lang/Cloneable")) return;
		}

		CodeWriter c = newMethod(PUBLIC, "clone", "()Ljava/lang/Object;");

		c.visitSize(1, 1);
		c.one(Opcodes.ALOAD_0);
		c.invoke(Opcodes.INVOKESPECIAL, invokeSuper?parent:"java/lang/Object", "clone", "()Ljava/lang/Object;");
		c.clazz(Opcodes.CHECKCAST, name);
		c.one(Opcodes.ARETURN);
		c.finish();

		interfaces.add(new CstClass("java/lang/Cloneable"));
	}

	private class ItfView extends AbstractList<String> {
		@Override
		public String get(int index) { return interfaces.get(index).name().str(); }
		@Override
		public String set(int index, String obj) { return interfaces.set(index, cp.getClazz(obj)).name().str(); }
		@Override
		public void add(int index, String element) { interfaces.add(index, cp.getClazz(element)); }
		@Override
		public String remove(int index) { return interfaces.remove(index).name().str(); }

		@Override
		public boolean remove(Object o) {
			int i = indexOf(o);
			if (i < 0) return false;
			remove(i);
			return true;
		}

		@Override
		public boolean contains(Object o) { return indexOf(o) >= 0; }
		@Override
		public int indexOf(Object o) {
			for (int i = 0; i < interfaces.size(); i++) {
				if (interfaces.get(i).name().str().equals(o)) {
					return i;
				}
			}
			return -1;
		}

		@Override
		public void clear() { interfaces.clear(); }
		@Override
		public int size() { return interfaces.size(); }
	}

	public final void addInterface(String s) {
		interfaces.add(cp.getClazz(s));
	}
}