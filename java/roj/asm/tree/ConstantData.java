package roj.asm.tree;

import roj.asm.AsmShared;
import roj.asm.Opcodes;
import roj.asm.Parser;
import roj.asm.cp.ConstantPool;
import roj.asm.cp.CstClass;
import roj.asm.tree.attr.AttrUnknown;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.AttributeList;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.Signature;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.AttrCodeWriter;
import roj.asm.visitor.CodeVisitor;
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.reflect.ReflectionUtils;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedKey;

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import static roj.asm.Opcodes.*;

/**
 * @author Roj234
 * @since 2021/5/30 19:59
 */
public class ConstantData implements IClass {
	private static final long
		N = ReflectionUtils.fieldOffset(ConstantData.class, "name"),
		P = ReflectionUtils.fieldOffset(ConstantData.class, "parent");

	public int version;

	public char modifier;

	private CstClass nameCst, parentCst;
	private ItfView itfView;

	public final String name, parent;

	public ConstantPool cp;

	public SimpleList<CstClass> interfaceWritable() {
		if (interfaces instanceof SimpleList<CstClass> c) return c;
		var list = new SimpleList<CstClass>();
		interfaces = list;
		return list;
	}

	protected List<CstClass> interfaces = Collections.emptyList();
	public final SimpleList<FieldNode> fields = new SimpleList<>();
	public final SimpleList<MethodNode> methods = new SimpleList<>();

	private AttributeList attributes;

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

		if (parent == null && !"java/lang/Object".equals(name) && !module)
			throw new IllegalArgumentException("parent is null in " + name);

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

