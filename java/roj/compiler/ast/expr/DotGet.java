package roj.compiler.ast.expr;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.FieldNode;
import roj.asm.tree.IClass;
import roj.asm.tree.anno.AnnValEnum;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.visitor.Label;
import roj.collect.SimpleList;
import roj.collect.ToIntMap;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.asm.Variable;
import roj.compiler.context.GlobalContext;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ComponentList;
import roj.compiler.resolve.FieldResult;
import roj.compiler.resolve.ResolveException;
import roj.text.CharList;
import roj.text.TextUtil;

import java.util.function.Consumer;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2022/2/27 20:27
 */
public final class DotGet extends VarNode {
	ExprNode parent;
	SimpleList<String> names;

	private byte flags;
	private long bits;

	private IClass begin;
	private FieldNode[] chain;
	private IType type;

	private static final byte ARRAY_LENGTH = 1, FINAL_FIELD = 2, SELF_FIELD = 4, RESOLVED = 8;

	private static final ToIntMap<Class<?>> TypeId = new ToIntMap<>();
	static {
		TypeId.putInt(This.class, 0);
		TypeId.putInt(Invoke.class, 1);
		TypeId.putInt(ArrayGet.class, 2);
		TypeId.putInt(ArrayDef.class, 3);
		TypeId.putInt(Constant.class, 4);
		TypeId.putInt(Cast.class, 5);
		TypeId.putInt(EncloseRef.class, 6);

		// only after resolve (via getOperatorOverride)
		TypeId.putInt(LocalVariable.class, 114);
	}

	public DotGet(@Nullable ExprNode parent, String name, int flag) {
		this.parent = parent;
		this.names = new SimpleList<>(4);
		this.names.add(name);
		// a() ?. expr
		if (flag != 0) bits = 1;

		if (parent != null) {
			int type = TypeId.getOrDefault(parent.getClass(), -1);
			if (type == -1) throw new IllegalArgumentException("未识别的parent:"+parent.getClass().getName());
			this.flags = (byte) type;
		} else {
			this.flags = -128;
		}
	}
	DotGet() {}

	public static ExprNode resolved(ExprNode parent, IClass begin, IType type, boolean isFinal, FieldNode... chain) {
		DotGet el = new DotGet();
		el.names = new SimpleList<>();
		el.parent = parent;
		el.begin = begin;
		el.chain = chain;
		el.type = type == null ? chain[chain.length-1].fieldType() : type;
		el.flags = (byte) (RESOLVED | (isFinal ? FINAL_FIELD : 0));
		return el;
	}

	Type toClassRef() {
		CharList sb = LocalContext.get().tmpSb; sb.clear();
		int i = 0;
		String part = names.get(0);
		while (true) {
			sb.append(part);
			if (++i == names.size() || (part = names.get(i)).equals(";[")) break;
			sb.append('/');
		}

		return new Type(sb.toString(), names.size()-i);
	}

	@Override
	public String toString() {
		CharList sb = new CharList();
		if (parent != null) sb.append(parent).append('.');
		return sb.append(TextUtil.join(names, ".")).toStringAndFree();
	}

