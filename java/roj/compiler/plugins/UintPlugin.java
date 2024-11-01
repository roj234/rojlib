package roj.compiler.plugins;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.tree.ConstantData;
import roj.asm.tree.MethodNode;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.api.Evaluable;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Constant;
import roj.compiler.ast.expr.ExprNode;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.context.Library;
import roj.compiler.context.LocalContext;
import roj.compiler.plugin.ExprApi;
import roj.compiler.plugin.LavaApi;
import roj.compiler.plugin.LavaPlugin;
import roj.compiler.resolve.TypeCast;
import roj.concurrent.OperationDone;

import java.util.List;

import static roj.compiler.JavaLexer.*;

/**
 * @author Roj234
 * @since 2024/12/1 0001 3:33
 */
@LavaPlugin(name = "uint", desc = "为Lava语言提供两种无符号数据类型: uint32和uint64")
public final class UintPlugin extends Evaluable implements Library, ExprApi.ExprOp {
	private static ExprNode _u32(ExprNode node) {return node instanceof uiCast cast ? cast.is64 == 0 ? cast.left : cast : new uiCast(node, 1);}
	private static ExprNode _u64(ExprNode node) {return node instanceof uiCast cast ? cast.is64 == 0 ? cast.left : cast : new uiCast(node, 2);}
	private static ExprNode _i32(ExprNode node) {return node instanceof uiCast cast ? cast.left : new uiCast(node, 0);}
	private static ExprNode _i64(ExprNode node) {return node instanceof uiCast cast ? cast.left : new uiCast(node, 3);}
	private static final class uiCast extends ExprNode {
		private ExprNode left;
		private final int is64;
		public uiCast(ExprNode left, int is64) {this.left = left;this.is64 = is64;}

		@Override public String toString() {
			return switch (is64) {
				case 0 -> "(int32) ";
				case 1 -> "(uint32) ";
				case 2 -> "(uint64) ";
				case 3 -> "(int64) ";
				default -> throw OperationDone.NEVER;
			}+left;
		}
		@Override public IType type() {
			return switch (is64) {
				case 0 -> Type.std(Type.INT);
				case 1 -> new Type.DirtyHacker('I', "uint32");
				case 2 -> new Type.DirtyHacker('J', "uint64");
				case 3 -> Type.std(Type.LONG);
				default -> throw OperationDone.NEVER;
			};
		}
		@Override public ExprNode resolve(LocalContext ctx) {left = left.resolve(ctx);return this;}
		@Override public boolean isConstant() {return left.isConstant();}
		@Override public Object constVal() {return left.constVal();}
		@Override public void write(MethodWriter cw, boolean noRet) {
			mustBeStatement(noRet);
			IType type = left.type();
			if (type.getActualType() != 'L' && !type.isPrimitive()) type = Type.std(type.getActualType());
			left.write(cw, LocalContext.get().castTo(type, Type.std(is64 < 2 ? Type.INT : Type.LONG), TypeCast.E_DOWNCAST));
		}
		//@Override public void write(MethodWriter cw, TypeCast.Cast returnType) {left.write(cw, returnType);}
	}

	private static int u32Count(ExprApi.OperatorContext opctx) {return u32Count(opctx.leftType())+u32Count(opctx.rightType());}
	private static int u32Count(IType type) {
		if ("uint32".equals(type.owner())) return 1;
		int cap = TypeCast.getDataCap(type.getActualType());
		return cap >= 1 && cap <= 4 ? 0 : -1;
	}

	private static int u64Count(ExprApi.OperatorContext opctx) {return u64Count(opctx.leftType())+u64Count(opctx.rightType());}
	private static int u64Count(IType type) {
		if ("uint64".equals(type.owner())) return 1;
		int cap = TypeCast.getDataCap(type.getActualType());
		return cap >= 1 && cap <= 5 ? 0 : -1;
	}

	private final MethodNode[] exprRef = new MethodNode[6];
	private final Constant zero = Constant.valueOf(AnnVal.valueOf(0));

