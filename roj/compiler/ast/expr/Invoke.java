package roj.compiler.ast.expr;

import roj.asm.Opcodes;
import roj.asm.tree.IClass;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.Annotation;
import roj.asm.tree.attr.AttrClassList;
import roj.asm.tree.attr.Attribute;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asmlang.InlineAsm;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.MethodResult;
import roj.compiler.resolve.ResolveException;
import roj.config.word.NotStatementException;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.Helpers;

import javax.tools.Diagnostic;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public class Invoke implements ExprNode {
	Object fn; // new anonymous class may change this later
	private List<ExprNode> args;
	private List<IType> bounds;

	private MethodNode methodNode;
	private byte flag;
	private List<IType> desc;
	private IType genType1;

	public static ExprNode unaryAlt(ExprNode fn, MethodNode node) {
		assert node.parameters().size() == 0;
		Invoke invoke = new Invoke(fn, Collections.emptyList());
		invoke.methodNode = node;
		invoke.desc = Collections.singletonList(node.returnType());
		return invoke;
	}

	public static ExprNode binaryAlt(MethodNode override, ExprNode left, ExprNode right) {
		Invoke invoke;
		if ((override.modifier()&Opcodes.ACC_STATIC) != 0) invoke = new Invoke(null, List.of(left, right));
		else invoke = new Invoke(left, Collections.singletonList(right));

		invoke.methodNode = override;
		invoke.desc = Helpers.cast(TypeHelper.parseMethod(override.rawDesc()));
		return invoke;
	}

	private static final ToIntMap<Class<?>> TypeId = new ToIntMap<>();
	static {
		TypeId.putInt(This.class, 0);
		TypeId.putInt(DotGet.class, 1);
		TypeId.putInt(Constant.class, 2);
	}

	public Invoke(Object fn, List<ExprNode> args) {
		if (fn != null) {
			int type = TypeId.getOrDefault(fn.getClass(), -1);
			if (type < 0 && !(fn instanceof IType)) throw new IllegalArgumentException("不支持的表达式类型 "+fn);
			if (type == 2) CompileContext.get().report(Diagnostic.Kind.ERROR, "symbol.error.noSuchSymbol", fn);
		}
		this.fn = fn;
		this.args = args;
	}

	@Override
	public String toString() {
		CharList sb = new CharList();
		if (!(fn instanceof IType)) {
			if (bounds != null) TextUtil.join(bounds, ", ", sb.append('<')).append('>');
			if (fn != null) sb.append(fn); else if (methodNode != null) sb.append(methodNode.owner);
			if (methodNode != null) sb.replace('/', '.').append('.').append(methodNode.name());
		} else sb.append("new ").append(fn);
		return TextUtil.join(args, ", ", sb.append('(')).append(')').toStringAndFree();
	}

	static final byte RESOLVED = 1, INVOKE_SPECIAL = ComponentList.THIS_ONLY, INTERFACE_CLASS = 4, PMSIGN = 8;
	private static final byte MUST_VIRTUAL = ComponentList.IN_STATIC;
	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
		if ((flag&RESOLVED) != 0) return this;
		flag |= RESOLVED;

		IType genHint = null;
		String klass, method;
		int mode = 0;
		block:
		if (fn.getClass() == DotGet.class) {
			DotGet fn1 = (DotGet) fn;

			method = fn1.names.pop();

			ExprNode fn2;
			if (fn1.names.isEmpty()) {
				// 只有一个点
				// ((java.lang.Object) System.out).println(1);
				// 省略this : a() => this.a(); 或继承/什么的静态
				if (fn1.parent == null) {
					MethodNode mn = ctx.tryImportMethod(method);
					// 静态导入
					if (mn != null) {
						klass = mn.owner;
						method = mn.name();
						fn = null;
						break block;
					}

					mode = MUST_VIRTUAL;
					fn2 = ctx.ep.This().resolve(ctx);
				} else {
					fn2 = fn1.parent.resolve(ctx);
					mode = MUST_VIRTUAL;

					// [Type].[this|super]也走这个分支
					if (fn2 == ctx.ep.Super() || fn2 instanceof EncloseRef ref && !ref.thisEnclosing) flag |= INVOKE_SPECIAL;
				}
			} else {
				// [x.y].a();
				fn2 = fn1.resolveEx(ctx, this);

				// 静态方法
				if (fn2 == null) {
					klass = ((IClass) fn).name();
					fn = null;
					break block;
				}
			}

			fn = fn2;
			// 如果是this，那么要擦到上界 (哦this本来就没有generic，霉逝了)
			genHint = fn2.type();
			klass = genHint.owner();
		} else if (fn.getClass() == This.class) {
			if (!ctx.in_constructor | !ctx.first_statement) {
				ctx.report(Diagnostic.Kind.ERROR, "invoke.error.constructor", fn);
				return this;
			}

			flag |= INVOKE_SPECIAL;
			genHint = ((This) fn).resolve(ctx).type();
			klass = genHint.owner();
			method = "<init>";
		} else {
			genHint = ctx.resolveType((IType) fn);
			klass = genHint.owner();
			method = "<init>";

			IClass type = ctx.classes.getClassInfo(klass);
			if (type == null) throw new ResolveException("symbol.error.noSuchClass:"+klass);

			if ((type.modifier()&(Opcodes.ACC_ABSTRACT|Opcodes.ACC_INTERFACE)) != 0) {
				ctx.report(Diagnostic.Kind.ERROR, "invoke.error.instantiationAbstract", klass);
				return this;
			}
		}

		SimpleList<IType> tmp = Helpers.cast(ctx.tmpListForExpr2); tmp.clear();
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
		}

		IClass cn = ctx.resolveType(klass);
		block:
		if (cn != null) {
			ctx.assertAccessible(cn);
			if ((cn.modifier()&Opcodes.ACC_INTERFACE) != 0) flag |= INTERFACE_CLASS;

			ComponentList list = ctx.classes.methodList(cn, method);
			if (list == null) {
				// 错误: 找不到符号
				//  符号:   方法 test(<nulltype>,<nulltype>)
				//  位置: 类 roj.compiler.util.InvokeHelper.MethodCandidate
				ctx.report(Diagnostic.Kind.ERROR, "symbol.error.noSuchSymbol:"+method);
				break block;
			}

			if (bounds != null) {
				assert genHint != null : "unexpected state";
				Generic g = new Generic(genHint.owner(), genHint.array(), (byte) 0);
				genHint = g;
				g.children = bounds;
			}

			boolean staticEnv = (mode&MUST_VIRTUAL) != 0 ? ctx.in_static : fn == null;
			int flags = (flag&INVOKE_SPECIAL) | (staticEnv ? ComponentList.IN_STATIC : 0);

			MethodResult r = list.findMethod(ctx, genHint, tmp, namedType, flags);
			if (r == null) break block;

			MethodNode mn = r.method;
			methodNode = mn;
			genType1 = genHint;

			// TODO inline asm
			if (mn.owner.equals("roj/compiler/api/ASM")) {
				switch (mn.name()) {
					case "begin":
					case "end":
					case "asm":
						ExprNode node = args.get(0);
						ctx.report(Diagnostic.Kind.ERROR, "asm.error.not_implemented", node);
						return new InlineAsm((String) node.constVal());
					case "i2z", "z2i": return args.get(0);
				}
			}

			// Object#getClass泛型的特殊处理
			if (mn.name().equals("getClass") && mn.rawDesc().equals("()Ljava/lang/Class;")) {
				Generic val = genHint instanceof Generic ? (Generic) genHint.clone() : new Generic(genHint.owner(), genHint.array(), Generic.EX_NONE);
				val.extendType = Generic.EX_EXTENDS;
				desc = Collections.singletonList(new Generic("java/lang/Class", Collections.singletonList(val)));
			} else {
				desc = r.desc != null ? Arrays.asList(r.desc) : Helpers.cast(mn.parameters());
			}

			if (r.exception != null) {
				for (IType ex : r.exception) {
					ctx.addException(ex);
				}
			} else {
				AttrClassList exAttr = methodNode.parsedAttr(cn.cp(), Attribute.Exceptions);
				if (exAttr != null) {
					List<String> value = exAttr.value;
					for (int i = 0; i < value.size(); i++)
						ctx.addException(new Type(value.get(i)));
				}
			}

			if ((mn.modifier&Opcodes.ACC_STATIC) != 0) {
				if (!staticEnv & (mode&MUST_VIRTUAL) != 0)
					ctx.report(Diagnostic.Kind.MANDATORY_WARNING, "symbol.warn.static_on_half", mn.owner, mn.name(), "invoke.method");
				fn = null;
			}

			if ((mn.modifier & Opcodes.ACC_VARARGS) != 0) {
				Annotation pmsign = ctx.getAnnotation(cn, mn, "java/lang/invoke/MethodHandle$PolymorphicSignature", true);
				if (pmsign != null) {
					methodNode = new MethodNode(mn.modifier, mn.owner, mn.name(), "()V");
					List<Type> pars = methodNode.parameters(); pars.clear();
					for (int i = 0; i < tmp.size(); i++)
						pars.add(tmp.get(i).rawType());

					// result = Asterisk.nulltype;
					flag |= PMSIGN;
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
		} else {
			ctx.report(Diagnostic.Kind.ERROR, "symbol.error.noSuchClass", klass);
		}

		tmp.clear();
		return this;
	}

	@Override
	public IType type() { return fn instanceof IType ? ((IType) fn) : desc != null ? desc.get(desc.size()-1) : Asterisk.anyType; }

	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {
		byte opcode;
		// 明显不是构造器
		if ((methodNode.modifier&Opcodes.ACC_STATIC) != 0) {
			if (fn != null) {
				// 明显是对象
				((ExprNode) fn).write(cw, false);
				cw.one(Opcodes.POP);
			}
			opcode = Opcodes.INVOKESTATIC;
		} else if (fn instanceof ExprNode expr) {
			expr.writeDyn(cw, genType1 != null && genType1.genericType() < IType.ASTERISK_TYPE ? null : cw.ctx1.castTo(genType1, new Type(methodNode.owner), 0));
			opcode = (flag&INVOKE_SPECIAL) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL;
		} else {
			Type rawType = ((IType) fn).rawType();
			// 明显不是数组
			cw.clazz(Opcodes.NEW, rawType.owner());
			cw.one(Opcodes.DUP);
			opcode = Opcodes.INVOKESPECIAL;
		}

		for (int i = 0; i < args.size(); i++) {
			ExprNode expr = args.get(i);
			expr.writeDyn(cw, cw.ctx1.castTo(expr.type(), desc.get(i), 0));
		}

		if ((flag&INTERFACE_CLASS) != 0) {
			if (opcode == Opcodes.INVOKESTATIC) {
				cw.invoke(Opcodes.INVOKESTATIC, methodNode.owner, methodNode.name(), methodNode.rawDesc(), true);
			} else {
				assert opcode == Opcodes.INVOKEVIRTUAL;
				cw.invoke(Opcodes.INVOKEINTERFACE, methodNode);
			}
		} else {
			cw.invoke(opcode, methodNode);
		}

		// pop or pop2
		if (!methodNode.rawDesc().endsWith(")V") && noRet) cw.one((byte) (0x56 + methodNode.returnType().length()));
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof Invoke node)) return false;

		if (!fn.equals(node.fn)) return false;
		if (!args.equals(node.args)) return false;
		return bounds != null ? bounds.equals(node.bounds) : node.bounds == null;
	}

	// 泛型边界，用于推断
	public void setBounds(List<IType> bounds) { this.bounds = bounds; }
}