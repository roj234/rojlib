package roj.compiler.resolve;

import org.jetbrains.annotations.Range;
import roj.asm.*;
import roj.asm.annotation.Annotation;
import roj.asm.attr.Attribute;
import roj.asm.attr.InnerClasses;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Signature;
import roj.collect.ArrayList;
import roj.collect.HashMap;
import roj.collect.HashSet;
import roj.collect.ToIntMap;
import roj.compiler.diagnostic.Kind;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2024/2/6 2:40
 */
public final class LinkedClass {
	private Object _next;
	public final ClassDefinition owner;

	public LinkedClass(ClassDefinition owner) {this.owner = Objects.requireNonNull(owner);}

	private MethodNode lambdaMethod;
	private byte lambdaType; // 只有1，2有效，3，4，5均是提供错误诊断
	public int getLambdaType() {
		if (lambdaType == 0) {
			if ((owner.modifier()&Opcodes.ACC_ABSTRACT) == 0) {
				lambdaType = 3;
			} else {
				Member mn = null;
				for (Member method : owner.methods()) {
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
	public MethodNode getLambdaMethod() {getLambdaType();return lambdaMethod;}

	private byte iterateType;
	// -3=unknown, -2=Iterator, -1=Iterable, 1=RandomAccessList, 2=LavaRandomAccessible
	@Range(from = -3, to = 2)
	public int getIterableMode(Resolver ctx) {
		if (iterateType != 0) return iterateType;

		var classes = getHierarchyList(ctx);
		if (classes.containsKey("java/util/List") && classes.containsKey("java/util/RandomAccess")) {
			return iterateType = 1;
		} else if (Annotation.findInvisible(owner.cp(), owner, "roj/compiler/api/RandomAccessible") != null) {
			int tmp;
			if ((tmp = owner.getMethod("get")) >= 0 && owner.methods().get(tmp).rawDesc().startsWith("(I)") && owner.getMethod("size", "()I") >= 0) {
				return iterateType = 2;
			}
		} else if (classes.containsKey("java/util/Iterable")) {
			return iterateType = -1;
		} else if (classes.containsKey("java/util/Iterator")) {
			return iterateType = -2;
		}

		return iterateType = -3;
	}

	// region 父类列表
	private ToIntMap<String> hierarchyList;
	private boolean query;

	public synchronized ToIntMap<String> getHierarchyList(Resolver ctx) {
		if (hierarchyList != null) return hierarchyList;

		if (query) throw ResolveException.ofIllegalInput("rh.cyclicDepend", owner.name());
		query = true;

		ToIntMap<String> list = new ToIntMap<>();

		ClassDefinition info = owner;
		while (true) {
			String owner = info.name();
			try {
				int i = list.size();
				list.putInt(owner, (i << 16) | i);
			} catch (IllegalArgumentException e) {
				throw ResolveException.ofIllegalInput("rh.cyclicDepend", owner);
			}

			owner = info.parent();
			if (owner == null) break;
			info = ctx.resolve(owner);
			if (info == null) {
				ctx.report(this.owner, Kind.SEVERE_WARNING, -1, "symbol.error.noSuchClass", owner);
				break;
			}
		}

		info = owner;
		int castDistance = 1;
		int justAnId = list.size();
		while (true) {
			List<String> itf = info.interfaces();
			for (int i = 0; i < itf.size(); i++) {
				String name = itf.get(i);

				ClassDefinition itfInfo = ctx.resolve(name);
				if (itfInfo == null) {
					ctx.report(this.owner, Kind.SEVERE_WARNING, -1, "symbol.error.noSuchClass", name);
					break;
				}

				list.putInt(name, (justAnId++ << 16) | castDistance);

				for (var entry : ctx.getHierarchyList(itfInfo).selfEntrySet()) {
					name = entry.getKey();
					int id = entry.value;

					// id's castDistance is smaller
					// parentList是包含自身的
					if ((list.getInt(name)&0xFFFF) > (id&0xFFFF)) {
						list.putInt(name, (castDistance == 1 ? 0x80000000 : 0) | (justAnId++ << 16) | (castDistance + (id & 0xFFFF)));
					}
				}
			}

			castDistance++;
			String owner = info.parent();
			if (owner == null) break;
			info = ctx.resolve(owner);
			if (info == null) break;
		}

		query = false;

		return this.hierarchyList = list;
	}

	// endregion
	// region 注解
	private AnnotationType ac;

	public AnnotationType annotationInfo() {
		if (ac != null || (owner.modifier() & Opcodes.ACC_ANNOTATION) == 0) return ac;

		synchronized (this) {
			if (ac != null) return ac;
			return ac = new AnnotationType(owner);
		}
	}

	// endregion
	// region 方法
	private HashMap<String, ComponentList> methods;

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
	public HashMap<String, ComponentList> getMethods(Resolver ctx) {
		if (methods == null) {
			synchronized (this) {
				if (methods != null) return methods;
				methods = new HashMap<>();

				ClassDefinition type = owner;
				List<? extends Member> methods1 = type.methods();
				HashSet<MemberDescriptor> bridgeIgnore = new HashSet<>();
				for (int i = 0; i < methods1.size(); i++) {
					var mn = (MethodNode) methods1.get(i);

					// 下面会continue，<init>又不可能带bridge
					if ((mn.modifier & Opcodes.ACC_BRIDGE) != 0) bridgeIgnore.add(new MemberDescriptor("",mn.name(),mn.rawDesc()));

					// <init> 允许 synthetic
					if (mn.name().startsWith("<") ? mn.name().equals("<clinit>") : (mn.modifier & (Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC)) != 0)
						continue;

					var ml = (MethodList) methods.get(mn.name());
					if (ml == null) methods.put(mn.name(), ml = new MethodList());
					ml.add(type, mn);
				}

				var tmpDesc = new MemberDescriptor();
				tmpDesc.owner = "";

				// Object不会实现接口
				String className = type.parent();
				if (className != null) {
					int i = 0;

					// 尽量别走invokeinterface(毕竟多两个字节呢)
					List<String> itf = type.interfaces();

					while (true) {
						ClassDefinition info = ctx.resolve(className);
						if (info == null) {
							ctx.report(type, Kind.SEVERE_WARNING, -1, "symbol.error.noSuchClass", className);
						} else for (Map.Entry<String, ComponentList> entry : ctx.link(info).getMethods(ctx).entrySet()) {
							String key = entry.getKey();
							if (key.startsWith("<")) continue;
							tmpDesc.name = key;

							ComponentList list = entry.getValue();

							ComponentList cl = methods.putIfAbsent(key, list);
							if (cl instanceof MethodList prev && prev.owner == null) {
								if (list instanceof MethodList ml) {
									for (MethodNode node : ml.methods) {
										tmpDesc.rawDesc = node.rawDesc();
										if (!bridgeIgnore.contains(tmpDesc))
											prev.add(ctx.resolve(node.owner()), node);
									}
								} else {
									MethodNode node = ((MethodListSingle) list).method;
									tmpDesc.rawDesc = node.rawDesc();
									if (!bridgeIgnore.contains(tmpDesc))
										prev.add(ctx.resolve(node.owner()), node);
								}
							}
						}

						if (i == itf.size()) break;
						className = itf.get(i++);
					}
				}

				for (Map.Entry<String, ComponentList> entry : methods.entrySet()) {
					if (entry.getValue() instanceof MethodList ml && ml.owner == null && ml.pack(type)) {
						entry.setValue(new MethodListSingle(ml.methods.get(0), ml.isOverriddenMethod(0)));
					}
				}
			}
		}

		return methods;
	}
	//endregion
	//region 字段
	private HashMap<String, ComponentList> fields;

	public HashMap<String, ComponentList> getFields(Resolver ctx) {
		if (fields == null) {
			synchronized (this) {
				if (fields != null) return fields;
				fields = new HashMap<>();

				ClassDefinition type = owner;
				List<? extends Member> fields1 = type.fields();
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
						ClassDefinition info = ctx.resolve(className);
						if (info == null) {
							ctx.report(type, Kind.SEVERE_WARNING, -1, "symbol.error.noSuchClass", className);
						} else {

						for (var entry : ctx.link(info).getFields(ctx).entrySet()) {
							String key = entry.getKey();
							ComponentList list = entry.getValue();

							ComponentList cl = fields.putIfAbsent(key, list);
							if (cl instanceof FieldList prev && prev.owners.get(0) == type) {
								if (list instanceof FieldList fl) {
									ArrayList<FieldNode> nodes = fl.fields;
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
	public Map<String, List<IType>> getTypeParamOwner(Resolver ctx) {
		if (typeParamOwner == null) {
			synchronized (this) {
				if (typeParamOwner != null) return typeParamOwner;

				Signature sign = owner.getAttribute(owner.cp(), Attribute.SIGNATURE);
				if (sign == null) return typeParamOwner = Collections.emptyMap();

				typeParamOwner = new HashMap<>();
				Map<String, List<IType>> tmp = new HashMap<>();
				for (IType value : sign.values) {
					if (value.genericType() == IType.PLACEHOLDER_TYPE) continue;

					ClassDefinition ref = ctx.resolve(value.owner());
					if (ref == null) {
						ctx.report(this.owner, Kind.SEVERE_WARNING, -1, "symbol.error.noSuchClass", owner);
						continue;
					}

					if (value.genericType() == IType.GENERIC_TYPE) {
						// 大概是不允许用extendType的
						List<IType> children = ((Generic) value).children;
						if (Inferrer.hasTypeParam(value)) {
							List<IType> c1 = ArrayList.hugeCapacity(children.size());
							// getClass() marker
							c1.addAll(children);
							children = c1;
						}

						List<IType> prev = tmp.putIfAbsent(value.owner(), children);
						if (prev != null) throw new IllegalArgumentException(owner.name()+" 的泛型签名有误");
					}

					typeParamOwner.putAll(ctx.link(ref).getTypeParamOwner(ctx));
				}

				typeParamOwner.putAll(tmp);
			}
		}

		return typeParamOwner;
	}
	// endregion
	private volatile Map<String, InnerClasses.Item> subclassDecl;

	/**
	 * 自己或继承的内部类，只包含真正的内部类，不包含对外部的内部类的引用，感觉也用不上，给反编译用的，大家好像都在用$判断
	 */
	public Map<String, InnerClasses.Item> getInnerClasses(Resolver ctx) {
		if (subclassDecl == null) {
			synchronized (this) {
				if (subclassDecl != null) return subclassDecl;
				var classes = owner.getAttribute(owner.cp(), Attribute.InnerClasses);
				if (classes == null) return subclassDecl = Collections.emptyMap();

				var parentDecl = ctx.getInnerClassInfo(ctx.resolve(owner.parent()));
				var decl = new HashMap<>(parentDecl);

				var list = classes.classes;
				for (int i = 0; i < list.size(); i++) {
					var ref = list.get(i);
					if (ref.name != null && (owner.name().equals(ref.parent) || owner.name().equals(ref.self))) {
						decl.put("!"+ref.name, ref);
						decl.put(ref.self, ref);
					}
				}
				subclassDecl = decl;
			}
		}

		return subclassDecl;
	}
}