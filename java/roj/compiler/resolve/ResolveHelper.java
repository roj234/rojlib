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
import roj.asm.tree.attr.InnerClasses;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.asm.util.Attributes;
import roj.asmx.AnnotationSelf;
import roj.collect.IntBiMap;
import roj.collect.MyHashMap;
import roj.collect.MyHashSet;
import roj.collect.SimpleList;
import roj.compiler.asm.LazyAnnVal;
import roj.compiler.context.GlobalContext;
import roj.compiler.diagnostic.Kind;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static roj.asmx.AnnotationSelf.*;

/**
 * @author Roj234
 * @since 2024/2/6 0006 2:40
 */
public final class ResolveHelper {
	private Object _next;
	public final IClass owner;

	public ResolveHelper(IClass owner) {this.owner = Objects.requireNonNull(owner);}

	private MethodNode lambdaMethod;
	private byte lambdaType;
	public int getLambdaType() {
		if (lambdaType == 0) {
			if ((owner.modifier()&Opcodes.ACC_ABSTRACT) == 0) {
				lambdaType = 3;
			} else {
				RawNode mn = null;
				for (RawNode method : owner.methods()) {
					if ((method.modifier()&Opcodes.ACC_ABSTRACT) != 0) {
						if (mn != null) return lambdaType = 5;
						mn = method;
					}
				}

				if (mn == null) {
					lambdaType = 4;
				} else {
					lambdaMethod = (MethodNode) mn;
					lambdaType = (byte) ((owner.modifier()&Opcodes.ACC_INTERFACE) != 0 ? 1 : 2);
				}

			}
		}
		return lambdaType;
	}
	public MethodNode getLambdaMethod() {return lambdaMethod;}

	private byte foreachType;
	public boolean isFastForeach(GlobalContext ctx) {
		if (foreachType != 0) return foreachType > 0;

		IntBiMap<String> classes;
		try {
			classes = getClassList(ctx);
		} catch (ClassNotFoundException e) {
			ctx.report(owner, Kind.WARNING, -1, "symbol.error.noSuchClass", e.getMessage());
			foreachType = -1;
			return false;
		}

		if (classes.containsValue("java/util/List") && classes.containsValue("java/util/RandomAccess")) {
			foreachType = 1;
			return true;
		}

		var attr = owner.parsedAttr(owner.cp(), Attribute.ClAnnotations);
		if (attr != null && Attributes.getAnnotation(attr.annotations, "roj/compiler/api/ListIterable") != null) {
			if (owner.getMethod("get") >= 0 && owner.getMethod("size", "()I") >= 0) {
				foreachType = 1;
				return true;
			}
		}

		foreachType = -1;
		return false;
	}

	// region 父类列表
	private IntBiMap<String> classList;
	private boolean query;

	public synchronized IntBiMap<String> getClassList(GlobalContext ctx) throws ClassNotFoundException {
		if (classList != null) return classList;

		if (query) throw new ResolveException("rh.cyclicDepend:"+owner.name());
		query = true;

		IntBiMap<String> list = new IntBiMap<>();

		IClass info = owner;
		while (true) {
			String owner = info.name();
			try {
				int i = list.size();
				list.putInt((i << 16) | i, owner);
			} catch (IllegalArgumentException e) {
				throw new ResolveException("rh.cyclicDepend:"+owner);
			}

			owner = info.parent();
			if (owner == null) break;
			info = ctx.getClassInfo(owner);
			if (info == null) throw new ClassNotFoundException(owner);
		}

		info = owner;
		int castDistance = 1;
		while (true) {
			List<String> itf = info.interfaces();
			for (int i = 0; i < itf.size(); i++) {
				String name = itf.get(i);

				IClass itfInfo = ctx.getClassInfo(name);
				if (itfInfo == null) throw new ClassNotFoundException(name);

				list.forcePut((castDistance == 1 ? 0x80000000 : 0) | (list.size() << 16) | castDistance, name);

				for (var entry : ctx.getParentList(itfInfo).selfEntrySet()) {
					int id = entry.getIntKey();
					name = entry.getValue();

					// id's castDistance is smaller
					// parentList是包含自身的
					if ((list.getInt(name)&0xFFFF) > (id&0xFFFF)) {
						list.forcePut((list.size() << 16) | (castDistance + (id & 0xFFFF)), name);
					}
				}
			}

			castDistance++;
			String owner = info.parent();
			if (owner == null) break;
			info = ctx.getClassInfo(owner);
			if (info == null) throw new ClassNotFoundException(owner);
		}

		query = false;

		return this.classList = list;
	}