	@NotNull
	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException { return resolveEx(ctx, null); }
	@Contract("_, null -> !null")
	final ExprNode resolveEx(LocalContext ctx, Consumer<IClass> classExprTarget) throws ResolveException {
		if ((flags&RESOLVED) != 0) return this;

		// 用于错误提示
		FieldResult inaccessibleThis = null;

		if (flags < 0) {
			// 首先我们要拿到第一个field
			// 这里按顺序处理下列情况

			String part = names.get(0);

			Variable varParent = ctx.tryVariable(part);
			check:
			if (varParent != null) {
				// 1. 变量
				parent = new LocalVariable(varParent);
				if (names.size() == 1) return parent;

				names.remove(0);
				flags = 0;
				bits >>>= 1;
			} else {
				// 2. 省略this的当前类字段（包括继承！）
				// * => 在错误处理中需要二次检查以生成更有帮助的错误信息（static）

				ComponentList fieldList = ctx.fieldListOrReport(ctx.file, part);
				if (fieldList != null) {
					inaccessibleThis = fieldList.findField(ctx, ctx.in_static ? ComponentList.IN_STATIC : 0);
					if (inaccessibleThis.error == null) {
						begin = ctx.file;
						parent = (inaccessibleThis.field.modifier&Opcodes.ACC_STATIC) != 0 ? null : ctx.ep.This();
						flags = 0;
						break check;
					}
				}

				LocalContext.Import result;
				// 3. 静态字段导入
				if ((result = ctx.tryImportField(part)) != null) {
					begin = result.owner;
					if (begin == null) return result.prev;
					names.set(0, result.method);
					parent = result.prev;
					flags = 0;
				}
			}

			// 4. 前缀包名.字段
		}

		String part = names.get(0);
		int i = 0;
		CharList sb = ctx.tmpSb; sb.clear();
		while (true) {
			sb.append(part);
			if (++i == names.size()) break;
			if ((part = names.get(i)).equals(";[")) {
				ctx.report(Kind.ERROR, "symbol.error.arrayBrOutsideClassRef");
				return NaE.RESOLVE_FAILED;
			}
			sb.append('/');
		}

		if (flags >= 0) { // parent不为null, 下面是处理importField
			IType fType;
			IClass symbol;

			if (parent == null) {
				fType = new Type(begin.name());
				symbol = begin;
			} else {
				fType = (parent = parent.resolve(ctx)).type();
				if (fType.isPrimitive()) {
					ctx.report(Kind.ERROR, "symbol.error.derefPrimitiveField", fType);
					return NaE.RESOLVE_FAILED;
				}

				symbol = ctx.getClassOrArray(fType);
				if (symbol == null) {
					ctx.report(Kind.ERROR, "symbol.error.noSuchClass", fType);
					return NaE.RESOLVE_FAILED;
				}
			}

			String error = ctx.resolveField(symbol, fType, sb);
			if (error != null) {
				ctx.report(Kind.ERROR, error);
				return NaE.RESOLVE_FAILED;
			}

			part = fType.owner();
		} else {
			// 4. 前缀包名.字段 ^
			// ^ => 我决定采用（已经设计好的）这种机制，虽然可能没必要
			String error = ctx.resolveDotGet(sb, classExprTarget != null);

			if (bits != 0) {
				if (classExprTarget != null) ctx.report(Kind.ERROR, "dotGet.opChain.isChild");

				int offset = ctx.get_frOffset();
				if (Long.numberOfTrailingZeros(bits) < offset) ctx.report(Kind.ERROR, "dotGet.opChain.inClassDecl");
				bits >>>= offset;
			}

			if (error != null) {
				if (error.isEmpty()) {
					assert classExprTarget != null;
					classExprTarget.accept(ctx.get_frBegin());
					return null;
				}

				if (inaccessibleThis != null) error = inaccessibleThis.error;
				ctx.report(Kind.ERROR, error);
				return NaE.RESOLVE_FAILED;
			}

			part = ctx.get_frChain().get(0).fieldType().owner();
		}

		begin = ctx.get_frBegin();
		chain = ctx.get_frChain().toArray(new FieldNode[ctx.get_frChain().size()]);
		type = ctx.get_frType();

		ctx.checkType(part);

		flags = RESOLVED;

		int length = chain.length;
		FieldNode last = chain[length-1];

		// length不是字段而是opcode
		if (last == GlobalContext.arrayLength()) {
			flags |= FINAL_FIELD|ARRAY_LENGTH;
			type = Type.std(Type.INT);
			length--;
		} else if (type == null) {
			// get_frType只处理泛型
			type = last.fieldType();
		}

		FieldNode fn;
		int i1 = 1;
		if (i1 < length) for (;;) {
			fn = chain[i1];
			if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
				ctx.report(Kind.SEVERE_WARNING, "symbol.warn.static_on_half", part, fn.name(), "symbol.field");
			}

			if (++i1 == length) break;
			part = fn.fieldType().owner();
		} else  {
			fn = chain[0];
			assert chain.length == 1;

			// 替换常量 如你所见只有直接访问(<class>.<field>)才会替换,如果中途使用了非静态字段会警告，👆
			if (!ctx.disableConstantValue) {
				// 大概也用不到泛型... 不过还是留着
				ExprNode node = ctx.getConstantValue(begin, fn, type);
				if (node != null) return node;
			}
		}

		if (isStaticField()) bits &= ~1;
		if (bits != 0) {
			flags |= FINAL_FIELD;
			if ((flags&ARRAY_LENGTH) != 0) ctx.report(Kind.ERROR, "dotGet.opChain.arrayLen");
		} else if ((fn.modifier&Opcodes.ACC_FINAL) != 0) flags |= FINAL_FIELD;

