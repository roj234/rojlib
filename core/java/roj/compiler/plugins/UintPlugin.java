package roj.compiler.plugins;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.ClassNode;
import roj.asm.MethodNode;
import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.api.Compiler;
import roj.compiler.api.CompilerPlugin;
import roj.compiler.api.InvokeHook;
import roj.compiler.asm.MethodWriter;
import roj.compiler.ast.expr.Expr;
import roj.compiler.ast.expr.Invoke;
import roj.compiler.library.Library;
import roj.compiler.resolve.TypeCast;
import roj.util.OperationDone;

import java.util.List;

import static roj.compiler.JavaTokenizer.*;

/**
 * @author Roj234
 * @since 2024/12/1 3:33
 */
@CompilerPlugin(name = "uint", desc = """
		PseudoType测试插件

		为Lava语言提供两种无符号数据类型: uint32和uint64""")
public final class UintPlugin extends InvokeHook implements Library, Compiler.ExprOp {
	private static Expr _u32(Expr node) {return node instanceof uiCast cast ? cast.is64 == 0 ? cast.left : cast : new uiCast(node, 1);}
	private static Expr _u64(Expr node) {return node instanceof uiCast cast ? cast.is64 == 0 ? cast.left : cast : new uiCast(node, 2);}
	private static Expr _i32(Expr node) {return node instanceof uiCast cast ? cast.left : new uiCast(node, 0);}
	private static Expr _i64(Expr node) {return node instanceof uiCast cast ? cast.left : new uiCast(node, 3);}
	private static final class uiCast extends Expr {
		private Expr left;
		private final int is64;
		public uiCast(Expr left, int is64) {this.left = left;this.is64 = is64;}

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
				case 0 -> Type.primitive(Type.INT);
				case 1 -> new Type.ADT('I', "uint32");
				case 2 -> new Type.ADT('J', "uint64");
				case 3 -> Type.primitive(Type.LONG);
				default -> throw OperationDone.NEVER;
			};
		}
		@Override public Expr resolve(CompileContext ctx) {left = left.resolve(ctx);return this;}
		@Override public boolean isConstant() {return left.isConstant();}
		@Override public Object constVal() {return left.constVal();}
		@Override protected void write1(MethodWriter cw, @NotNull TypeCast.Cast cast) {
			mustBeStatement(cast);
			IType type = left.type();
			if (type.getActualType() != 'L' && !type.isPrimitive()) type = Type.primitive(type.getActualType());
			left.write(cw, CompileContext.get().castTo(type, Type.primitive(is64 < 2 ? Type.INT : Type.LONG), TypeCast.E_DOWNCAST));
		}
	}

	private static int u32Count(Compiler.OperatorContext opctx) {return u32Count(opctx.leftType())+u32Count(opctx.rightType());}
	private static int u32Count(IType type) {
		if (type == null) return -1;
		if ("uint32".equals(type.owner())) return 1;
		int cap = TypeCast.getDataCap(type.getActualType());
		return cap >= 1 && cap <= 4 ? 0 : -1;
	}

	private static int u64Count(Compiler.OperatorContext opctx) {return u64Count(opctx.leftType())+u64Count(opctx.rightType());}
	private static int u64Count(IType type) {
		if (type == null) return -1;
		if ("uint64".equals(type.owner())) return 1;
		int cap = TypeCast.getDataCap(type.getActualType());
		return cap >= 1 && cap <= 5 ? 0 : -1;
	}

	private final MethodNode[] exprRef = new MethodNode[6];
	private final Expr zero = Expr.valueOf(0);

	private final ClassNode Uint32, Uint64;
	private final MethodNode u32ToString, u32FromString, u64ToString, u64FromString;
	public UintPlugin() {
		Uint32 = new ClassNode();
		Uint32.name("uint32");
		Uint32.parent(null);

		u32ToString = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "toUnsignedString", "(I)Ljava/lang/String;");
		u32FromString = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "parseUnsignedInt", "(Ljava/lang/String;)I");
		exprRef[0] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "divideUnsigned", "(II)I");
		exprRef[1] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "reminderUnsigned", "(II)I");
		exprRef[2] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Integer", "compareUnsigned", "(II)I");

		var m1 = new MethodNode(Opcodes.ACC_PUBLIC, "uint32", "toString", "()Ljava/lang/String;");
		m1.addAttribute(this);
		Uint32.methods.add(m1);

		m1 = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "uint32", "parse", "(Ljava/lang/String;)Luint32;");
		m1.addAttribute(this);
		Uint32.methods.add(m1);

		Uint64 = new ClassNode();
		Uint64.name("uint64");
		Uint64.parent(null);

		u64ToString = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "toUnsignedString", "(J)Ljava/lang/String;");
		u64FromString = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "parseUnsignedLong", "(Ljava/lang/String;)J");
		exprRef[3] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "divideUnsigned", "(JJ)J");
		exprRef[4] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "reminderUnsigned", "(JJ)J");
		exprRef[5] = new MethodNode(Opcodes.ACC_STATIC, "java/lang/Long", "compareUnsigned", "(JJ)I");

		m1 = new MethodNode(Opcodes.ACC_PUBLIC, "uint64", "toString", "()Ljava/lang/String;");
		m1.addAttribute(this);
		Uint64.methods.add(m1);

		m1 = new MethodNode(Opcodes.ACC_PUBLIC|Opcodes.ACC_STATIC, "uint64", "parse", "(Ljava/lang/String;)Luint32;");
		m1.addAttribute(this);
		Uint64.methods.add(m1);

		//for (MethodNode node : exprRef) node.putAttr(this);
	}

	@Override public ClassNode get(CharSequence name) {
		if (name.equals("uint32")) {return Uint32;}
		if (name.equals("uint64")) {return Uint64;}
		return null;
	}
	@Override public String toString() {return "Plugin<uint>";}
	@Override public Expr eval(MethodNode owner, @Nullable Expr that, List<Expr> args, Invoke node) {
		boolean is32 = owner.owner().equals("uint32");
		if (owner.name().equals("parse")) {
			return is32
				? _u32(Invoke.staticMethod(u32FromString, that))
				: _u64(Invoke.staticMethod(u64FromString, that));
		} else {
			return Invoke.staticMethod(is32?u32ToString:u64ToString, _i32(that));
		}
	}

	public void pluginInit(Compiler api) {
		api.addLibrary(this);

		api.addOpHandler("(", this); // 窄化转型失败 (cast)
		api.addOpHandler("=", this); // 宽化转型失败 (目前仅有var和assign做了这个check)
		api.addOpHandler("==", this);
		api.addOpHandler("!=", this);
		api.addOpHandler(">=", this);
		api.addOpHandler("<=", this);
		api.addOpHandler(">", this);
		api.addOpHandler("<", this);
		api.addOpHandler("/", this);
		api.addOpHandler("%", this);
		api.addOpHandler("++", this);
		api.addOpHandler("--", this);
		api.addOpHandler("+", this);
		api.addOpHandler("-", this);
		api.addOpHandler("*", this);
		api.addOpHandler("<<", this);
		api.addOpHandler(">>", this);
		api.addOpHandler("~", this);
		api.addOpHandler("&", this);
		api.addOpHandler("|", this);
		api.addOpHandler("^", this);
		//api.onOperator(">>>", math);
	}

	@Override
	public Expr test(CompileContext ctx, Compiler.OperatorContext opctx, Expr left, Object right) {
		short sym = opctx.symbol();
		switch (sym) {
			case inc, dec, add, sub, mul, shl, shr, inv, and, or, xor -> {
				if (u32Count(opctx) > 0) {
					var node = ctx.ep.binaryOp(sym == shr ? ushr : sym, _i32(left), _i32((Expr) right));
					return _u32(node);
				}
				if (u64Count(opctx) > 0) {
					var node = ctx.ep.binaryOp(sym == shr ? ushr : sym, _i64(left), _i64((Expr) right));
					return _u64(node);
				}
			}
			case equ, neq, lss, leq, gtr, geq -> {
				if (u32Count(opctx) > 0) {
					left = _i32(left);
					var rNode = _i32((Expr) right);
					return ctx.ep.binaryOp(sym, Invoke.staticMethod(exprRef[3], left, rNode), zero);
				}

				if (u64Count(opctx) > 0) {
					left = _i64(left);
					var rNode = _i64((Expr) right);
					return ctx.ep.binaryOp(sym, Invoke.staticMethod(exprRef[5], left, rNode), zero);
				}
			}
			case div, rem -> {
				if (u32Count(opctx) > 0) {
					left = _i32(left);
					var rNode = _i32((Expr) right);
					return _u32(Invoke.staticMethod(exprRef[sym == rem ? 1 : 0], left, rNode));
				}

				if (u64Count(opctx) > 0) {
					left = _i64(left);
					var rNode = _i64((Expr) right);
					return _u64(Invoke.staticMethod(exprRef[sym == rem ? 4 : 3], left, rNode));
				}
			}
			case lParen, assign -> {
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