		MyHashSet<String> descs = new MyHashSet<>();
		fastValidate(Helpers.cast(methods), descs);
		descs.clear();
		fastValidate(Helpers.cast(fields), descs);
		return true;
	}

	private void fastValidate(SimpleList<RawNode> nodes, MyHashSet<String> out) {
		for (int i = 0; i < nodes.size(); i++) {
			RawNode n = nodes.get(i);
			if (!out.add(n.name().concat(n.rawDesc())))
				throw new IllegalArgumentException("重复的方法或字段 "+n);

			int acc = n.modifier();
			if (Integer.bitCount(acc&(ACC_PUBLIC|ACC_PROTECTED|ACC_PRIVATE)) > 1)
				throw new IllegalArgumentException("无效的描述符组合(Acc) "+n);
			if ((acc& ACC_ABSTRACT) != 0 && Integer.bitCount(acc&(ACC_FINAL|ACC_NATIVE|ACC_STATIC)) > 0)
				throw new IllegalArgumentException("无效的描述符组合(Fin) "+n);
		}
	}

	public ConstantData() {
		this.cp = new ConstantPool();
		this.name = Helpers.nonnull();
		this.parent = "java/lang/Object";
		this.modifier = ACC_PUBLIC | ACC_SUPER;
		this.version = 49 << 16;
	}
	public static int JavaVersion(int version) { return (44+version) << 16; }

	public ConstantData(int version, ConstantPool cp, int modifier, int nameIndex, int parentIndex) {
		this.cp = cp;
		this.version = version;
		this.modifier = (char) modifier;
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
	public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedKey<T> type) { return Parser.parseAttribute(this,this.cp,type,attributes,Signature.CLASS); }
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

	public List<InnerClasses.Item> getInnerClasses() {
		var ic = parsedAttr(cp, Attribute.InnerClasses);
		return ic == null ? Collections.emptyList() : ic.classes;
	}

	@Override public ConstantPool cp() { return cp; }

	@Override
	public DynByteBuf getBytes(DynByteBuf w) {
		int begin = w.wIndex();

		ConstantPool cw = this.cp;
		assert autoVerify(w, cw);

		getBytesNoCp(w, cw);

		int pos = w.wIndex();
		int cpl = cw.byteLength() + 10;
		w.preInsert(begin, cpl);

		w.wIndex(begin);
		cw.write(w.putInt(0xCAFEBABE).putShort(version).putShort(version >> 16), false);
		w.wIndex(pos + cpl);

		return w;
	}
	public final void getBytesNoCp(DynByteBuf w, ConstantPool cw) {
		w.putShort(modifier)
		 .putShort(cw.reset(nameCst).getIndex())
		 .putShort(parent == null ? 0 : parentCst == null ? (parentCst = cw.getClazz(parent)).getIndex() : cw.reset(parentCst).getIndex())
		 .putShort(interfaces.size());

		var interfaces = this.interfaces;
		for (int i = 0; i < interfaces.size(); i++) {
			w.putShort(cw.reset(interfaces.get(i)).getIndex());
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

	private boolean autoVerify(DynByteBuf w, ConstantPool cw) {
		cw.checkCollision(w);
		verify();
		return true;
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

		var _a = parsedAttr(cp, Attribute.RtAnnotations);
		if (_a != null) _a.toString(sb, 0);
		_a = parsedAttr(cp, Attribute.ClAnnotations);
		if (_a != null) _a.toString(sb, 0);

		int acc = modifier;
		if ((acc&ACC_ANNOTATION) != 0) acc &= ~(ACC_ABSTRACT|ACC_INTERFACE);
		else if ((acc&ACC_INTERFACE) != 0) acc &= ~(ACC_ABSTRACT);
		showModifiers(acc, ACC_SHOW_CLASS, sb);

		var _seal = parsedAttr(cp, Attribute.PermittedSubclasses);
		if (_seal != null) sb.append("sealed ");

		var _module = parsedAttr(cp, Attribute.Module);
		if (_module != null) sb.append( _module.self.name);
		else {
			if ((acc&(ACC_ENUM|ACC_INTERFACE|ACC_MODULE|ACC_ANNOTATION)) == 0) sb.append("class ");
			TypeHelper.toStringOptionalPackage(sb, name);
		}

		var _sign = parsedAttr(cp, Attribute.SIGNATURE);
		if (_sign != null) sb.append(_sign);
		else {
			if (!"java/lang/Object".equals(parent) && parent != null) {
				TypeHelper.toStringOptionalPackage(sb.append(" extends "), parent);
			}

			var _list = interfaces;
			if (!_list.isEmpty()) {
				sb.append(" implements ");
				for (int j = 0; j < _list.size();) {
					String i = _list.get(j).name().str();
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

	public final String name() { return nameCst.name().str(); }
	public final String parent() { return parentCst == null ? Helpers.maybeNull() : parentCst.name().str(); }

	public final void modifier(int flag) { modifier = (char) flag; }
	public final char modifier() { return modifier; }

	public final List<String> interfaces() { return itfView == null ? itfView = new ItfView() : itfView; }
	public final List<MethodNode> methods() { return methods; }
	public final List<FieldNode> fields() { return fields; }

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
		if ((acc & (ACC_ABSTRACT|ACC_NATIVE)) != 0) return Helpers.nonnull();
		AttrCodeWriter cw = new AttrCodeWriter(cp, m);
		m.putAttr(cw);
		return cw.cw;
	}

	public void npConstructor() {
		if (getMethod("<init>", "()V") >= 0) return;

		CodeWriter c = newMethod(ACC_PUBLIC, "<init>", "()V");
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

		CodeWriter c = newMethod(ACC_PUBLIC, "clone", "()Ljava/lang/Object;");

		c.visitSize(1, 1);
		c.one(Opcodes.ALOAD_0);
		c.invoke(Opcodes.INVOKESPECIAL, invokeSuper?parent:"java/lang/Object", "clone", "()Ljava/lang/Object;");
		c.clazz(Opcodes.CHECKCAST, name);
		c.one(Opcodes.ARETURN);
		c.finish();

		addInterface("java/lang/Cloneable");
	}

	private class ItfView extends AbstractList<String> {
		@Override
		public String get(int index) { return interfaces.get(index).name().str(); }
		@Override
		public String set(int index, String obj) { return interfaces.set(index, cp.getClazz(obj)).name().str(); }
		@Override
		public void add(int index, String element) { interfaceWritable().add(index, cp.getClazz(element)); }
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
	public final void addInterface(String s) {interfaceWritable().add(cp.getClazz(s));}
}