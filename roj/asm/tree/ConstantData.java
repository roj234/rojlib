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
import roj.asm.visitor.CodeWriter;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.reflect.FieldAccessor;
import roj.util.DynByteBuf;
import roj.util.Helpers;
import roj.util.TypedName;

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
	private static final long N, P;
	static {
		try {
			N = FieldAccessor.u.objectFieldOffset(ConstantData.class.getDeclaredField("name"));
			P = FieldAccessor.u.objectFieldOffset(ConstantData.class.getDeclaredField("parent"));
		} catch (NoSuchFieldException e) {
			throw new RuntimeException(e);
		}
	}

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
			if (access != AccessFlag.MODULE) {
				throw new IllegalArgumentException("Module should only have 'module' flag");
			}
			if (!interfaces.isEmpty()) {
				throw new IllegalArgumentException("Module should not have interfaces");
			}
			if (!fields.isEmpty()) {
				throw new IllegalArgumentException("Module should not have fields");
			}
			if (!methods.isEmpty()) {
				throw new IllegalArgumentException("Module should not have methods");
			}
		}

		if (parent == null && !"java/lang/Object".equals(name) && !module) {
			throw new IllegalArgumentException("No father found " + name);
		}

		int permDesc = 0;
		int typeDesc = 0;
		int fn = -1;

		for (int i = 0; i < 16; i++) {
			int v = 1 << i;
			if ((access & v) != 0) {
				switch (v) {
					case PUBLIC:
					case PROTECTED:
						permDesc++;
						break;
					case INTERFACE:
					case ENUM:
						typeDesc++;
						break;
					case FINAL:
						if (fn == 0) {
							throw new IllegalArgumentException("Final and Abstract");
						}
						fn = 1;
						break;
					case ABSTRACT:
						if (fn == 1) {
							throw new IllegalArgumentException("Final and Abstract");
						}
						fn = 0;
						break;
					case ANNOTATION:
					case SUPER:
					case SYNTHETIC:
					case STRICTFP:
					case BRIDGE:
						break;
					case MODULE:
						if (access == MODULE) break;
					default:
						throw new IllegalArgumentException("Unsupported access flag " + v + "/" + (int) access);
				}
			}
		}

		int v = access & (ANNOTATION | INTERFACE);
		if (v == ANNOTATION) throw new IllegalArgumentException("Not valid @interface " + access);

		if (permDesc > 1) {
			throw new IllegalArgumentException("ACCPermission too much " + access);
		}

		if (typeDesc > 1) {
			throw new IllegalArgumentException("ACCType too much " + access);
		}

		MyHashSet<String> descs = new MyHashSet<>();
		for (int j = 0; j < methods.size(); j++) {
			MoFNode method = methods.get(j);
			if (!descs.add(method.name() + '|' + method.rawDesc())) {
				throw new IllegalArgumentException("Duplicate method " + method.name() + method.rawDesc());
			}
		}
		descs.clear();

		for (int j = 0; j < fields.size(); j++) {
			MoFNode field = fields.get(j);
			if (!descs.add(field.name() + '|' + field.rawDesc())) {
				throw new IllegalArgumentException("Duplicate field " + field.name() + field.rawDesc());
			}
		}
	}

	public ConstantData() {
		this.cp = new ConstantPool();
		this.name = null;
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
	public <T extends Attribute> T parsedAttr(ConstantPool cp, TypedName<T> type) { return Parser.parseAttribute(this,this.cp,type,attributes,Signature.CLASS); }
	public Attribute attrByName(String name) {
		return attributes == null ? null : (Attribute) attributes.getByName(name);
	}
	public AttributeList attributes() { return attributes == null ? attributes = new AttributeList() : attributes; }
	public AttributeList attributesNullable() { return attributes; }

	@Override
	public ConstantPool cp() {
		return cp;
	}

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

		List<? extends FieldNode> fields = this.fields;
		w.putShort(fields.size());
		for (int i = 0, l = fields.size(); i < l; i++) {
			fields.get(i).toByteArray(w, cw);
		}

		List<? extends MethodNode> methods = this.methods;
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

	public void normalize() {
		List<? extends MethodNode> methods = this.methods;
		for (int i = 0; i < methods.size(); i++) {
			if (methods.get(i) instanceof Method) {
				methods.set(i, Helpers.cast(((Method) methods.get(i)).i_downgrade(cp)));
			}
		}
		List<? extends MoFNode> fields = this.fields;
		for (int i = 0; i < fields.size(); i++) {
			if (fields.get(i) instanceof Field) {
				fields.set(i, Helpers.cast(((Field) fields.get(i)).i_downgrade(cp)));
			}
		}

		AttributeList attrs = attributes;
		if (attrs != null) {
			DynByteBuf w = AsmShared.getBuf();
			for (int i = 0; i < attrs.size(); i++) {
				attrs.set(i, AttrUnknown.downgrade(cp, w, attrs.get(i)));
			}
		}
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(1000);

		Attribute a = parsedAttr(cp, Attribute.RtAnnotations);
		if (a != null) sb.append(a);
		a = parsedAttr(cp, Attribute.ClAnnotations);
		if (a != null) sb.append(a);

		AccessFlag.toString(access, AccessFlag.TS_CLASS, sb);
		sb.append(name.substring(name.lastIndexOf('/')+1));
		a = parsedAttr(cp, Attribute.SIGNATURE);
		if (a != null) {
			sb.append(a);
		} else {
			if (!"java/lang/Object".equals(parent) && parent != null) sb.append(" extends ").append(parent);
			if (interfaces.size() > 0) {
				sb.append(" implements ");
				for (CstClass c : interfaces) {
					String i = c.name().str();
					sb.append(i.substring(i.lastIndexOf('/')+1)).append(", ");
				}
				sb.delete(sb.length() - 2, sb.length());
			}
		}
		if (!fields.isEmpty()) {
			sb.append("\n\n");
			for (int i = 0; i < fields.size(); i++) {
				sb.append(fields.get(i)).append("\n");
			}
			sb.deleteCharAt(sb.length() - 1);
		}
		if (!methods.isEmpty()) {
			sb.append("\n\n");
			for (int i = 0; i < methods.size(); i++) {
				sb.append(methods.get(i)).append("\n");
			}
			sb.deleteCharAt(sb.length() - 1);
		}

		return sb.append("\n").toString();
	}

	public final String name() { return nameCst.name().str(); }
	public final String parent() { return parentCst == null ? null : parentCst.name().str(); }

	public final void modifier(int flag) { access = (char) flag; }
	public final char modifier() { return access; }

	public final List<String> interfaces() { return itfView == null ? itfView = new ItfView() : itfView; }
	public final List<? extends MoFNode> methods() { return methods; }
	public final List<? extends MoFNode> fields() { return fields; }

	public final int type() { return Parser.CTYPE_PARSED; }

	public final void dump() {
		try (FileOutputStream fos = new FileOutputStream(name.replace('/', '.') + ".class")) {
			Parser.toByteArrayShared(this).writeToStream(fos);
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	public final Method getUpgradedMethod(int i) {
		MoFNode ms = methods.get(i);
		if (ms instanceof Method) return (Method) ms;
		Method m = new Method(this, (RawMethod) ms);
		methods.set(i, Helpers.cast(m));
		return m;
	}
	public final Method getUpgradedMethod(String name) {
		return getUpgradedMethod(name, null);
	}
	public final Method getUpgradedMethod(String name, String desc) {
		List<? extends MoFNode> methods = this.methods;
		for (int i = 0; i < methods.size(); i++) {
			MoFNode ms = methods.get(i);
			if (ms.name().equals(name) && (desc == null || ms.rawDesc().equals(desc))) {
				if (ms instanceof Method) return (Method) ms;
				Method m = new Method(this, (RawMethod) ms);
				methods.set(i, Helpers.cast(m));
				return m;
			}
		}
		return null;
	}

	@Override
	public final void name(String name) {
		if (name == null) throw new NullPointerException("name");

		if (nameCst == null) nameCst = cp.getClazz(name);
		else {
			for (int i = 0; i < methods.size(); i++) {
				MethodNode mn = methods.get(i);
				if (mn instanceof RawMethod) {
					((RawMethod) mn).cn(name);
				} else {
					((Method) mn).owner = name;
				}
			}
			cp.setUTFValue(nameCst.name(), name);
		}

		FieldAccessor.u.putObject(this, N, name);
	}
	@Override
	public final void parent(String name) {
		parentCst = name == null ? null : cp.getClazz(name);

		FieldAccessor.u.putObject(this, P, name);
	}

	public final int newField(int acc, String name, Type clazz) {
		return newField(acc,name, TypeHelper.getField(clazz));
	}
	public final int newField(int acc, String name, String type) {
		RawField f0 = new RawField(acc, cp.getUtf(name), cp.getUtf(type));
		int id = fields.size();
		fields.add(Helpers.cast(f0));
		return id;
	}

	public final CodeWriter newMethod(int acc, String name, String desc) {
		RawMethod m0 = new RawMethod(acc, cp.getUtf(name), cp.getUtf(desc));
		m0.cn(this.name);
		methods.add(Helpers.cast(m0));
		if ((acc & (ABSTRACT|NATIVE)) != 0) return Helpers.nonnull();
		AttrCodeWriter cw = new AttrCodeWriter(cp, m0);
		m0.putAttr(cw);
		return cw.cw;
	}

	public void npConstructor() {
		if (getMethod("<init>", "()V") >= 0) return;

		CodeWriter cw = newMethod(AccessFlag.PUBLIC, "<init>", "()V");
		cw.visitSize(1, 1);
		cw.one(ALOAD_0);
		cw.invoke(INVOKESPECIAL, parent, "<init>", "()V");
		cw.one(RETURN);
		cw.finish();
	}

	public final void cloneable() {
		cloneable(false);
	}
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
		public String get(int index) {
			return interfaces.get(index).name().str();
		}

		@Override
		public String set(int index, String obj) {
			CstClass prev = interfaces.set(index, cp.getClazz(obj));
			return prev.name().str();
		}

		@Override
		public void add(int index, String element) {
			interfaces.add(index, cp.getClazz(element));
		}

		@Override
		public String remove(int index) {
			return interfaces.remove(index).name().str();
		}

		@Override
		public boolean remove(Object o) {
			int i = indexOf(o);
			if (i < 0) return false;
			remove(i);
			return true;
		}

		@Override
		public boolean contains(Object o) {
			return indexOf(o) >= 0;
		}

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
		public void clear() {
			interfaces.clear();
		}

		@Override
		public int size() {
			return interfaces.size();
		}
	}

	public final void addInterface(String s) {
		interfaces.add(cp.getClazz(s));
	}
}