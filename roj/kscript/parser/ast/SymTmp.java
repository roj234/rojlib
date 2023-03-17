package roj.kscript.parser.ast;

import roj.collect.MyHashSet;
import roj.kscript.asm.KS_ASM;
import roj.kscript.parser.JSLexer;

/**
 * 临时操作符1 - 保存运算符
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class SymTmp implements Expression {
	public short operator;

	static final MyHashSet<SymTmp> finder = new MyHashSet<>();
	static SymTmp checker = new SymTmp(0);

	private SymTmp(int operator) {
		this.operator = (short) operator;
	}

	public static SymTmp retain(short op) {
		checker.operator = op;
		SymTmp tmp = finder.intern(checker);
		if (tmp == checker) {
			checker = new SymTmp(0);
		}
		return tmp;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SymTmp tmp = (SymTmp) o;
		return operator == tmp.operator;
	}

	@Override
	public int hashCode() {
		return operator;
	}

	@Override
	public void write(KS_ASM tree, boolean noRet) {
		throw new UnsupportedOperationException();
	}

	@Override
	public byte type() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isEqual(Expression left) {
		return false;
	}

	@Override
	public String toString() {
		return "~{" + JSLexer.byId(operator) + '}';
	}
}