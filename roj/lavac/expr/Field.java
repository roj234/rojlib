package roj.lavac.expr;

import roj.asm.Opcodes;
import roj.asm.frame.MethodPoet;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.type.Type;
import roj.asm.type.TypeHelper;
import roj.config.word.NotStatementException;
import roj.lavac.parser.MethodPoetL;
import roj.mapper.util.Desc;

import javax.annotation.Nonnull;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2022/2/27 20:27
 */
public final class Field implements LoadExpression {
	Expression parent;
	Desc field;

	public Field(Expression parent, String name, int flag) {
		this.parent = parent;
		this.field = field;
	}

	@Nonnull
	@Override
	public Expression compress() {
		parent = parent.compress();
		assert !parent.isConstant();
		return this;
	}

	@Override
	public Type type() {
		return TypeHelper.parseField(field.param);
	}

	@Override
	public boolean isEqual(Expression left) {
		if (this == left) return true;
		if (!(left instanceof Field)) return false;
		Field field = (Field) left;
		return field.parent.isEqual(parent) && field.field.equals(this.field);
	}

	@Override
	public void write(MethodPoetL tree, boolean noRet) {
		if (noRet) throw new NotStatementException();

		parent.write(tree, false);
		tree.node(new FieldInsnNode(Opcodes.GETFIELD, field.owner, field.name, field.param));
	}

	@Override
	public String toString() {
		return String.valueOf(parent) + '.' + field;
	}

	@Override
	public void write2(MethodPoetL tree) {
		parent.write(tree, false);
	}

	public void write3(MethodPoet tree) {
		tree.node(new FieldInsnNode(Opcodes.PUTFIELD, field.owner, field.name, field.param));
	}

	@Override
	public byte loadType() {
		return DYNAMIC_FIELD;
	}
}
