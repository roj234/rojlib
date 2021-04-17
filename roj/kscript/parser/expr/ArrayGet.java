package roj.kscript.parser.expr;

import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
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
    final Expression array, index;

    public ArrayGet(Expression array, Expression index) {
        this.array = array.compress();
        this.index = index.compress();
    }

    @Override
    public void write(ASTree tree) {
        this.array.write(tree);
        this.index.write(tree);
        tree.Std(ASTCode.GET_OBJECT);
    }

    @Nonnull
    @Override
    public Expression compress() {
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
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        return array.compute(parameters, thisContext).asArray().get(index.compute(parameters, thisContext).asInteger());
    }

    @Override
    public void writeLoad(ASTree tree) {
        this.array.write(tree);
        this.index.write(tree);
    }
}
