package roj.compiler.resolve;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.RawNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asmx.AnnotationSelf;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.context.ClassContext;

import java.util.Iterator;
import java.util.List;

import static roj.asmx.AnnotationSelf.*;

/**
 * @author Roj234
 * @since 2024/2/6 0006 2:40
 */
public final class ResolveHelper {
	private Object _next;
	public final IClass owner;

	public ResolveHelper(IClass owner) {this.owner = owner;}

	private IntBiMap<String> classList;

	public synchronized IntBiMap<String> getClassList(ClassContext ctx) throws ClassNotFoundException {
		if (classList != null) return classList;

		IntBiMap<String> classList = new IntBiMap<>();

		String owner = this.owner.name();
		while (true) {
			IClass info = ctx.getClassInfo(owner);
			if (info == null) throw new ClassNotFoundException(owner);
			owner = info.parent();
			if (owner == null) break;
			classList.putInt(classList.size(), owner);
		}

		owner = this.owner.name();
		do {
			IClass classInfo = ctx.getClassInfo(owner);

			List<String> interfaces = classInfo.interfaces();
			for (int i = 0; i < interfaces.size(); i++) {
				classList.putInt(classList.size(), interfaces.get(i));
			}

			owner = classInfo.parent();
		} while (owner != null);

		return this.classList = classList;
	}

	// region 注解
	private AnnotationSelf ac;

	public synchronized AnnotationSelf annotationInfo() {
		if (ac != null || (owner.modifier() & Opcodes.ACC_ANNOTATION) == 0) return ac;

		AnnotationSelf ac = this.ac = new AnnotationSelf();
		Annotations attr = owner.parsedAttr(owner.cp(), Attribute.RtAnnotations);
		if (attr == null) return ac;

		List<Annotation> list = attr.annotations;
		for (int i = 0; i < list.size(); i++) {
			Annotation a = list.get(i);
			switch (a.type) {
				case "java/lang/annotation/Retention":
					if (!a.containsKey("value")) return null;
					switch (a.getEnumValue("value", "RUNTIME")) {
						case "SOURCE":
							ac.kind = SOURCE;
							break;
						case "CLASS":
							ac.kind = CLASS;
							break;
						case "RUNTIME":
							ac.kind = RUNTIME;
							break;
					}
					break;
				case "java/lang/annotation/Repeatable":
					if (!a.containsKey("value")) return null;
					ac.repeatOn = a.getClass("value").owner;
					break;
				case "java/lang/annotation/Target":
					int tmp = 0;
					if (!a.containsKey("value")) return null;
					List<AnnVal> array = a.getArray("value");
					for (int j = 0; j < array.size(); j++) {
						switch (array.get(j).asEnum().field) {
							case "TYPE":
								tmp |= TYPE;
								break;
							case "FIELD":
								tmp |= FIELD;
								break;
							case "METHOD":
								tmp |= METHOD;
								break;
							case "PARAMETER":
								tmp |= PARAMETER;
								break;
							case "CONSTRUCTOR":
								tmp |= CONSTRUCTOR;
								break;
							case "LOCAL_VARIABLE":
								tmp |= LOCAL_VARIABLE;
								break;
							case "ANNOTATION_TYPE":
								tmp |= ANNOTATION_TYPE;
								break;
							case "PACKAGE":
								tmp |= PACKAGE;
								break;
							case "TYPE_PARAMETER":
								tmp |= TYPE_PARAMETER;
								break;
							case "TYPE_USE":
								tmp |= TYPE_USE;
								break;
						}
					}
					ac.applicableTo = tmp;
					break;
			}
		}
		return ac;
	}

	// endregion
	// region 方法
	private MyHashMap<String, ComponentList> methods;

