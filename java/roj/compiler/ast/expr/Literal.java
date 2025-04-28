package roj.compiler.ast.expr;

import org.jetbrains.annotations.Nullable;
import roj.asm.Opcodes;
import roj.asm.cp.CstClass;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.compiler.asm.MethodWriter;
import roj.compiler.context.LocalContext;
import roj.compiler.resolve.ResolveException;
import roj.compiler.resolve.TypeCast;
import roj.config.Tokenizer;
import roj.config.data.CEntry;

import java.util.Objects;

/**
 * AST - 常量
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Literal extends Expr {
	private static final Object CLASSREF = Literal.class;
	// 给CompileContext的getConstantValue省几个对象
	private boolean isClassRef() { return c == CLASSREF || c instanceof CstClass; }

	private final IType type;
	private Object c;

	Literal(IType type, Object c) {
		this.type = type;
		this.c = c;
	}

	@Override public String toString() { return c instanceof String ? '"'+ Tokenizer.addSlashes(c.toString())+'"' : isClassRef() ? type+".class" : String.valueOf(c); }
	@Override public boolean hasFeature(Feature feature) {return feature == Feature.IMMEDIATE_CONSTANT || (feature == Feature.LDC_CLASS && isClassRef());}
	@Override
	public IType minType() {
		if (c instanceof CEntry x && TypeCast.getDataCap(type.getActualType()) <= 4) {
			if (x.mayCastTo(roj.config.data.Type.Int1)) {
				return Type.primitive(Type.BYTE);
			}
			if (x.mayCastTo(roj.config.data.Type.Int2)) {
				return Type.primitive(Type.SHORT);
			}
			if (x.mayCastTo(roj.config.data.Type.CHAR)) {
				return Type.primitive(Type.CHAR);
			}
			if (x.mayCastTo(roj.config.data.Type.INTEGER)) {
				return Type.primitive(Type.INT);
			}
		}
		return type;
	}
	@Override public IType type() { return type; }
	@Override public boolean isConstant() { return !isClassRef(); }
	@Override public Object constVal() { return c; }

	@Override
	public Expr resolve(LocalContext ctx) throws ResolveException {
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
		else if (c == null) cw.insn(Opcodes.ACONST_NULL);
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
		if (!(o instanceof Expr r)) return false;

		return r.isConstant() && type.equals(r.type()) && (c == null ? r.constVal() == null : c.equals(r.constVal()));
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + (c != null ? c.hashCode() : 0);
		return result;
	}
}