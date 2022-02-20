package roj.lavac.expr;

import roj.asm.Opcodes;
import roj.asm.tree.insn.FieldInsnNode;
import roj.asm.type.ParamHelper;
import roj.asm.type.Type;
import roj.config.word.NotStatementException;
import roj.lavac.parser.ParseContext;
import roj.mapper.util.Desc;

import javax.annotation.Nonnull;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2022/2/27 20:35
 */
public final class StaticField implements LoadExpression {
    Desc field;

    public StaticField(Desc field) {
        this.field = field;
    }

    @Nonnull
    @Override
    public Expression compress() {
        return this;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof StaticField))
            return false;
        StaticField field = (StaticField) left;
        return field.field.equals(this.field);
    }

    @Override
    public Type type() {
        return ParamHelper.parseField(field.param);
    }

    @Override
    public void write(ParseContext tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        tree.node(new FieldInsnNode(Opcodes.GETSTATIC, field.owner, field.name, field.param));
    }

    @Override
    public String toString() {
        return field.owner + '.' + field.name;
    }

    @Override
    public void write2(ParseContext tree) {
        tree.node(new FieldInsnNode(Opcodes.PUTSTATIC, field.owner, field.name, field.param));
    }

    @Override
    public byte loadType() {
        return STATIC_FIELD;
    }
}
