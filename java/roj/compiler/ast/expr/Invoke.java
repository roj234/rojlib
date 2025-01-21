package roj.compiler.ast.expr;

import roj.WillChange;
import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.asm.visitor.Label;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.compiler.LavaFeatures;
import roj.compiler.api.Evaluable;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.EnumUtil;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.Inferrer;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.ResolveException;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import java.util.*;

import static roj.asm.Opcodes.*;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Invoke extends ExprNode {
	Object fn; // new anonymous class may change this later
	List<ExprNode> args;
	private List<IType> bounds;

	private MethodNode methodNode;
	private byte flag;
	private List<IType> desc;
	private IType genType1;

	public static Invoke staticMethod(MethodNode node) {return staticMethod(node, Collections.emptyList());}
	public static Invoke staticMethod(MethodNode node, @WillChange ExprNode... args) {return staticMethod(node, Arrays.asList(args));}
	public static Invoke staticMethod(MethodNode node, @WillChange List<ExprNode> args) {return invoke(node, null, args, 0);}

	public static Invoke virtualMethod(MethodNode node, ExprNode loader) {return virtualMethod(node, loader, Collections.emptyList());}
	public static Invoke virtualMethod(MethodNode node, ExprNode loader, @WillChange ExprNode... args) {return virtualMethod(node, loader, Arrays.asList(args));}
	public static Invoke virtualMethod(MethodNode node, ExprNode loader, @WillChange List<ExprNode> args) {return invoke(node, loader, args, 0);}

	public static Invoke interfaceMethod(MethodNode node, ExprNode loader) {return interfaceMethod(node, loader, Collections.emptyList());}
	public static Invoke interfaceMethod(MethodNode node, ExprNode loader, @WillChange ExprNode... args) {return interfaceMethod(node, loader, Arrays.asList(args));}
	public static Invoke interfaceMethod(MethodNode node, ExprNode loader, @WillChange List<ExprNode> args) {return invoke(node, loader, args, INTERFACE_CLASS);}

	public static Invoke constructor(MethodNode node) {return constructor(node, Collections.emptyList());}
	public static Invoke constructor(MethodNode node, @WillChange ExprNode... args) {return constructor(node, Arrays.asList(args));}
	public static Invoke constructor(MethodNode node, @WillChange List<ExprNode> args) {
		if (!node.name().equals("<init>")) throw new IllegalArgumentException("调用的不是构造函数");
		return invoke(node, new Type(node.owner), args, 0);
	}

	static Invoke invoke(MethodNode method, Object fn, List<ExprNode> args, int flag) {
		var node = new Invoke(fn, args);
		node.flag = (byte) flag;
		if (method != null) {
			node.methodNode = method;
			node.desc = Helpers.cast(TypeHelper.parseMethod(method.rawDesc()));
			node.flag |= RESOLVED;
			// 不可能在生成的代码中出现，仅供插件使用
			if ((method.modifier&ACC_INTERFACE) != 0) node.flag |= INTERFACE_CLASS;
			if (((method.modifier&Opcodes.ACC_STATIC) == 0) == (fn == null)) throw new IllegalArgumentException("静态参数错误:"+node);
			if (args.size() != node.desc.size()-1) throw new IllegalArgumentException("参数数量错误:"+node);
		}
		return node;
	}


	private static final ToIntMap<Class<?>> TypeId = new ToIntMap<>();
	static {
		TypeId.putInt(This.class, 0);
		TypeId.putInt(DotGet.class, 1);
		TypeId.putInt(Constant.class, 2);
		TypeId.putInt(LocalVariable.class, 3);
	}

	public Invoke(Object fn, List<ExprNode> args) {
		if (fn != null) {
			int type = TypeId.getOrDefault(fn.getClass(), -1);
			if (type < 0 && !(fn instanceof IType)) throw new IllegalArgumentException("不支持的表达式类型 "+fn);
			if (type == 2 && ((Constant) fn).constVal().getClass() != String.class) {
				LocalContext ctx = LocalContext.get();
				ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", "invoke.method", fn, ctx.currentCodeBlockForReport());
			}
		}
		this.fn = fn;
		this.args = args;
	}

	@Override
	public String toString() {
		CharList sb = new CharList();
		if (!(fn instanceof IType)) {
			if (fn != null) sb.append(fn); else if (methodNode != null) sb.append(methodNode.owner);
			if (methodNode != null) sb.replace('/', '.').append('.').append(methodNode.name());
		} else sb.append("new ").append(fn);
		return TextUtil.join(args, ", ", sb.append('(')).append(')').toStringAndFree();
	}

	static final byte RESOLVED = 1, INVOKE_SPECIAL = ComponentList.THIS_ONLY, INTERFACE_CLASS = 4, POLYSIGN = 8, ARG0_IS_THIS = 16;
	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		if ((flag&RESOLVED) != 0) return this;
		flag |= RESOLVED;

		IType ownMirror = null;
		List<IType> tpHint = bounds;
		if (tpHint != null) {
			for (int i = 0; i < tpHint.size(); i++) ctx.resolveType(tpHint.get(i));
		}

		boolean notStatic = true;
		IClass type;
		String klass, method;
		block:
		if (fn.getClass() == DotGet.class) {
			DotGet fn1 = (DotGet) fn;

			method = fn1.names.pop();

			ExprNode fn2;
			// 只有一个点
			check:
			if (fn1.names.isEmpty()) {
				if (fn1.parent == null) {
					notStatic = false;

					// 省略this : a() => this.a();
					fn2 = ctx.ep.This().resolve(ctx);

					ComponentList list = ctx.methodListOrReport(ctx.file, method);
					if (list != null) {
						/*  这么设计源于下列代码无法编译
							class TestN {
								{ a(); }
								void a (int a) {}
							}
							void a() {}
						 */
						for (MethodNode mn : list.getMethods()) {
							if (ctx.checkAccessible(ctx.classes.getClassInfo(mn.owner), mn, false, false)) {
								break check;
							}
						}
					}

					var mn = ctx.tryImportMethod(method, args);
					// 静态导入
					if (mn != null) {
						type = mn.owner;
						if (type == null) return mn.parent();
						method = mn.method;
						fn = mn.prev;
						break block;
					}

					if(ctx.classes.hasFeature(LavaFeatures.OMISSION_NEW)) {
						type = ctx.resolveType(method);
						if (type != null) {
							// 构造器
							method = "<init>";
							fn = new Type(type.name());
							break block;
						}
					}

					// will fail 但是要打印错误
				} else {
					// ((java.lang.Object) System.out).println(1);
					fn2 = fn1.parent.resolve(ctx);

					// [Type].[this|super]也走这个分支
					if (fn2 == ctx.ep.Super() || fn2 instanceof EncloseRef ref && !ref.thisEnclosing) {
						flag |= INVOKE_SPECIAL;
					}
				}
			} else {
				// [x.y].a();
				fn2 = fn1.resolveEx(ctx, x -> fn = x, method);

				if (fn2 == null) {
					if (fn instanceof Type t) {
						// 构造器
						method = "<init>";
						type = Objects.requireNonNull(ctx.getClassOrArray(t));
					} else {
						// 静态方法
						type = (IClass) fn;
						fn = null;
					}
					break block;
				}
			}

			fn = fn2;
			// 如果是this，那么要擦到上界 (哦this本来就没有generic，霉逝了)
			ownMirror = fn2.type();
			// Notfound
			if (ownMirror == Asterisk.anyType) return NaE.RESOLVE_FAILED;

			if (ownMirror.isPrimitive()) {
				type = ctx.getPrimitiveMethod(ownMirror, fn2, args);
				if (type == null) {
					ctx.report(Kind.ERROR, "symbol.error.derefPrimitive", ownMirror);
					return NaE.RESOLVE_FAILED;
				} else {
					SimpleList<ExprNode> tmp = Helpers.cast(ctx.tmpList); tmp.clear();
					tmp.add(fn2); tmp.addAll(args);
					args = ctx.ep.copyOf(tmp);
					fn = null;
					tmp.clear();
				}
			} else {
				type = ctx.getClassOrArray(ownMirror);
				if (type == null) {
					ctx.report(Kind.ERROR, "symbol.error.noSuchClass", ownMirror);
					return NaE.RESOLVE_FAILED;
				}
			}
		} else if (fn.getClass() == This.class) {// this / super
			if (!ctx.in_constructor | !ctx.not_invoke_constructor) {
				ctx.report(Kind.ERROR, "invoke.error.constructor", fn);
				return NaE.RESOLVE_FAILED;
			}

			flag |= INVOKE_SPECIAL;
			ownMirror = ((This) fn).resolve(ctx).type();
			type = ctx.classes.getClassInfo(ownMirror.owner());
			method = "<init>";

			if ((ctx.file.modifier()&Opcodes.ACC_ENUM) != 0) {
				args = EnumUtil.prependEnumConstructor(args);
			}
		} else {// new type
			ownMirror = ctx.resolveType((IType) fn);
			if (Inferrer.hasUndefined(ownMirror)) ctx.report(Kind.ERROR, "invoke.noExact");
			klass = ownMirror.owner();
			method = "<init>";

			type = ctx.classes.getClassInfo(klass);
			if (type == null) {
				ctx.report(Kind.ERROR, "symbol.error.noSuchClass", klass);
				return NaE.RESOLVE_FAILED;
			}

			int check_flag = ctx.file == type ? ACC_ABSTRACT|ACC_INTERFACE : ACC_ABSTRACT|ACC_INTERFACE|ACC_ENUM;
			if ((type.modifier()&check_flag) != 0) {
				ctx.report(Kind.ERROR, (type.modifier()&ACC_ENUM) != 0 ? "invoke.error.instantiationEnum" : "invoke.error.instantiationAbstract", klass);
				return NaE.RESOLVE_FAILED;
			}
		}

		var innerClassFlags = ctx.classes.getInnerClassFlags(type);
		if ((flag&ARG0_IS_THIS) == 0 == innerClassFlags.containsKey(type)) {
			ctx.report(Kind.ERROR, "invoke.error.notThisInnerClass");
		}

		SimpleList<IType> tmp = Helpers.cast(ctx.tmpList);
		Map<String, IType> namedType = Collections.emptyMap();
		int size = args.size()-1;
		if (size >= 0) {
			for (int i = 0; i < size; i++) args.set(i, args.get(i).resolve(ctx));
			ExprNode last = args.get(size).resolve(ctx);
			args.set(size, last);

			tmp.clear();
			for (int i = 0; i < size; i++) tmp.add(args.get(i).type());

			if (last.getClass() != NamedParamList.class) tmp.add(last.type());
			else namedType = ((NamedParamList) last).getExtraParams();
		} else tmp.clear();

		block: {
			ctx.assertAccessible(type);

			ComponentList list = ctx.methodListOrReport(type, method);
			if (list == null) {
				ctx.report(Kind.ERROR, "symbol.error.noSuchSymbol", method.equals("<init>") ? "invoke.constructor" : "invoke.method", method+"("+TextUtil.join(tmp, ",")+")", "\1symbol.type\0 "+type.name(),
					reportSimilarMethod(ctx, type, method));
				return NaE.RESOLVE_FAILED;
			}

			int flags = fn == null ? ComponentList.IN_STATIC : 0;
			if ((flag&INVOKE_SPECIAL) != 0) flags |= ComponentList.THIS_ONLY;

			ctx.inferrer.manualTPBounds = tpHint;
			MethodResult r = list.findMethod(ctx, ownMirror, tmp, namedType, flags);
			ctx.inferrer.manualTPBounds = null;
			if (r == null) return NaE.RESOLVE_FAILED;

			type = ctx.classes.getClassInfo(r.method.owner);
			if ((type.modifier()&Opcodes.ACC_INTERFACE) != 0) flag |= INTERFACE_CLASS;

			ctx.checkType(r.method.returnType().owner);

			MethodNode mn = r.method;
			methodNode = mn;
			genType1 = ownMirror;

			// Object#getClass的特殊处理
			if (mn.name().equals("getClass") && mn.rawDesc().equals("()Ljava/lang/Class;")) {
				Generic val = Objects.requireNonNull(ownMirror) instanceof Generic ? (Generic) ownMirror.clone() : new Generic(ownMirror.owner(), ownMirror.array(), Generic.EX_EXTENDS);
				val.extendType = Generic.EX_EXTENDS;
				desc = Collections.singletonList(new Generic("java/lang/Class", Collections.singletonList(val)));
			} else if (mn == GlobalContext.arrayClone()) {
				// 数组的clone方法的特殊处理
				desc = Collections.singletonList(Asterisk.genericReturn(ownMirror, LocalContext.OBJECT_TYPE));
			} else {
				desc = r.desc != null ? Arrays.asList(r.desc) : Helpers.cast(TypeHelper.parseMethod(mn.rawDesc()));
			}

			if ((mn.modifier&Opcodes.ACC_STATIC) != 0) {
				if (fn != null) {
					if (notStatic) ctx.report(Kind.SEVERE_WARNING, "symbol.warn.static_on_half", mn.owner, mn.name(), "invoke.method");
					else fn = null; // should be this
				}
				//fn = null;
			} else if ((mn.modifier&Opcodes.ACC_PRIVATE) != 0) {
				flag |= INVOKE_SPECIAL;
			}

			if ((mn.modifier & Opcodes.ACC_VARARGS) != 0) {
				var polySign = ctx.getAnnotation(type, mn, "java/lang/invoke/MethodHandle$PolymorphicSignature", true);
				if (polySign != null) {
					methodNode = new MethodNode(mn.modifier, mn.owner, mn.name(), "()V");
					List<Type> pars = methodNode.parameters();
					for (int i = 0; i < tmp.size(); i++)
						pars.add(tmp.get(i).rawType());

					// TODO returnValue
					flag |= POLYSIGN;
					break block;
				}

				if (!r.directVarargCall) {
					int pSize = mn.parameters().size() - 1;
					Type arrType = mn.parameters().get(pSize);
					List<ExprNode> nodes = args.subList(pSize, args.size());
					ArrayDef def = new ArrayDef(arrType, new SimpleList<>(nodes), false);
					nodes.clear();
					if (args == Collections.EMPTY_LIST) args = new SimpleList<>();
					args.add(def.resolve(ctx));
				}
			}

			IntMap<Object> params = r.namedParams;
			if (params != null) {
				NamedParamList npl = namedType == Collections.EMPTY_MAP ? null : (NamedParamList) args.remove(args.size() - 1);
				if (args == Collections.EMPTY_LIST) args = new SimpleList<>();
				for (IntMap.Entry<Object> entry : params.selfEntrySet()) {
					while (args.size() <= entry.getIntKey()) args.add(null);
					Object value = entry.getValue();
					if (value.getClass() == String.class) value = npl.map.get(value);
					args.set(entry.getIntKey(), (ExprNode) value);
				}
			}

			if (mn.attrByName(Evaluable.NAME) instanceof Evaluable eval) {
				ExprNode result = eval.eval(mn, getParent(), args, this);
				if (result != null) return result.resolve(ctx);
			}

			r.addExceptions(ctx, false);
		}

		tmp.clear();
		return this;
	}
	private String reportSimilarMethod(LocalContext ctx, IClass type, String method) {
		var maybeWrongMethod = new SimpleList<String>();
		loop:
		for (var entry : ctx.classes.getResolveHelper(type).getMethods(ctx.classes).entrySet()) {
			for (MethodNode node : entry.getValue().getMethods()) {
				int parSize = node.parameters().size();
				int argSize = args.size();
				if ((node.modifier & Opcodes.ACC_VARARGS) != 0 ? argSize < parSize - 1 : argSize != parSize) {
					continue loop;
				}
			}
			if (TextUtil.editDistance(method, entry.getKey()) < (method.length()+1)/2) {
				maybeWrongMethod.add(entry.getKey());
			}
		}
		if (maybeWrongMethod.isEmpty()) return "";

		var sb = ctx.getTmpSb().append("\1symbol.similar:\1invoke.method\0:");
		sb.append(TextUtil.join(maybeWrongMethod, "\n    "));
		return sb.append('\0').toString();
	}

	@Override
	public boolean hasFeature(ExprFeat kind) {
		if (kind == ExprFeat.INVOKE_CONSTRUCTOR) return fn instanceof This;
		if (kind == ExprFeat.TAILREC) return methodNode == LocalContext.get().method;
		return false;
	}
	@Override
	public IType type() { return fn instanceof IType ? ((IType) fn) : desc != null ? desc.get(desc.size()-1) : Asterisk.anyType; }

	private boolean checkTailrec(LocalContext ctx, MethodWriter cw) {
		var mn = methodNode;
		if (mn != ctx.method) return false;

		Annotation tailrec;
		// static，private，final或者@Tailrec(true)
		if ((tailrec = ctx.getAnnotation(ctx.file, mn, "roj/compiler/api/Tailrec", false)) != null
			? tailrec.getBoolean("value", true)
			: (mn.modifier&(ACC_STATIC|ACC_PRIVATE|ACC_FINAL)) != 0
		) {
			int slot = (mn.modifier&ACC_STATIC) != 0 ? 0 : 1;
			var argType = mn.parameters();// not desc, not generic
			var argVal = this.args;
			// 可能会使用当前的变量，所以把结果放在栈上
			for (int i = 0; i < argVal.size(); i++) {
				var node = argVal.get(i);
				node.write(cw, ctx.castTo(node.type(), argType.get(i), 0));
			}
			for (int i = 0; i < argVal.size(); i++) {
				Type type = argType.get(i);
				cw.varStore(type, slot);
				slot += type.length();
			}

			cw.jump(Label.atZero());
			return true;
		}
		return false;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		var lc = LocalContext.get();
		if (lc.inReturn && checkTailrec(lc, cw)) return;

		boolean isSet = false;
		var ifNull = DotGet.NULLISH_TARGET.get();

		byte opcode;
		// 明显不是构造器
		if ((methodNode.modifier&Opcodes.ACC_STATIC) != 0) {
			if (fn != null) {
				// 明显是对象
				((ExprNode) fn).write(cw);
				cw.one(Opcodes.POP);
			}
			opcode = Opcodes.INVOKESTATIC;
		} else if (fn instanceof ExprNode expr) {
			var cast = genType1 == null || genType1.genericType() >= IType.ASTERISK_TYPE ? null : lc.castTo(genType1, new Type(methodNode.owner), 0);

			int v = 0;
			if (expr instanceof DotGet dg && (v = dg.isNullish()) != 0) {
				if (ifNull == null) {
					isSet = true;
					DotGet.NULLISH_TARGET.set(ifNull = new Label());
				}
			}
			expr.write(cw, cast);

			if (v == 2) {
				cw.one(DUP);
				cw.jump(IFNULL, ifNull);
			}

			opcode = (flag&INVOKE_SPECIAL) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL;
		} else {
			Type rawType = ((IType) fn).rawType();
			// 明显不是数组
			cw.clazz(Opcodes.NEW, rawType.owner());
			cw.one(Opcodes.DUP);
			opcode = Opcodes.INVOKESPECIAL;
		}

		if (ifNull != null)
			//noinspection ThreadLocalSetWithNull
			DotGet.NULLISH_TARGET.set(null);
		for (int i = 0; i < args.size(); i++) {
			ExprNode expr = args.get(i);
			expr.write(cw, lc.castTo(expr.type(), desc.get(i), 0));
		}
		if (ifNull != null) DotGet.NULLISH_TARGET.set(ifNull);

		if ((flag&INTERFACE_CLASS) != 0) {
			if (opcode == Opcodes.INVOKESTATIC) {
				LocalContext.get().file.setMinimumBinaryCompatibility(LavaFeatures.COMPATIBILITY_LEVEL_JAVA_8);
			}
			cw.invoke(opcode == Opcodes.INVOKEVIRTUAL ? Opcodes.INVOKEINTERFACE : opcode, methodNode.owner, methodNode.name(), methodNode.rawDesc(), true);
		} else {
			cw.invoke(opcode, methodNode);
		}

		// pop or pop2
		if (!methodNode.rawDesc().endsWith(")V") && noRet) cw.one((byte) (0x56 + methodNode.returnType().length()));

		if (isSet) DotGet.writeNullishTarget(cw, ifNull, noRet ? Type.std(Type.VOID) : methodNode.returnType());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof Invoke node)) return false;

		if (!fn.equals(node.fn)) return false;
		if (!args.equals(node.args)) return false;
		return bounds != null ? bounds.equals(node.bounds) : node.bounds == null;
	}

	@Override
	public int hashCode() {
		int result = fn.hashCode();
		result = 31 * result + args.hashCode();
		result = 31 * result + (bounds != null ? bounds.hashCode() : 0);
		return result;
	}

	// 泛型边界，用于推断
	public void setBounds(List<IType> bounds) { this.bounds = bounds; }

	public boolean isNew() {return fn instanceof IType;}
	public ExprNode getParent() {return fn instanceof ExprNode expr ? expr : null;}
	public MethodNode getMethod() {return methodNode;}
	public List<ExprNode> getArguments() {return args;}
	/**
	 * 仅给静态工厂准备，指定该表达式的泛型返回值
	 */
	public void setGenericReturnType(IType genericReturnType) {desc.set(desc.size()-1, genericReturnType);}

	public Invoke setEnclosingArg() {
		this.flag |= ARG0_IS_THIS;
		// TODO
		GlobalContext.debugLogger().warn("NewEnclosingClass is not finished");
		return this;
	}
}