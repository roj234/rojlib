package roj.compiler.resolve;

import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.RawNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AnnotationDefault;
import roj.asm.tree.attr.Annotations;
import roj.asm.tree.attr.Attribute;
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asmx.AnnotationSelf;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.compiler.context.GlobalContext;
import roj.compiler.diagnostic.Kind;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

	public synchronized IntBiMap<String> getClassList(GlobalContext ctx) throws ClassNotFoundException {
		if (classList != null) return classList;

		IntBiMap<String> list = new IntBiMap<>();

		String owner = this.owner.name();
		list.putInt(0, owner);

		while (true) {
			IClass info = ctx.getClassInfo(owner);
			if (info == null) throw new ClassNotFoundException(owner);
			owner = info.parent();
			if (owner == null) break;
			DIST(list, list.size()+1, owner);
		}

		owner = this.owner.name();
		int castDistance = 1;
		do {
			IClass info = ctx.getClassInfo(owner);

			List<String> itf = info.interfaces();
			for (int i = 0; i < itf.size(); i++) {
				String name = itf.get(i);

				IClass itfInfo = ctx.getClassInfo(name);
				if (itfInfo == null) throw new ClassNotFoundException(owner);

				if (list.getInt(name) < 0) DIST(list, castDistance, name);

				IntBiMap<String> superInterfaces = ctx.parentList(itfInfo);
				for (IntBiMap.Entry<String> entry : superInterfaces.selfEntrySet()) {
					name = entry.getValue();
					if (list.getInt(name) < 0) DIST(list, castDistance + (entry.getIntKey()>>>16), name);
				}
			}

			castDistance++;
			owner = info.parent();
		} while (owner != null);

		return this.classList = list;
	}

	private static void DIST(IntBiMap<String> list, int castDistance, String name) {
		list.putInt((list.size()<<16) | castDistance, name);
	}

	// region 注解
	private AnnotationSelf ac;

	public synchronized AnnotationSelf annotationInfo() {
		if (ac != null || (owner.modifier() & Opcodes.ACC_ANNOTATION) == 0) return ac;

		AnnotationSelf ac = this.ac = new AnnotationSelf();

		List<? extends RawNode> methods = owner.methods();
		for (int j = 0; j < methods.size(); j++) {
			MethodNode m = (MethodNode) methods.get(j);
			if ((m.modifier() & Opcodes.ACC_STATIC) != 0) continue;
			AnnotationDefault dv = m.parsedAttr(owner.cp(), Attribute.AnnotationDefault);
			ac.values.put(m.name(), dv == null ? null : dv.val);
		}

		Annotations attr = owner.parsedAttr(owner.cp(), Attribute.RtAnnotations);
		if (attr == null) return ac;

		List<Annotation> list = attr.annotations;
		for (int i = 0; i < list.size(); i++) {
			Annotation a = list.get(i);
			switch (a.type()) {
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

	public ComponentList findMethod(GlobalContext ctx, String name) {return getMethods(ctx).get(name);}
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
	public MyHashMap<String, ComponentList> getMethods(GlobalContext ctx) {
		if (methods == null) {
			synchronized (this) {
				if (methods != null) return methods;
				methods = new MyHashMap<>();
			}

			IClass type = owner;
			List<? extends RawNode> methods1 = type.methods();
			for (int i = 0; i < methods1.size(); i++) {
				MethodNode mn = (MethodNode) methods1.get(i);
				if (mn.name().equals("<clinit>") || (mn.modifier & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) continue;

				MethodList ml = (MethodList) methods.get(mn.name());
				if (ml == null) methods.put(mn.name(), ml = new MethodList());
				ml.add(type, mn);
			}

			List<String> tmp = new SimpleList<>();
			String parent = type.parent();
			if (parent != null) tmp.add(parent);
			// 尽量别走invokeinterface(毕竟多两个字节呢)
			tmp.addAll(type.interfaces());

			for (int i = 0; i < tmp.size(); i++) {
				String className = tmp.get(i);

				IClass info = ctx.getClassInfo(className);
				if (info == null) {
					ctx.report(type, Kind.WARNING, -1, "symbol.error.noSuchClass", className);
					continue;
				}

				ResolveHelper rh = ctx.getResolveHelper(info);
				rh.findMethod(ctx, "");

				for (Map.Entry<String, ComponentList> entry : rh.methods.entrySet()) {
					String key = entry.getKey();
					if (key.startsWith("<")) continue;

					ComponentList list = entry.getValue();

					ComponentList cl = methods.putIfAbsent(key, list);
					if (cl instanceof MethodList prev && prev.owner == null) {
						if (list instanceof MethodList ml) {
							for (MethodNode node : ml.methods) {
								prev.add(ctx.getClassInfo(node.owner), node);
							}
						} else {
							MethodNode node = ((MethodListSingle) list).node;
							prev.add(ctx.getClassInfo(node.owner), node);
						}
					}
				}
			}

			for (Map.Entry<String, ComponentList> entry : methods.entrySet()) {
				if (entry.getValue() instanceof MethodList ml && ml.owner == null && ml.pack(type)) {
					entry.setValue(new MethodListSingle(ml.methods.get(0)));
				}
			}
		}

		return methods;
	}
	//endregion
	//region 字段
	private MyHashMap<String, ComponentList> fields;

	public ComponentList findField(GlobalContext ctx, String name) {return getFields(ctx).get(name);}
	public MyHashMap<String, ComponentList> getFields(GlobalContext ctx) {
		if (fields == null) {
			synchronized (this) {
				if (fields != null) return fields;
				fields = new MyHashMap<>();
			}

			IClass type = owner;
			List<? extends RawNode> fields1 = type.fields();
			for (int i = 0; i < fields1.size(); i++) {
				FieldNode fn = (FieldNode) fields1.get(i);

				FieldList fl = (FieldList) fields.get(fn.name());
				if (fl == null) fields.put(fn.name(), fl = new FieldList());
				fl.add(type, fn);
			}

			List<String> tmp = new SimpleList<>();
			String parent = type.parent();
			if (parent != null) tmp.add(parent);
			tmp.addAll(type.interfaces());

			for (int i = 0; i < tmp.size(); i++) {
				String className = tmp.get(i);

				IClass info = ctx.getClassInfo(className);
				if (info == null) {
					ctx.report(type, Kind.WARNING, -1, "symbol.error.noSuchClass", className);
					continue;
				}

				ResolveHelper rh = ctx.getResolveHelper(info);
				rh.findField(ctx, "");

				for (Map.Entry<String, ComponentList> entry : rh.fields.entrySet()) {
					String key = entry.getKey();
					ComponentList list = entry.getValue();

					ComponentList cl = fields.putIfAbsent(key, list);
					if (cl instanceof FieldList prev && prev.owners.get(0) == type) {
						if (list instanceof FieldList fl) {
							SimpleList<FieldNode> nodes = fl.fields;
							for (int j = 0; j < nodes.size(); j++) {
								prev.add(fl.owners.get(j), nodes.get(j));
							}
						} else {
							FieldListSingle fl = (FieldListSingle) list;
							prev.add(fl.owner, fl.node);
						}
					}
				}
			}

			for (Map.Entry<String, ComponentList> entry : fields.entrySet()) {
				if (entry.getValue() instanceof FieldList fl && fl.owners.get(0) == type && fl.pack(type)) {
					entry.setValue(new FieldListSingle(type, fl.fields.get(0)));
				}
			}
		}

		return fields;
	}
	//endregion
	// region 类型拥有者
	private Map<String, List<IType>> typeParamOwner;

	// overriding type parameter bounds
	// a. class Ch<T> extends Su<T>
	// b. class Ch<T> extends Su<String>
	// c. class Ch<T> extends Su<List<T>>
	public synchronized Map<String, List<IType>> getTypeParamOwner(GlobalContext ctx) throws ClassNotFoundException {
		if (typeParamOwner == null) {
			Signature sign = owner.parsedAttr(owner.cp(), Attribute.SIGNATURE);
			if (sign == null) return typeParamOwner = Collections.emptyMap();

			typeParamOwner = new MyHashMap<>();
			for (IType value : sign.values) {
				if (value.genericType() == IType.PLACEHOLDER_TYPE) continue;

				IClass ref = ctx.getClassInfo(value.owner());
				if (ref == null) throw new ClassNotFoundException(value.toString());

				if (value.genericType() == IType.GENERIC_TYPE) {
					// 大概是不允许用extendType的
					List<IType> children = ((Generic) value).children;
					if (Inferrer.hasTypeParam(value)) children = new SimpleList<>(children) {}; // class marker

					List<IType> prev = typeParamOwner.putIfAbsent(value.owner(), children);
					if (prev != null) throw new IllegalArgumentException(owner.name()+" 的泛型签名有误");
				}

				Map<String, List<IType>> parentMap = ctx.getResolveHelper(ref).getTypeParamOwner(ctx);

				int size = parentMap.size()+typeParamOwner.size();
				typeParamOwner.putAll(parentMap);
				if (typeParamOwner.size() != size) throw new IllegalArgumentException(owner.name()+" 的泛型签名有误");
			}
		}

		return typeParamOwner;
	}
	// endregion
	private MyHashMap<String, InnerClasses.InnerClass> subclassByName;

	public synchronized MyHashMap<String, InnerClasses.InnerClass> getInnerClassFlags(GlobalContext ctx) {
		if (subclassByName == null) {
			subclassByName = new MyHashMap<>();
			InnerClasses classes = owner.parsedAttr(owner.cp(), Attribute.InnerClasses);
			if (classes == null) return subclassByName;

			List<InnerClasses.InnerClass> list = classes.classes;
			for (int i = 0; i < list.size(); i++) {
				InnerClasses.InnerClass ref = list.get(i);
				if (ref.name != null) {
					subclassByName.put(ref.name, ref);
				}
			}
		}

		return subclassByName;
	}
}