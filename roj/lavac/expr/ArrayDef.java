package roj.lavac.expr;

import roj.asm.Opcodes;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.asm.util.InsnHelper;
import roj.compiler.ast.expr.ExprNode;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodWriterL;
import roj.text.CharList;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * 操作符 - 定义数组（有内容）
 *
 * @author Roj233
 * @since 2022/2/27 19:43
 */
final class ArrayDef implements ExprNode {
	private final IType type;
	private final List<ExprNode> expr;
	private final boolean size;

	ArrayDef(IType type, List<ExprNode> args, boolean size) {
		this.type = type;
		this.expr = args;
		this.size = size;
	}

	@Override
	public IType type() { return type; }

	@Nonnull
	@Override
	public ExprNode resolve() {
		for (int i = 0; i < expr.size(); i++) {
			if (!expr.get(i).isConstant()) return this;
		}
		Object[] arrayEntry = new Object[expr.size()];
		for (int i = 0; i < expr.size(); i++) {
			arrayEntry[i] = expr.get(i).constVal();
		}
		return new Constant(type, arrayEntry);
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) {
		if (noRet) throw new NotStatementException();

		if (size) {
			for (int i = 0; i < expr.size(); i++) {
				expr.get(i).write(cw, false);
			}

			getType(cw, expr.size());
		} else {
			cw.ldc(expr.size());

			Type at = getType(cw, 1);

			byte storeType = (byte) Opcodes.opcodeByName().getInt(at.nativeName()+"ASTORE");
			for (int i = 0; i < expr.size(); i++) {
				cw.one(Opcodes.LDC);
				cw.ldc(i);
				expr.get(i).write(cw, false);
				cw.one(storeType);
			}
		}
	}

	private Type getType(MethodWriterL cw, int dimension) {
		Type at = type.rawType();
		if (at.array() == 1) {
			if (at.isPrimitive()) cw.newArray(InsnHelper.ToPrimitiveArrayId(at.type));
			else cw.clazz(Opcodes.ANEWARRAY, at);
		} else {
			cw.multiArray(at.getActualClass(), dimension);
		}
		return at;
	}

	@Override
	public String toString() {
		CharList sb = new CharList().append("new ");
		if (size) {
			IType clone = type.clone();
			clone.setArrayDim(0);
			sb.append(clone);
			for (int i = 0; i < expr.size(); i++) {
				sb.append('[').append(expr.get(i).toString()).append(']');
			}
			return sb.toStringAndFree();
		} else {
			sb.append(type).append(" {");
			int i = 0;
			while (true) {
				sb.append(expr.get(i++).toString());
				if (i == expr.size()) break;
				sb.append(',');
			}
			return sb.append("}").toStringAndFree();
		}
	}

	@Override
	public boolean equalTo(Object left) {
		if (this == left) return true;
		if (left == null || getClass() != left.getClass()) return false;
		return expr.equals(((ArrayDef) left).expr);
	}

	@Override
	public int hashCode() {
		int result = type.hashCode();
		result = 31 * result + expr.hashCode();
		result = 31 * result + (size ? 1 : 0);
		return result;
	}
}