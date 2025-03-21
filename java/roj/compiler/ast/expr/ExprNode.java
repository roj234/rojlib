package roj.compiler.ast.expr;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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
public abstract class ExprNode implements UnresolvedExprNode {
	public int wordStart, wordEnd;
	public int getWordStart() {return wordStart;}
	public int getWordEnd() {return wordEnd;}

	protected ExprNode() {
		var lc = LocalContext.get();
		if (lc != null) {
			wordStart = lc.lexer.current().pos();
			wordEnd = lc.lexer.index;
		}
	}
	protected ExprNode(int _noUpdate) {}

	public abstract String toString();

	public enum ExprFeat {
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
	public boolean hasFeature(ExprFeat kind) {return false;}
	public abstract IType type();
	/**
	 * 常量允许到达的最小数量级
	 */
	public IType minType() {return type();}
	public ExprNode resolve(LocalContext ctx) throws ResolveException { return this; }

	@Override public boolean isConstant() {return UnresolvedExprNode.super.isConstant();}
	@Override public Object constVal() {return UnresolvedExprNode.super.constVal();}

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


	public static ExprNode constant(IType type, Object value) {return new Constant(type, value);}
	public static ExprNode classRef(Type type) {return new Constant(new Generic("java/lang/Class", Collections.singletonList(type)), type);}
	public static ExprNode valueOf(String v) {return new Constant(Types.STRING_TYPE, v);}
	public static ExprNode valueOf(boolean v) {return new Constant(Type.primitive(Type.BOOLEAN), v);}
	public static ExprNode valueOf(int v) {return new Constant(Type.primitive(Type.INT), CEntry.valueOf(v));}
	public static ExprNode valueOf(CEntry v) {
		return switch (v.dataType()) {
			case 's' -> valueOf(v.asString());
			case 'I', 'B', 'C', 'S', 'J', 'F', 'D' -> new Constant(Type.primitive(v.dataType()), v);
			default -> throw new IllegalArgumentException("暂时不支持这些类型："+v);
		};
	}

	public static ExprNode packedBooleanArray(DynByteBuf data) {return new PrimitiveArray(Type.BOOLEAN, data);}
	public static ExprNode packedByteArray(DynByteBuf data) {return new PrimitiveArray(Type.BYTE, data);}
	public static ExprNode packedShortArray(DynByteBuf data) {return new PrimitiveArray(Type.SHORT, data);}
	public static ExprNode packedCharArray(DynByteBuf data) {return new PrimitiveArray(Type.CHAR, data);}
	public static ExprNode packedIntArray(DynByteBuf data) {return new PrimitiveArray(Type.INT, data);}
	public static ExprNode packedLongArray(DynByteBuf data) {return new PrimitiveArray(Type.LONG, data);}
	public static ExprNode packedFloatArray(DynByteBuf data) {return new PrimitiveArray(Type.FLOAT, data);}
	public static ExprNode packedDoubleArray(DynByteBuf data) {return new PrimitiveArray(Type.DOUBLE, data);}
}