	public synchronized ComponentList findMethod(ClassContext ctx, String name) {
		if (methods == null) {
			methods = new MyHashMap<>();

			List<? extends RawNode> methods1 = owner.methods();
			for (int i = 0; i < methods1.size(); i++) {
				MethodNode mn = (MethodNode) methods1.get(i);
				if (mn.name().equals("<clinit>") || (mn.modifier & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) continue;

				MethodList ml = (MethodList) methods.get(mn.name());
				if (ml == null) methods.put(mn.name(), ml = new MethodList());
				ml.add(owner, mn);
			}

			IClass type = owner;
			List<Object> tmp = new SimpleList<>(); tmp.add(type);

			String parent = type.parent();
			if (parent != null) {
				type = ctx.getClassInfo(parent);
				while (true) {
					tmp.add(type);
					addMethods(ctx, type);

					parent = type.parent();
					if (parent == null) break;
					type = ctx.getClassInfo(parent);
				}
			}

			// 尽量别走invokeinterface(毕竟多两个字节呢)
			for (int i = 0; i < tmp.size(); i++) {
				List<String> list = ((IClass) tmp.get(i)).interfaces();
				for (int j = 0; j < list.size(); j++)
					addMethods(ctx, ctx.getClassInfo(list.get(j)));
			}

			tmp.clear();
			for (Iterator<ComponentList> itr = methods.values().iterator(); itr.hasNext(); ) {
				if (itr.next() instanceof MethodList ml && ml.owner == null && ml.pack(owner)) {
					tmp.add(new MethodListSingle(ml.methods.get(0)));
					itr.remove();
				}
			}

			for (int i = 0; i < tmp.size(); i++) {
				MethodListSingle list = (MethodListSingle) tmp.get(i);
				methods.put(list.node.name(), list);
			}
		}

		return methods.get(name);
	}

	/*
	 * <pre> 调用的实际方法由以下查找过程选择：
	 * C = 符号引用的类名称
	 *
	 * 1. C自己
	 * 2. C的直接超类,递归 (接口的default因为接口自身没有ACC_SUPER，不会走这一步)
	 * 3. C是接口，尝试 Object的方法
	 * 4. C的最大特定超接口方法中，没有设置 ACC_ABSTRACT 标志的
	 *
	 * 异常:
	 * a. 如果步骤 1、2 或 3 选择了抽象方法，抛出 AbstractMethodError
	 * b. 如果步骤 4 找到了多于一个方法，抛出 IncompatibleClassChangeError
	 * c. 如果没有找到方法，抛出 AbstractMethodError
	 *
	 * (invokespecial)如果以下条件全部为真，则 C 是当前类的直接超类：
	 * 1. 解析的方法不是实例初始化方法（§2.9.1）。
	 * 2. 符号引用的是类（#CstRefMethod），并且该类是当前类的超类。
	 * 3. 当前类拥有 ACC_SUPER 标志（§4.1）。
	 *
	 * 如果以下条件全部为真，则 M 是类或接口 C 的最大特定超接口方法：
	 * 1. M 在 C 的超接口（直接或间接）中声明
	 * 2. M 未设置 ACC_PRIVATE 或 ACC_STATIC 标志
	 * 3. 如果方法是在接口 I 中声明的，则接口 I 的子接口中没有 C 的最大特异性超接口方法
	 */
	private void addMethods(ClassContext ctx, IClass type) {
		List<? extends RawNode> list = type.methods();
		for (int i = 0; i < list.size(); i++) {
			MethodNode mn = (MethodNode) list.get(i);
			if (mn.name().charAt(0) == '<' || (mn.modifier & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) continue;

			ComponentList cl = methods.get(mn.name());
			if (cl instanceof MethodList ml) {
				if (ml.owner == owner) {
					ml.add(type, mn);
				}
			} else {
				methods.put(mn.name(), ctx.methodList(type, mn.name()));
			}
		}
	}

	//endregion
	//region 字段
	private MyHashMap<String, ComponentList> fields;

	public synchronized ComponentList findField(ClassContext ctx, String name) {
		block:
		if (fields == null) {
			List<? extends RawNode> fields1 = owner.fields();
			if (fields1.isEmpty()) {
				fields = ctx.getResolveHelper(ctx.getClassInfo(owner.parent())).fields;
				break block;
			}

			fields = new MyHashMap<>();

			for (int i = 0; i < fields1.size(); i++) {
				FieldNode fn = (FieldNode) fields1.get(i);

				FieldList fl = (FieldList) fields.get(fn.name());
				if (fl == null) fields.put(fn.name(), fl = new FieldList());
				fl.add(owner, fn);
			}

			IClass type = owner;
			String parent = type.parent();
			if (parent != null) {
				type = ctx.getClassInfo(parent);
				while (true) {
					addFields(ctx, type);

					parent = type.parent();
					if (parent == null) break;
					type = ctx.getClassInfo(parent);
				}
			}

			List<FieldListSingle> tmp = new SimpleList<>();
			for (Iterator<ComponentList> itr = fields.values().iterator(); itr.hasNext(); ) {
				if (itr.next() instanceof FieldList fl && fl.owners.get(0) == owner && fl.pack(owner)) {
					tmp.add(new FieldListSingle(owner, fl.fields.get(0)));
					itr.remove();
				}
			}

			for (int i = 0; i < tmp.size(); i++)
				fields.put(tmp.get(i).node.name(), tmp.get(i));
		}

		return fields.get(name);
	}

	private void addFields(ClassContext ctx, IClass type) {
		List<? extends RawNode> list = type.fields();
		for (int i = 0; i < list.size(); i++) {
			FieldNode fn = (FieldNode) list.get(i);
			if ((fn.modifier & (Opcodes.ACC_SYNTHETIC)) != 0) continue;

			ComponentList cl = fields.get(fn.name());
			if (cl instanceof FieldList fl) {
				if (fl.owners.get(0) == owner) {
					fl.add(type, fn);
				}
			} else {
				fields.put(fn.name(), ctx.fieldList(type, fn.name()));
			}
		}
	}
	//endregion
}