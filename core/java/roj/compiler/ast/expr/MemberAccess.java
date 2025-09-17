package roj.compiler.ast.expr;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassDefinition;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.annotation.AnnVal;
import roj.asm.insn.Label;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.collect.ArrayList;
import roj.compiler.CompileContext;
import roj.compiler.LavaCompiler;
import roj.compiler.api.Compiler;
import roj.compiler.api.FieldAccessHook;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.FieldResult;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.compiler.runtime.SwitchMap;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.function.Flow;

import java.util.function.Consumer;

/**
 * AST - 级联成员访问.
 *
 * <p>典型结构示例：
 * <pre>{@code
 * System.out
 * obj.field
 * list.size()
 * array.length
 * }</pre>
 *
 * @author Roj233
 * @see Invoke#resolve(CompileContext) 在方法调用表达式中的使用
 * @since 2022/2/27 20:27
 */
final class MemberAccess extends LeftValue {
	public static final String EMPTY_BRACKET_MAGIC = ";[";

	static final ThreadLocal<Label> NULLISH_TARGET = new ThreadLocal<>();
	static void writeNullishExpr(MethodWriter cw, Label ifNull, IType targetType, Expr caller) {
		NULLISH_TARGET.remove();

		var end = new Label();
		cw.jump(end);
		cw.label(ifNull);
		cw.insn(Opcodes.POP);
		int type1 = targetType.getActualType();
		if (type1 != Type.VOID) {
			if (type1 != Type.CLASS) cw.ctx.report(caller, Kind.ERROR, "symbol.derefPrimitive", targetType);
			cw.insn(Opcodes.ACONST_NULL);
		}
		cw.label(end);
	}

	//package-private: Invoke会修改这些字段
	Expr parent;
	ArrayList<String> nameChain;

	private long nullishBits;
	private static final byte ARRAY_LENGTH = 1, FINAL_FIELD = 2, SELF_FIELD = 4, RESOLVED = 8;
	private byte flags;

	// 解析时填充
	private ClassDefinition owner; // 静态字段的所属
	private FieldNode[] chain; // 字段访问链
	private IType resultType; // 表达式结果类型

	private static final SwitchMap TypeId = SwitchMap.Builder
			.builder(10, true)

			// Notnull
			.add(This.class, 1)
			.add(QualifiedThis.class, 1)
			.add(Literal.class, 1)
			.add(NewArray.class, 1)
			.add(NewAnonymousClass.class, 1)

			.add(Invoke.class, 2)
			.add(ArrayAccess.class, 2)
			.add(BinaryOp.class, 2)

			.add(Cast.class, 3)

			// only after resolve (via getOperatorOverride)
			.add(LocalVariable.class, 114)
			.build();

	public MemberAccess(@Nullable Expr parent, String name, int flag) {
		this.parent = parent;
		this.nameChain = new ArrayList<>(4);
		this.nameChain.add(name);
		// a() ?. expr
		if (flag != 0) nullishBits = 1;

		if (parent != null) {
			int type = TypeId.get(parent.getClass());
			if (type == 0) throw new IllegalArgumentException("未识别的parent:"+parent.getClass().getName());
			this.flags = (byte) type;
		} else {
			this.flags = -128;
		}
	}
	MemberAccess() {}

	public static Expr fieldChain(Expr parent, ClassDefinition begin, IType type, boolean isFinal, FieldNode... chain) {
		MemberAccess el = new MemberAccess();
		el.nameChain = (ArrayList<String>) Flow.of(chain).map(FieldNode::name).toList();
		el.parent = parent;
		el.owner = begin;
		el.chain = chain;
		el.resultType = type == null ? chain[chain.length-1].fieldType() : type;
		el.flags = (byte) (RESOLVED | (isFinal ? FINAL_FIELD : 0));
		return el;
	}

	Type toClassRef() {
		CharList sb = CompileContext.get().getTmpSb();
		int i = 0;
		String part = nameChain.get(0);
		while (true) {
			sb.append(part);
			if (++i == nameChain.size() || (part = nameChain.get(i)).equals(EMPTY_BRACKET_MAGIC)) break;
			sb.append('/');
		}

		return Type.klass(sb.toString(), nameChain.size() - i);
	}

	@Override
	public String toString() {
		CharList sb = new CharList();
		if (parent != null) sb.append(parent).append('.');
		return sb.append(TextUtil.join(nameChain, ".")).toStringAndFree();
	}