		// == is better
		//noinspection all
		if (part != null && part == ctx.file.name) {
			flags |= SELF_FIELD;
			// redirect check to LocalContext
			if (ctx.in_constructor) flags &= ~FINAL_FIELD;
		}
		return this;
	}

	@Override
	public boolean isKind(ExprKind kind) {return kind == ExprKind.ENUM_REFERENCE && isStaticField() && (chain[0].modifier&Opcodes.ACC_ENUM) != 0;}
	@Override
	public Object constVal() {return isKind(ExprKind.ENUM_REFERENCE) ? new AnnValEnum(begin.name(), chain[0].name()) : super.constVal();}

	@Override
	public IType type() {return type == null ? Asterisk.anyType : type;}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);

		int length = chain.length - (flags&ARRAY_LENGTH);
		write0(cw, length);
		if (length != chain.length) cw.one(Opcodes.ARRAYLENGTH);
	}

	@Override
	public boolean isFinal() { return (flags&FINAL_FIELD) != 0; }

	@Override
	public void preStore(MethodWriter cw) {
		if ((flags&SELF_FIELD) != 0) LocalContext.get().checkSelfField(chain[chain.length-1], true);
		write0(cw, chain.length-1);
	}

	@Override
	public void preLoadStore(MethodWriter cw) {
		if ((flags&SELF_FIELD) != 0) LocalContext.get().checkSelfField(chain[chain.length-1], false);
		preStore(cw);
		if (!isStaticField()) cw.one(Opcodes.DUP);
	}

	@Override
	public void postStore(MethodWriter cw) {
		FieldNode fn = chain[chain.length-1];
		String owner = chain.length == 1 ? begin.name() : chain[chain.length-2].fieldType().owner();
		cw.field((fn.modifier & Opcodes.ACC_STATIC) != 0 ? Opcodes.PUTSTATIC : Opcodes.PUTFIELD, owner, fn.name(), fn.rawDesc());
	}

	@Override
	public void copyValue(MethodWriter cw, boolean twoStack) {
		cw.one(isStaticField() ? twoStack ? Opcodes.DUP2 : Opcodes.DUP : twoStack ? Opcodes.DUP2_X1 : Opcodes.DUP_X1);
	}

	@Override
	public boolean canBeReordered() { return parent == null && isStaticField(); }
	private boolean isStaticField() { return chain.length == 1 && (chain[0].modifier & Opcodes.ACC_STATIC) != 0; }

	private void write0(MethodWriter cw, int length) {
		int i = 0;
		String owner = begin.name();
		FieldNode fn = chain[0];
		byte opcode;

		boolean hasParent = parent != null;
		if (hasParent) parent.write(cw, false);

		if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
			opcode = Opcodes.GETSTATIC;
			if (hasParent) cw.one(Opcodes.POP); // field is useless
		} else {
			opcode = Opcodes.GETFIELD;
		}

		if (i == length) return;

		Label ifNull = bits == 0 ? null : new Label();
		for (;;) {
			if ((bits&(1L << i)) != 0) {
				cw.one(Opcodes.DUP);
				cw.jump(Opcodes.IFNULL, ifNull);
			}

			// 应该不需要
			// 在构造器里没法拿到间接且未初始化的this，ASM除外，但这论外了
			/*LocalContext lc = LocalContext.get();
			if (owner.equals(lc.file.name))
				lc.checkSelfField(fn, false);*/
			cw.field(opcode, owner, fn.name(), fn.rawDesc());
			if (++i == length) break;

			owner = fn.fieldType().owner();
			fn = chain[i];

			if ((fn.modifier & Opcodes.ACC_STATIC) != 0) {
				opcode = Opcodes.GETSTATIC;
				cw.one(Opcodes.POP); // field is useless
			} else {
				opcode = Opcodes.GETFIELD;
			}
		}

		if (ifNull != null) {
			if (cw.noverify) {
				cw.label(ifNull);
			} else {
				Label tmp = new Label();
				cw.jump(tmp);
				cw.label(ifNull);
				cw.one(Opcodes.POP);
				cw.one(Opcodes.ACONST_NULL);
				cw.label(tmp);
			}
		}
	}

	public DotGet add(String name, int flag) {
		if (flag != 0) {
			if (names.size() > 64) throw new ResolveException("dotGet.opChain.tooLong");
			bits |= 1L << names.size();
		}
		names.add(name);
		return this;
	}

	public boolean maybeStringTemplate() {return parent == null && names.size() == 1 && bits == 0;}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		DotGet get = (DotGet) o;

		if (parent != null ? !parent.equals(get.parent) : get.parent != null) return false;
		return names.equals(get.names);
	}

	@Override
	public int hashCode() {
		int result = parent == null ? 42 : parent.hashCode();
		result = 31 * result + names.hashCode();
		result = 31 * result + (int) (bits ^ (bits >>> 32));
		return result;
	}
}