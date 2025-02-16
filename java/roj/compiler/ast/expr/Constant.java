package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.type.Generic;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.api.Types;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.Tokenizer;
import roj.config.data.CEntry;

import java.util.Collections;
import java.util.Objects;

/**
 * 操作符 - 常量
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Constant extends ExprNode {
	private static final Object CLASSREF = Constant.class;
	// 给CompileContext的getConstantValue省几个对象
	private boolean isClassRef() { return c == CLASSREF || c instanceof CstClass; }

	private final IType type;
	private Object c;

	public Constant(IType type, Object c) {
		this.type = type;
		this.c = c;
	}

	@Override public String toString() { return c instanceof String ? '"'+ Tokenizer.addSlashes(c.toString())+'"' : isClassRef() ? type+".class" : String.valueOf(c); }
	@Override public boolean hasFeature(ExprFeat kind) {return kind == ExprFeat.IMMEDIATE_CONSTANT || (kind == ExprFeat.LDC_CLASS && isClassRef());}
	@Override public IType type() { return type; }
	@Override public boolean isConstant() { return !isClassRef(); }
	@Override public Object constVal() { return c; }

	public static Constant classRef(Type type) {return new Constant(new Generic("java/lang/Class", Collections.singletonList(type)), type);}
	public static Constant valueOf(String v) {return new Constant(Types.STRING_TYPE, v);}
	public static Constant valueOf(boolean v) {return new Constant(Type.primitive(Type.BOOLEAN), v);}
	public static Constant valueOf(int v) {return new Constant(Type.primitive(Type.INT), CEntry.valueOf(v));}
	public static Constant valueOf(CEntry v) {
		return switch (v.dataType()) {
			case 's' -> valueOf(v.asString());
			case 'I', 'B', 'C', 'S', 'J', 'F', 'D' -> new Constant(Type.primitive(v.dataType()), v);
			default -> throw new IllegalArgumentException("暂时不支持这些类型："+v);
		};
	}

	@Override
	public ExprNode resolve(LocalContext ctx) throws ResolveException {
		if (c instanceof IType t) c = ctx.resolveType(t);
		return this;
	}

	@Override
	public void write(MethodWriter cw, boolean noRet) {
		mustBeStatement(noRet);
		if (c instanceof IType t) {
			if (t.isPrimitive()) {
				cw.field(Opcodes.GETSTATIC, Objects.requireNonNull(TypeCast.getWrapper(t)).owner, "TYPE", "Ljava/lang/Class;");
			} else {
				cw.ldc(new CstClass(t.rawType().getActualClass()));
			}
		} else if (c instanceof CstClass cx) cw.ldc(cx);
		else if (c == null) cw.one(Opcodes.ACONST_NULL);
		else if ("java/lang/String".equals(type.owner())) cw.ldc(c.toString());
		else {
			assert type.isPrimitive();
			writePrimitive(cw, TypeCast.getDataCap(type.rawType().type));
		}
	}
	@Override
	public void write(MethodWriter cw, @Nullable TypeCast.Cast returnType) {
		if (returnType == null) {
			write(cw, false);
		} else {
			switch (returnType.type) {
				case TypeCast.E_NUMBER_DOWNCAST, TypeCast.NUMBER_UPCAST, TypeCast.BOXING -> {
					String name = Opcodes.showOpcode(returnType.getOp1());
					assert name.length() == 3;
					writePrimitive(cw, switch (name.charAt(2)) {
						default -> 4;
						case 'L' -> 5;
						case 'F' -> 6;
						case 'D' -> 7;
					});
					if (returnType.type == TypeCast.BOXING)
						returnType.writeBox(cw);
				}
				default -> {
					write(cw, false);
					returnType.write(cw);
				}
			}
		}
	}

	private void writePrimitive(MethodWriter cw, int cap) {
		if ((cap & 7) != 0) switch (cap) {
			case 5: cw.ldc(((CEntry) c).asLong()); break;
			case 6: cw.ldc(((CEntry) c).asFloat()); break;
			case 7: cw.ldc(((CEntry) c).asDouble()); break;
			default: cw.ldc(((CEntry) c).asInt()); break;
		}
		else cw.ldc(c == Boolean.TRUE ? 1 : 0);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof ExprNode r)) return false;

		return r.isConstant() && type.equals(r.type()) && (c == null ? r.constVal() == null : c.equals(r.constVal()));
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + (c != null ? c.hashCode() : 0);
		return result;
	}
}