	// endregion
	// region 注解
	private AnnotationSelf ac;

	public AnnotationSelf annotationInfo() {
		if (ac != null || (owner.modifier() & Opcodes.ACC_ANNOTATION) == 0) return ac;

		synchronized (this) {
			if (ac != null) return ac;
			AnnotationSelf ac = this.ac = new AnnotationSelf();

			List<? extends RawNode> methods = owner.methods();
			for (int j = 0; j < methods.size(); j++) {
				MethodNode m = (MethodNode) methods.get(j);
				if ((m.modifier() & Opcodes.ACC_STATIC) != 0) continue;
				var dv = m.parsedAttr(owner.cp(), Attribute.AnnotationDefault);
				if (dv != null) ac.values.put(m.name(), dv.val == null ? new LazyAnnVal(dv) : dv.val);
				ac.types.put(m.name(), m.returnType());
			}

			Annotations attr = owner.parsedAttr(owner.cp(), Attribute.RtAnnotations);
			if (attr == null) return ac;

			List<Annotation> list = attr.annotations;
			for (int i = 0; i < list.size(); i++) {
				Annotation a = list.get(i);
				switch (a.type()) {
					case "java/lang/annotation/Retention" -> {
						if (!a.containsKey("value")) throw new NullPointerException("Invalid @Retention");
						ac.kind = switch (a.getEnumValue("value", "RUNTIME")) {
							case "SOURCE" -> SOURCE;
							case "CLASS" -> CLASS;
							case "RUNTIME" -> RUNTIME;
							default -> throw new IllegalStateException("Unexpected Retention: "+a.getEnumValue("value", "RUNTIME"));
						};
					}
					case "java/lang/annotation/Repeatable" -> {
						if (!a.containsKey("value")) throw new NullPointerException("Invalid @Repeatable");
						ac.repeatOn = a.getClass("value").owner;
					}
					case "java/lang/annotation/Target" -> {
						int tmp = 0;
						if (!a.containsKey("value")) throw new NullPointerException("Invalid @Target");
						List<AnnVal> array = a.getArray("value");
						for (int j = 0; j < array.size(); j++) {
							tmp |= switch (array.get(j).asEnum().field) {
								case "TYPE" -> TYPE;
								case "FIELD" -> FIELD;
								case "METHOD" -> METHOD;
								case "PARAMETER" -> PARAMETER;
								case "CONSTRUCTOR" -> CONSTRUCTOR;
								case "LOCAL_VARIABLE" -> LOCAL_VARIABLE;
								case "ANNOTATION_TYPE" -> ANNOTATION_TYPE;
								case "PACKAGE" -> PACKAGE;
								case "TYPE_PARAMETER" -> TYPE_PARAMETER;
								case "TYPE_USE" -> TYPE_USE;
								case "MODULE" -> MODULE;
								case "RECORD_COMPONENT" -> RECORD_COMPONENT;
								default -> 0;
							};
						}
						ac.applicableTo = tmp;
					}
					case "roj/compiler/api/Stackable" -> ac.stackable = true;
				}
			}
			return ac;
		}
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

				IClass type = owner;
				List<? extends RawNode> methods1 = type.methods();
				MyHashSet<String> remove = new MyHashSet<>();
				for (int i = 0; i < methods1.size(); i++) {
					MethodNode mn = (MethodNode) methods1.get(i);
					// <init> 允许 synthetic
					if (mn.name().startsWith("<") ? mn.name().equals("<clinit>") : (mn.modifier & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0) {
						remove.add(mn.rawDesc());
						continue;
					}

					MethodList ml = (MethodList) methods.get(mn.name());
					if (ml == null) methods.put(mn.name(), ml = new MethodList());
					ml.add(type, mn);
				}

				// Object不会实现接口
				String className = type.parent();
				if (className != null) {
					int i = 0;

					// 尽量别走invokeinterface(毕竟多两个字节呢)
					List<String> itf = type.interfaces();

					while (true) {
						IClass info = ctx.getClassInfo(className);
						if (info == null) {
							ctx.report(type, Kind.WARNING, -1, "symbol.error.noSuchClass", className);
						} else for (Map.Entry<String, ComponentList> entry : ctx.getResolveHelper(info).getMethods(ctx).entrySet()) {
							String key = entry.getKey();
							if (key.startsWith("<")) continue;

							ComponentList list = entry.getValue();

							ComponentList cl = methods.putIfAbsent(key, list);
							if (cl instanceof MethodList prev && prev.owner == null) {
								if (list instanceof MethodList ml) {
									for (MethodNode node : ml.methods) {
										if (!remove.contains(node.rawDesc()))
											prev.add(ctx.getClassInfo(node.owner), node);
									}
								} else {
									MethodNode node = ((MethodListSingle) list).node;
									if (!remove.contains(node.rawDesc()))
										prev.add(ctx.getClassInfo(node.owner), node);
								}
							}
						}

						if (i == itf.size()) break;
						className = itf.get(i++);
					}
				}

				for (Map.Entry<String, ComponentList> entry : methods.entrySet()) {
					if (entry.getValue() instanceof MethodList ml && ml.owner == null && ml.pack(type)) {
						entry.setValue(new MethodListSingle(ml.methods.get(0)));
					}
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

				IClass type = owner;
				List<? extends RawNode> fields1 = type.fields();
				for (int i = 0; i < fields1.size(); i++) {
					FieldNode fn = (FieldNode) fields1.get(i);

					FieldList fl = (FieldList) fields.get(fn.name());
					if (fl == null) fields.put(fn.name(), fl = new FieldList());
					fl.add(type, fn);
				}

				String className = type.parent();
				if (className != null) {
					int i = 0;
					List<String> itf = type.interfaces();

					while (true) {
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
						if (i == itf.size()) break;
						className = itf.get(i++);
					}
				}

				for (Map.Entry<String, ComponentList> entry : fields.entrySet()) {
					if (entry.getValue() instanceof FieldList fl && fl.owners.get(0) == type && fl.pack(type)) {
						entry.setValue(new FieldListSingle(type, fl.fields.get(0)));
					}
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
	public Map<String, List<IType>> getTypeParamOwner(GlobalContext ctx) throws ClassNotFoundException {
		if (typeParamOwner == null) {
			synchronized (this) {
				if (typeParamOwner != null) return typeParamOwner;

				Signature sign = owner.parsedAttr(owner.cp(), Attribute.SIGNATURE);
				if (sign == null) return typeParamOwner = Collections.emptyMap();

				typeParamOwner = new MyHashMap<>();
				Map<String, List<IType>> tmp = new MyHashMap<>();
				for (IType value : sign.values) {
					if (value.genericType() == IType.PLACEHOLDER_TYPE) continue;

					IClass ref = ctx.getClassInfo(value.owner());
					if (ref == null) throw new ClassNotFoundException(value.toString());

					if (value.genericType() == IType.GENERIC_TYPE) {
						// 大概是不允许用extendType的
						List<IType> children = ((Generic) value).children;
						if (Inferrer.hasTypeParam(value)) {
							List<IType> c1 = SimpleList.hugeCapacity(children.size());
							// getClass() marker
							c1.addAll(children);
							children = c1;
						}

						List<IType> prev = tmp.putIfAbsent(value.owner(), children);
						if (prev != null) throw new IllegalArgumentException(owner.name()+" 的泛型签名有误");
					}

					typeParamOwner.putAll(ctx.getResolveHelper(ref).getTypeParamOwner(ctx));
				}

				typeParamOwner.putAll(tmp);
			}
		}

		return typeParamOwner;
	}
	// endregion
	private MyHashMap<String, InnerClasses.Item> subclassDecl;

	public MyHashMap<String, InnerClasses.Item> getInnerClasses() {
		if (subclassDecl == null) {
			synchronized (this) {
				if (subclassDecl != null) return subclassDecl;
				subclassDecl = new MyHashMap<>();
				var classes = owner.parsedAttr(owner.cp(), Attribute.InnerClasses);
				if (classes == null) return subclassDecl;

				var list = classes.classes;
				for (int i = 0; i < list.size(); i++) {
					var ref = list.get(i);
					if (ref.name != null && owner.name().equals(ref.parent))
						subclassDecl.put(ref.name, ref);
				}
			}
		}

		return subclassDecl;
	}
}