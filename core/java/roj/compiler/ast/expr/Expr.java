package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import roj.asm.ClassDefinition;
import roj.asm.FieldNode;
import roj.asm.Opcodes;
import roj.asm.insn.Label;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.CompileContext;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.diagnostic.Kind;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.node.ConfigValue;
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
		var lc = CompileContext.get();
		if (lc != null) {
			wordStart = lc.lexer.prevIndex;
			wordEnd = lc.lexer.index;
		}
	}
	protected Expr(int nopos) {}

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
	public Expr resolve(CompileContext ctx) throws ResolveException { return this; }

	@Override public boolean isConstant() {return RawExpr.super.isConstant();}
	@Override public Object constVal() {return RawExpr.super.constVal();}

	public LeftValue asLeftValue(CompileContext ctx) {
		ctx.report(this, Kind.ERROR, "var.notAssignable", this);
		return null;
	}

	protected abstract void write1(MethodWriter cw, @NotNull TypeCast.Cast cast);

	public final void write(MethodWriter cw) {write(cw, TypeCast.Cast.IDENTITY);}
	public final void writeStmt(MethodWriter cw) {write(cw, NORET);}
	public void write(MethodWriter cw, @NotNull TypeCast.Cast cast) {write1(cw, cast);cast.write(cw);}
	public void writeShortCircuit(MethodWriter cw, @NotNull TypeCast.Cast cast,
								   boolean ifThen, @NotNull Label label) {
		write(cw, cast);
		cw.jump(ifThen ? Opcodes.IFNE/*true*/ : Opcodes.IFEQ/*false*/, label);
	}

	public static final TypeCast.Cast NORET = TypeCast.RESULT(0, -1);
	protected static TypeCast.Cast noRet(TypeCast.Cast n) {return n == NORET ? NORET : TypeCast.Cast.IDENTITY;}
	protected final void mustBeStatement(TypeCast.Cast noRet) { if (noRet == NORET) CompileContext.get().report(this, Kind.ERROR, "expr.skipReturnValue"); }

	public static Expr constant(IType type, Object value) {return new Literal(type, value);}
	public static Expr classRef(Type type) {return new Literal(new Generic("java/lang/Class", Collections.singletonList(type)), type);}
	public static Expr valueOf(String v) {return new Literal(Types.STRING_TYPE, v);}
	public static Expr valueOf(boolean v) {return new Literal(Type.BOOLEAN_TYPE, v);}
	public static Expr valueOf(int v) {return new Literal(Type.INT_TYPE, ConfigValue.valueOf(v));}
	public static Expr valueOf(ConfigValue v) {
		if (v.dataType() == 's') return valueOf(v.asString());
		return new Literal(Type.primitive(v.dataType()), v);
	}

	public static Expr fieldChain(Expr parent, ClassDefinition begin, IType type, boolean isFinal, FieldNode... chain) {return MemberAccess.fieldChain(parent, begin, type, isFinal, chain);}

	public static Expr packedBooleanArray(DynByteBuf data) {return new NewPackedArray(Type.BOOLEAN, data);}
	public static Expr packedByteArray(DynByteBuf data) {return new NewPackedArray(Type.BYTE, data);}
	public static Expr packedShortArray(DynByteBuf data) {return new NewPackedArray(Type.SHORT, data);}
	public static Expr packedCharArray(DynByteBuf data) {return new NewPackedArray(Type.CHAR, data);}
	public static Expr packedIntArray(DynByteBuf data) {return new NewPackedArray(Type.INT, data);}
	public static Expr packedLongArray(DynByteBuf data) {return new NewPackedArray(Type.LONG, data);}
	public static Expr packedFloatArray(DynByteBuf data) {return new NewPackedArray(Type.FLOAT, data);}
	public static Expr packedDoubleArray(DynByteBuf data) {return new NewPackedArray(Type.DOUBLE, data);}
}