	private final ConstantData Uint32, Uint64;
	private final MethodNode u32ToString, u32FromString, u64ToString, u64FromString;
	public UintPlugin() {
		Uint32 = new ConstantData();
		Uint32.name("uint32");
		Uint32.parent(null);

		u32ToString = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "toUnsignedString", "(I)Ljava/lang/String;");
		u32FromString = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "parseUnsignedInt", "(Ljava/lang/String;)I");
		exprRef[0] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "divideUnsigned", "(II)I");
		exprRef[1] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "reminderUnsigned", "(II)I");
		exprRef[2] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "compareUnsigned", "(II)I");

		var m1 = new MethodNode(Opcodes.ACC_PUBLIC, "uint32", "toString", "()Ljava/lang/String;");
		m1.putAttr(this);
		Uint32.methods.add(m1);

		m1 = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "uint32", "parse", "(Ljava/lang/String;)Luint32;");
		m1.putAttr(this);
		Uint32.methods.add(m1);

		Uint64 = new ConstantData();
		Uint64.name("uint64");
		Uint64.parent(null);

		u64ToString = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "toUnsignedString", "(J)Ljava/lang/String;");
		u64FromString = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "parseUnsignedLong", "(Ljava/lang/String;)J");
		exprRef[3] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "divideUnsigned", "(JJ)J");
		exprRef[4] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "reminderUnsigned", "(JJ)J");
		exprRef[5] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "compareUnsigned", "(JJ)I");

		m1 = new MethodNode(Opcodes.ACC_PUBLIC, "uint64", "toString", "()Ljava/lang/String;");
		m1.putAttr(this);
		Uint64.methods.add(m1);

		m1 = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "uint64", "parse", "(Ljava/lang/String;)Luint32;");
		m1.putAttr(this);
		Uint64.methods.add(m1);

		//for (MethodNode node : exprRef) node.putAttr(this);
	}

	@Override public ConstantData get(CharSequence name) {
		if (name.equals("uint32")) {return Uint32;}
		if (name.equals("uint64")) {return Uint64;}
		return null;
	}
	@Override public String toString() {return "Plugin<uint>";}
	@Override public ExprNode eval(MethodNode owner, @Nullable ExprNode self, List<ExprNode> args, Invoke node) {
		boolean is32 = owner.owner.equals("uint32");
		if (owner.name().equals("parse")) {
			return is32
				? _u32(Invoke.staticMethod(u32FromString, self))
				: _u64(Invoke.staticMethod(u64FromString, self));
		} else {
			return Invoke.staticMethod(is32?u32ToString:u64ToString, _i32(self));
		}
	}

	public void pluginInit(LavaApi api) {
		api.addLibrary(this);

		var expr = api.getExprApi();
		expr.addOpHandler("(", this); // 窄化转型失败
		expr.addOpHandler(")", this); // 宽化转型失败 (目前仅有var做了这个check)
		expr.addOpHandler("==", this);
		expr.addOpHandler("!=", this);
		expr.addOpHandler(">=", this);
		expr.addOpHandler("<=", this);
		expr.addOpHandler(">", this);
		expr.addOpHandler("<", this);
		expr.addOpHandler("/", this);
		expr.addOpHandler("%", this);
		expr.addOpHandler("++", this);
		expr.addOpHandler("--", this);
		expr.addOpHandler("+", this);
		expr.addOpHandler("-", this);
		expr.addOpHandler("*", this);
		expr.addOpHandler("<<", this);
		expr.addOpHandler(">>", this);
		expr.addOpHandler("~", this);
		expr.addOpHandler("&", this);
		expr.addOpHandler("|", this);
		expr.addOpHandler("^", this);
		//rtApi.onOperator(">>>", math);
	}

	@Override
	public ExprNode test(LocalContext ctx, ExprApi.OperatorContext opctx, ExprNode left, Object right) {
		short sym = opctx.symbol();
		switch (sym) {
			case inc, dec, add, sub, mul, lsh, rsh, rev, and, or, xor -> {
				if (u32Count(opctx) > 0) {
					var node = ctx.ep.binary(sym == rsh ? rsh_unsigned : sym, _i32(left), _i32((ExprNode) right));
					return _u32(node);
				}
				if (u64Count(opctx) > 0) {
					var node = ctx.ep.binary(sym == rsh ? rsh_unsigned : sym, _i64(left), _i64((ExprNode) right));
					return _u64(node);
				}
			}
			case equ, neq, lss, leq, gtr, geq -> {
				if (u32Count(opctx) > 0) {
					left = _i32(left);
					var rNode = _i32((ExprNode) right);
					return ctx.ep.binary(sym, Invoke.staticMethod(exprRef[3], left, rNode), zero);
				}

				if (u64Count(opctx) > 0) {
					left = _i64(left);
					var rNode = _i64((ExprNode) right);
					return ctx.ep.binary(sym, Invoke.staticMethod(exprRef[5], left, rNode), zero);
				}
			}
			case div, rem -> {
				if (u32Count(opctx) > 0) {
					left = _i32(left);
					var rNode = _i32((ExprNode) right);
					return _u32(Invoke.staticMethod(exprRef[sym == rem ? 1 : 0], left, rNode));
				}

				if (u64Count(opctx) > 0) {
					left = _i64(left);
					var rNode = _i64((ExprNode) right);
					return _u64(Invoke.staticMethod(exprRef[sym == rem ? 4 : 3], left, rNode));
				}
			}
			case lParen, rParen -> {
				var owner = opctx.rightType().owner();
				if ("uint32".equals(owner)) return u32Count(opctx.leftType()) < 0 ? null : _u32(left);
				if ("uint64".equals(owner)) return u64Count(opctx.leftType()) < 0 ? null : _u64(left);
				if (sym == lParen) {
					owner = opctx.leftType().owner();
					if ("uint32".equals(owner)) return u32Count(opctx.rightType()) < 0 ? null : _i32(left);
					if ("uint64".equals(owner)) return u64Count(opctx.rightType()) < 0 ? null : _i64(left);
				}
				return null;
			}
		}
		return null;
	}
}