	@NotNull
	@Override
	public Expr resolve(CompileContext ctx) throws ResolveException { return resolveEx(ctx, null, null); }
	/**
	 * @param classExprTarget 如果这个表达式不能作为字段访问解析，尝试作为类访问解析，并把结果交给它
	 * @param lastSegment Invoke解析时候拿走的方法名称，用于New省略语法
	 * @see Invoke#resolve(CompileContext)
	 */
	@Contract("_, null, _ -> !null")
	final Expr resolveEx(CompileContext ctx, Consumer<Object> classExprTarget, String lastSegment) throws ResolveException {
		if ((flags&RESOLVED) != 0) return this;

		// 用于错误提示
		FieldResult inaccessibleThis = null;

		if (flags < 0) {
			// 首先我们要拿到第一个field
			// 这里按顺序处理下列情况

			String part = nameChain.get(0);

			Variable varParent = ctx.getVariable(part);
			check:
			if (varParent != null) {
				// 1. 变量
				var varNode = new LocalVariable(varParent);
				varNode.wordStart = wordStart;
				varNode.wordEnd = wordEnd;

				parent = varNode;
				if (nameChain.size() == 1) return parent;

				nameChain.remove(0);
				flags = 0;
				nullishBits >>>= 1;
			} else {
				// 2. 省略this的当前类字段（包括继承！）
				// * => 在错误处理中需要二次检查以生成更有帮助的错误信息（static）

				var fieldList = ctx.getFieldList(ctx.file, part);
				if (fieldList != ComponentList.NOT_FOUND) {
					inaccessibleThis = fieldList.findField(ctx, ctx.inStatic ? ComponentList.IN_STATIC : 0);
					if (inaccessibleThis.error == null) {
						owner = ctx.file;
						parent = (inaccessibleThis.field.modifier&Opcodes.ACC_STATIC) != 0 ? null : ctx.ep.This();
						flags = 0;
						break check;
					}
				}

				CompileContext.Import result;
				// 3. 静态字段导入
				if ((result = ctx.tryImportField(part)) != null) {
					owner = result.owner;
					if (owner == null) return result.parent();
					nameChain.set(0, result.name);
					parent = result.parent();
					flags = 0;
				}
			}

			// 4. 前缀包名.字段
		}

		String part = nameChain.get(0);
		int i = 0;
		CharList sb = ctx.getTmpSb();
		while (true) {
			sb.append(part);
			if (++i == nameChain.size()) break;
			if ((part = nameChain.get(i)).equals(EMPTY_BRACKET_MAGIC)) {
				ctx.report(this, Kind.ERROR, "memberAccess.emptyArrayIndex");
				return NaE.resolveFailed(this);
			}
			sb.append('/');
		}

		if (flags >= 0) { // parent不为null, 下面是处理importField
			IType fType;
			ClassDefinition symbol;

			if (parent == null) {
				fType = Type.klass(owner.name());
				symbol = owner;
			} else {
				fType = (parent = parent.resolve(ctx)).type();
				if (fType.isPrimitive()) {
					ctx.report(this, Kind.ERROR, "symbol.derefPrimitive", fType);
					return NaE.resolveFailed(this);
				}

				symbol = ctx.resolve(fType);
				if (symbol == null) {
					ctx.reportNoSuchType(this, Kind.ERROR, fType);
					return NaE.resolveFailed(this);
				}

				checkNullishDecl(ctx);
			}

			String error = ctx.resolveField(symbol, fType, sb);
			if (error != null) {
				ctx.report(this, Kind.ERROR, error);
				return NaE.resolveFailed(this);
			}

			part = fType.owner();
		} else {
			// 4. 前缀包名.字段 ^
			// ^ => 我决定采用（已经设计好的）这种机制，虽然可能没必要
			String error = ctx.resolveDotGet(sb, classExprTarget != null);

			if (nullishBits != 0) {
				int offset = ctx.get_frOffset()+1;
				if (Long.numberOfTrailingZeros(nullishBits) <= offset) ctx.report(this, Kind.ERROR, "memberAccess.opChain.inClassDecl");
				nullishBits >>>= offset;
			}

			if (error != null) {
				if (error.isEmpty()) {
					assert classExprTarget != null;
					classExprTarget.accept(ctx.get_frBegin());
					return null;
				}

				if (lastSegment != null && ctx.compiler.hasFeature(Compiler.OMISSION_NEW)) {
					var checkConstructor = ctx.resolveDotGet(sb.append('/').append(lastSegment), true);
					if ("".equals(checkConstructor)) {
						assert classExprTarget != null;
						classExprTarget.accept(Type.klass(ctx.get_frBegin().name()));
						return null;
					}
				}

				if (inaccessibleThis != null) error = inaccessibleThis.error;
				if (ctx.firstError != null) ctx.reportFirstError(this);
				else ctx.report(this, Kind.ERROR, error);
				return NaE.resolveFailed(this);
			}

			part = ctx.get_frChain().get(0).fieldType().owner();
		}

		owner = ctx.get_frBegin();
		chain = ctx.get_frChain().toArray(new FieldNode[ctx.get_frChain().size()]);
		resultType = ctx.get_frType();

		ctx.checkTypeRestriction(part);

		flags = RESOLVED;

		int length = chain.length;
		FieldNode last = chain[length-1];

		// length不是字段而是opcode
		if (last == LavaCompiler.arrayLength()) {
			flags |= FINAL_FIELD|ARRAY_LENGTH;
			resultType = Type.INT_TYPE;
			length--;
		} else if (resultType == null) {
			// get_frType只处理泛型
			resultType = last.fieldType();
		}

		FieldNode fn;
		int i1 = 1;
		if (i1 < length) for (;;) {
			fn = chain[i1];
			if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
				ctx.report(this, Kind.SEVERE_WARNING, "symbol.isStatic", part, fn.name(), "symbol.field");
			}

			if (++i1 == length) break;
			part = fn.fieldType().owner();
		} else  {
			fn = chain[0];
			assert chain.length == 1;

			// 替换常量 如你所见只有直接访问(<class>.<field>)才会替换,如果中途使用了非静态字段会警告，👆
			// 大概也用不到泛型... 不过还是留着type参数
			Expr node = ctx.getConstantValue(owner, fn, resultType);
			if (node != null) return node;
		}

