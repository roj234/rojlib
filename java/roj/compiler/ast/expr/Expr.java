package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.FieldNode;
import roj.asm.IClass;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.data.CEntry;
import roj.util.DynByteBuf;

import java.util.Collections;

/**
 * @author Roj233
 * @since 2022/2/24 19:16
 */
public abstract class Expr implements RawExpr {
	public int wordStart, wordEnd;
	public int getWordStart() {return wordStart;}
	public int getWordEnd() {return wordEnd;}

	protected Expr() {
		var lc = LocalContext.get();
		if (lc != null) {
			wordStart = lc.lexer.current().pos();
			wordEnd = lc.lexer.index;
		}
	}
	protected Expr(int _noUpdate) {}

	public abstract String toString();

	public enum Feature {
		// this() or super()
		INVOKE_CONSTRUCTOR,
		// constant literal
		IMMEDIATE_CONSTANT,
		// literal xxx.class
		LDC_CLASS,
		// special kind for block passing parser
		CONSTANT_WRITABLE,
		ENUM_REFERENCE,
		//尾调用
		TAILREC,
		STATIC_BEGIN
	}
	public boolean hasFeature(Feature feature) {return false;}
	public abstract IType type();
	/**
	 * 常量允许到达的最小数量级
	 */
	public IType minType() {return type();}
	public Expr resolve(LocalContext ctx) throws ResolveException { return this; }

	@Override public boolean isConstant() {return RawExpr.super.isConstant();}
	@Override public Object constVal() {return RawExpr.super.constVal();}

	public LeftValue asLeftValue(LocalContext ctx) {
		ctx.report(this, Kind.ERROR, "var.notAssignable", this);
		return null;
	}

	public final void write(MethodWriter cw) {write(cw, false);}
	public abstract void write(MethodWriter cw, boolean noRet);
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		write(cw, false);
		if (returnType != null) returnType.write(cw);
	}
	public void writeShortCircuit(MethodWriter cw, @Nullable TypeCast.Cast cast,
								   boolean ifThen, @NotNull Label label) {
		write(cw, cast);
		cw.jump(ifThen ? Opcodes.IFNE/*true*/ : Opcodes.IFEQ/*false*/, label);
	}

	protected final void mustBeStatement(boolean noRet) { if (noRet) LocalContext.get().report(this, Kind.ERROR, "expr.skipReturnValue"); }


	public static Expr constant(IType type, Object value) {return new Literal(type, value);}
	public static Expr classRef(Type type) {return new Literal(new Generic("java/lang/Class", Collections.singletonList(type)), type);}
	public static Expr valueOf(String v) {return new Literal(Types.STRING_TYPE, v);}
	public static Expr valueOf(boolean v) {return new Literal(Type.primitive(Type.BOOLEAN), v);}
	public static Expr valueOf(int v) {return new Literal(Type.primitive(Type.INT), CEntry.valueOf(v));}
	public static Expr valueOf(CEntry v) {
		return switch (v.dataType()) {
			case 's' -> valueOf(v.asString());
			case 'I', 'B', 'C', 'S', 'J', 'F', 'D' -> new Literal(Type.primitive(v.dataType()), v);
			default -> throw new IllegalArgumentException("暂时不支持这些类型："+v);
		};
	}

	public static Expr fieldChain(Expr parent, IClass begin, IType type, boolean isFinal, FieldNode... chain) {return MemberAccess.fieldChain(parent, begin, type, isFinal, chain);}

	public static Expr packedBooleanArray(DynByteBuf data) {return new NewPackedArray(Type.BOOLEAN, data);}
	public static Expr packedByteArray(DynByteBuf data) {return new NewPackedArray(Type.BYTE, data);}
	public static Expr packedShortArray(DynByteBuf data) {return new NewPackedArray(Type.SHORT, data);}
	public static Expr packedCharArray(DynByteBuf data) {return new NewPackedArray(Type.CHAR, data);}
	public static Expr packedIntArray(DynByteBuf data) {return new NewPackedArray(Type.INT, data);}
	public static Expr packedLongArray(DynByteBuf data) {return new NewPackedArray(Type.LONG, data);}
	public static Expr packedFloatArray(DynByteBuf data) {return new NewPackedArray(Type.FLOAT, data);}
	public static Expr packedDoubleArray(DynByteBuf data) {return new NewPackedArray(Type.DOUBLE, data);}
}