package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.tree.anno.AnnVal;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.JavaLexer;
import roj.compiler.asm.Asterisk;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.CompileContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.concurrent.OperationDone;
import roj.config.word.ITokenizer;
import roj.config.word.NotStatementException;
import roj.config.word.Word;

import java.util.Collections;

/**
 * 操作符 - 常量
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Constant implements ExprNode {
	private static final Object CLASSREF = Constant.class;
	// 给CompileContext的getConstantValue省几个对象
	private boolean isClassRef() { return c == CLASSREF || c instanceof CstClass; }

	private final IType type;
	private final Object c;

	public Constant(IType type, Object c) {
		this.type = type;
		this.c = c;
	}

	@Override
	public String toString() { return c instanceof String ? '"'+ITokenizer.addSlashes(c.toString())+'"' : isClassRef() ? type+".class" : String.valueOf(c); }

	@Override
	public IType type() { return type; }
	@Override
	public boolean isConstant() { return !isClassRef(); }
	@Override
	public Object constVal() { return c; }

	public static final Type STRING = new Type("java/lang/String");
	public static ExprNode classRef(Type type) { return new Constant(new Generic("java/lang/Class", Collections.singletonList(type)), type); }
	public static Constant valueOf(boolean v) { return new Constant(Type.std(Type.BOOLEAN), v); }
	public static Constant valueOf(String v) { return new Constant(STRING, v); }
	public static Constant valueOf(Word word) {
		switch (word.type()) {
			case JavaLexer.NULL: return new Constant(Asterisk.anyType, null);
			case Word.CHARACTER: return new Constant(Type.std(Type.CHAR), word.val().charAt(0));
			case Word.STRING: return new Constant(STRING, word.val());
			case Word.LONG: return new Constant(Type.std(Type.LONG), AnnVal.valueOf(word.asLong()));
			case Word.DOUBLE: return new Constant(Type.std(Type.DOUBLE), AnnVal.valueOf(word.asDouble()));
			case Word.FLOAT: return new Constant(Type.std(Type.FLOAT), AnnVal.valueOf((float)word.asDouble()));
			case Word.INTEGER: return new Constant(Type.std(Type.INT), AnnVal.valueOf(word.asInt()));
			case JavaLexer.TRUE: return new Constant(Type.std(Type.BOOLEAN), true);
			case JavaLexer.FALSE: return new Constant(Type.std(Type.BOOLEAN), false);
			default: throw OperationDone.NEVER;
		}
	}

	@Override
	public ExprNode resolve(CompileContext ctx) throws ResolveException {
		if (c instanceof IType t) ctx.resolveType(t);
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) throws NotStatementException {
		mustBeStatement(noRet);
		if (c instanceof IType t) cw.ldc(new CstClass(t.rawType().ownerForCstClass()));
		else if (c instanceof CstClass cx) cw.ldc(cx);
		else if (c == null) cw.one(Opcodes.ACONST_NULL);
		else if ("java/lang/String".equals(type.owner())) cw.ldc(c.toString());
		else {
			assert type.isPrimitive();
			writePrimitive(cw, TypeCast.getDataCap(type.rawType().type));
		}
	}
	@Override
	public void writeDyn(MethodWriter cw, @Nullable TypeCast.Cast cast) {
		if (cast == null) {
			write(cw, false);
		} else {
			if (cast.type == TypeCast.E_NUMBER_DOWNCAST || cast.type == TypeCast.NUMBER_UPCAST) {
				String name = Opcodes.showOpcode(cast.getOp1());
				assert name.length() == 3;
				writePrimitive(cw, switch (name.charAt(2)) {
					default  -> 4;
					case 'L' -> 5;
					case 'F' -> 6;
					case 'D' -> 7;
				});
				return;
			}

			write(cw, false);
			cast.write(cw);
		}
	}

	private void writePrimitive(MethodWriter cw, int cap) {
		if ((cap & 7) != 0) switch (cap) {
			case 5: cw.ldc(((AnnVal) c).asLong()); break;
			case 6: cw.ldc(((AnnVal) c).asFloat()); break;
			case 7: cw.ldc(((AnnVal) c).asDouble()); break;
			default: cw.ldc(((AnnVal) c).asInt()); break;
		}
		else cw.ldc(c == Boolean.TRUE ? 1 : 0);
	}

	@Override
	public boolean equalTo(Object o) {
		if (this == o) return true;
		if (!(o instanceof ExprNode)) return false;

		ExprNode r = (ExprNode) o;
		return r.isConstant() && type.equals(r.type()) && (c == null ? r.constVal() == null : c.equals(r.constVal()));
	}
}