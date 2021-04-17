package roj.kscript.parser.expr;

import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.node.IfNode;
import roj.kscript.ast.node.LabelNode;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 三元运算符 ? :
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class TrueIf implements Expression {
    private final Expression determine, truly, fake;

    public TrueIf(Expression determine, Expression truly, Expression fake) {
        this.determine = determine.compress();
        this.truly = truly.compress();
        this.fake = fake.compress();
    }

    @Override
    public void write(ASTree tree) {
        LabelNode ifFalse = new LabelNode();
        LabelNode end = new LabelNode();

        determine.write(tree);
        truly.write(tree.If(ifFalse, IfNode.IS_TRUE).Goto(end));
        fake.write(tree.Node(ifFalse));
        tree.Node(end);

        /**
         * if(!determine)
         *   goto :ifFalse
         *  truly
         *  goto :end
         * :ifFalse
         *  fake
         * :end
         */
    }

    @Override
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        return determine.compute(parameters, thisContext).asBoolean() ? truly.compute(parameters, thisContext) : fake.compute(parameters, thisContext);
    }

    @Nonnull
    @Override
    public Expression compress() {
        if (determine.type() == -1) {
            return this;
        } else {
            return determine.asCst().asBoolean() ? truly : fake;
        }
    }

    @Override
    public byte type() {
        byte typeA = truly.type();
        byte typeB = fake.type();
        return typeA == typeB ? typeA : -1;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof TrueIf))
            return false;
        TrueIf trueIf = (TrueIf) left;
        return trueIf.determine.isEqual(determine) && trueIf.truly.isEqual(truly) && trueIf.fake.isEqual(fake);
    }

    @Override
    public String toString() {
        return determine.toString() + " ? " + truly + " : " + fake;
    }
}
