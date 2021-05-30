package roj.kscript.parser.expr;

import roj.config.word.NotStatementException;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.Opcode;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 获取对象可变名称属性
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class ArrayGet implements LoadExpression {
    Expression array, index;

    public ArrayGet(Expression array, Expression index) {
        this.array = array;
        this.index = index;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        if(noRet)
            throw new NotStatementException();

        this.array.write(tree, false);
        this.index.write(tree, false);
        tree.Std(Opcode.GET_OBJ);
    }

    @Nonnull
    @Override
    public Expression compress() {
        array = array.compress();
        index = index.compress();
        return this;
    }

    @Override
    public byte type() {
        return -1;
    }

    @Override
    public String toString() {
        return array.toString() + '[' + index.toString() + ']';
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof ArrayGet))
            return false;
        ArrayGet get = (ArrayGet) left;
        return get.array.isEqual(array) && get.index.isEqual(index);
    }

    @Override
    public KType compute(Map<String, KType> param) {
        return array.compute(param).asArray().get(index.compute(param).asInt());
    }

    @Override
    public void writeLoad(ASTree tree) {
        this.array.write(tree, false);
        this.index.write(tree, false);
    }

    @Override
    public void writeObj(ASTree tree) {
        array.write(tree, false);
    }

    @Override
    public void writeKey(ASTree tree) {
        index.write(tree, false);
    }
}