		if (ctx.fieldDFS) ctx.checkSelfField(chain[chain.length-1], false);

		if (isStaticField()) nullishBits &= ~1;
		if (nullishBits != 0) {
			flags |= FINAL_FIELD;
			if ((flags&ARRAY_LENGTH) != 0) ctx.report(this, Kind.ERROR, "memberAccess.opChain.arrayLen");
		} else if ((fn.modifier&Opcodes.ACC_FINAL) != 0) flags |= FINAL_FIELD;

		// == is better
		//noinspection all
		if (part != null && part == ctx.file.name()) {
			flags |= SELF_FIELD;
			// redirect check to CompileContext
			if (ctx.inConstructor) flags &= ~FINAL_FIELD;
		}
		return this;
	}

	@Override
	public boolean hasFeature(Feature feature) {
		if (feature == Feature.ENUM_REFERENCE) return isStaticField() && (chain[0].modifier & Opcodes.ACC_ENUM) != 0;
		if (feature == Feature.STATIC_BEGIN) return parent == null;
		return false;
	}

	@Override
	public Object constVal() {return hasFeature(Feature.ENUM_REFERENCE) ? AnnVal.ofEnum(owner.name(), chain[0].name()) : super.constVal();}

	@Override
	public IType type() {return resultType == null ? Asterisk.anyType : resultType;}

	@Override
	protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
		mustBeStatement(cast);

		int length = chain.length - (flags&ARRAY_LENGTH);
		write(cw, length);
		if (length != chain.length) cw.insn(Opcodes.ARRAYLENGTH);
	}

	@Override
	public boolean isFinal() { return (flags&FINAL_FIELD) != 0; }

	@Override
	public void preStore(MethodWriter cw) {
		if ((flags&SELF_FIELD) != 0) CompileContext.get().checkSelfField(chain[chain.length-1], true);
		write(cw, chain.length-1);
	}

	@Override
	public void preLoadStore(MethodWriter cw) {
		if ((flags&SELF_FIELD) != 0) CompileContext.get().checkSelfField(chain[chain.length-1], false);
		write(cw, chain.length-1);
		if (!isStaticField()) {
			/*if (parent instanceof LocalVariable) parent.write(cw);
			else */cw.insn(Opcodes.DUP);
		}

		FieldNode fn = chain[chain.length-1];
		String owner = chain.length == 1 ? this.owner.name() : chain[chain.length-2].fieldType().owner();

		if (fn.getAttribute(FieldAccessHook.NAME) instanceof FieldAccessHook hook) {
			hook.writeRead(cw, owner, fn);
		} else {
			cw.field((fn.modifier & Opcodes.ACC_STATIC) != 0 ? Opcodes.GETSTATIC : Opcodes.GETFIELD, owner, fn.name(), fn.rawDesc());
		}
	}

	@Override
	public void postStore(MethodWriter cw, int state) {
		FieldNode fn = chain[chain.length-1];
		String owner = chain.length == 1 ? this.owner.name() : chain[chain.length-2].fieldType().owner();

		if (fn.getAttribute(FieldAccessHook.NAME) instanceof FieldAccessHook hook) {
			hook.writeWrite(cw, owner, fn);
		} else {
			cw.field((fn.modifier & Opcodes.ACC_STATIC) != 0 ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, owner, fn.name(), fn.rawDesc());
		}
	}

	@Override
	public int copyValue(MethodWriter cw, boolean twoStack) {
		cw.insn(isStaticField() ? twoStack ? Opcodes.DUP2 : Opcodes.DUP : twoStack ? Opcodes.DUP2_X1 : Opcodes.DUP_X1);
		return 0;
	}

	@Override
	public boolean hasSideEffect() { return parent != null || !isStaticField(); }
	private boolean isStaticField() { return chain.length == 1 && (chain[0].modifier & Opcodes.ACC_STATIC) != 0; }

	private void write(MethodWriter cw, int length) {
		int i = 0;
		String owner = this.owner.name();
		FieldNode fn = chain[0];
		byte opcode;

		boolean hasParent = parent != null;
		if (hasParent) parent.write(cw);

		if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
			opcode = Opcodes.GETSTATIC;
			if (hasParent) cw.insn(Opcodes.POP); // field is useless
		} else {
			opcode = Opcodes.GETFIELD;
		}

		if (i == length) return;

		boolean isSet = false;
		Label ifNull;
		if (nullishBits == 0) ifNull = null;
		else {
			ifNull = NULLISH_TARGET.get();
			if (ifNull == null) {
				NULLISH_TARGET.set(ifNull = new Label());
				isSet = true;
			}
		}

		for (;;) {
			if ((nullishBits&(1L << i)) != 0) {
				cw.insn(Opcodes.DUP);
				cw.jump(Opcodes.IFNULL, ifNull);
			}

			if (fn.getAttribute(FieldAccessHook.NAME) instanceof FieldAccessHook hook) {
				hook.writeRead(cw, owner, fn);
			} else {
				cw.field(opcode, owner, fn.name(), fn.rawDesc());
			}
			if (++i == length) break;

			owner = fn.fieldType().owner();
			fn = chain[i];

			if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
				opcode = Opcodes.GETSTATIC;
				cw.insn(Opcodes.POP); // field is useless
			} else {
				opcode = Opcodes.GETFIELD;
			}
		}

		if (isSet) writeNullishExpr(cw, ifNull, fn.fieldType(), this);
	}

	/**
	 * 链式追加
	 * @param name 字段名称
	 * @param flag 标志位，目前只有第一位用掉，代表这是一个可空访问
	 */
	public MemberAccess add(String name, int flag) {
		if (flag != 0) {
			if (nameChain.size() > 64) CompileContext.get().report(this, Kind.ERROR, "memberAccess.opChain.tooLong");
			nullishBits |= 1L << nameChain.size();
		}
		nameChain.add(name);
		return this;
	}

	/**
	 * 表达式是否能作为字符串模板的预处理器名称
	 */
	public boolean maybeStringTemplate() {return parent == null && nameChain.size() == 1 && nullishBits == 0;}

	/**
	 * 0: 非
	 * 1: 存在nullish
	 * 2: 最后一个就是nullish
	 */
	public int isNullish() {
		int off = (chain[0].modifier & Opcodes.ACC_STATIC) != 0 ? 1 : 0;
		return ((1L << nameChain.size() - off) & nullishBits) != 0 ? 2 : nullishBits != 0 ? 1 : 0;
	}
	public void checkNullishDecl(CompileContext ctx) {
		if ((nullishBits&1) != 0 && flags == 1) ctx.report(this, Kind.ERROR, "memberAccess.opChain.inClassDecl");
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MemberAccess get = (MemberAccess) o;

		if (parent != null ? !parent.equals(get.parent) : get.parent != null) return false;
		return nameChain.equals(get.nameChain);
	}

	@Override
	public int hashCode() {
		int result = parent == null ? 42 : parent.hashCode();
		result = 31 * result + nameChain.hashCode();
		result = 31 * result + (int) (nullishBits ^ (nullishBits >>> 32));
		return result;
	}
}