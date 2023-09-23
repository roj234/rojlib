package roj.lavac.expr;

import roj.asm.type.IType;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodWriterL;

/**
 * 强制类型转换
 *
 * @author Roj234
 * @since 2022/2/24 19:48
 */
public class Cast extends UnaryPre {
	IType type;

	public Cast(IType type) {
		super((short) 0);
		this.type = type;
	}

	@Override
	public void write(MethodWriterL cw, boolean noRet) throws NotStatementException {
		right.write(cw, false);
		// todo
	}

	@Override
	public IType type() { return type; }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		Cast cast = (Cast) o;

		if (!type.equals(cast.type)) return false;
		return right.equals(cast.right);
	}

	@Override
	public String toString() { return "("+type+") "+right; }

	@Override
	public String setRight(Expression right) {
		this.right = right;
		return null;
	}
}
