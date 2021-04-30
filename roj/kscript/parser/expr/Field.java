package roj.kscript.parser.expr;

import roj.kscript.api.IObject;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.type.KString;
import roj.kscript.type.KType;
import roj.kscript.util.NotStatementException;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 获取定名字的量
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public class Field implements LoadExpression {
    Expression parent;
    final String name;
    boolean delete;

    public Field(Expression parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    @Nonnull
    @Override
    public Expression compress() {
        parent = parent.compress();
        return this;
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


    public boolean setDeletion() {
        return delete = true;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        if(noRet && !delete)
            throw new NotStatementException();

        parent.write(tree, false);
        tree.Load(KString.valueOf(name)).Std(delete ? ASTCode.DELETE_OBJECT : ASTCode.GET_OBJECT);
    }

    @Override
    public KType compute(Map<String, KType> parameters, IObject thisContext) {
        return parent.compute(parameters, thisContext).asObject().get(name);
    }

    @Override
    public String toString() {
        return String.valueOf(parent) + '.' + name;
    }

    @Override
    public void writeLoad(ASTree tree) {
        this.parent.write(tree, false);
        tree.Load(KString.valueOf(name));
    }
}
