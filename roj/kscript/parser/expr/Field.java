package roj.kscript.parser.expr;

import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.type.KString;
import roj.kscript.type.KType;

import java.util.Map;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public class Field implements LoadExpression {
    final Expression parent;
    final String name;

    public Field(Expression parent, String name) {
        this.parent = parent.compress();
        this.name = name;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Field))
            return false;
        Field field = (Field) left;
        return field.parent.isEqual(parent) && field.name.equals(name);
    }

    @Override
    public void write(ASTree tree) {
        parent.write(tree);
        tree.Load(KString.valueOf(name)).Std(ASTCode.GET_OBJECT);
    }

    @Override
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        return parent.compute(parameters, thisContext).asObject().get(name);
    }

    @Override
    public String toString() {
        return String.valueOf(parent) + '.' + name;
    }

    @Override
    public void writeLoad(ASTree tree) {
        this.parent.write(tree);
        tree.Load(KString.valueOf(name));
    }
}
