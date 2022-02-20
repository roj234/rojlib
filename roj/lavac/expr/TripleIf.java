package roj.lavac.expr;

import roj.asm.Opcodes;
import roj.asm.tree.anno.AnnValInt;
import roj.asm.tree.insn.IfInsnNode;
import roj.asm.tree.insn.LabelInsnNode;
import roj.asm.type.Type;
import roj.lavac.parser.ParseContext;

import javax.annotation.Nonnull;

/**
 * 操作符 - 三元运算符 ? :
 *
 * @author Roj233
 * @since 2022/3/1 19:17
 */
public final class TripleIf implements Expression {
    Expression determine, truly, fake;

    public TripleIf(Expression determine, Expression truly, Expression fake) {
        this.determine = determine;
        this.truly = truly;
        this.fake = fake;
    }

    @Override
    public void write(ParseContext tree, boolean noRet) {
        LabelInsnNode ifFalse = new LabelInsnNode();
        LabelInsnNode end = new LabelInsnNode();

        determine.write(tree, false);
        tree.node(new IfInsnNode(Opcodes.IFEQ, ifFalse));
        truly.write(tree, noRet);
        tree.goto1(end).node(ifFalse);
        fake.write(tree, noRet);
        tree.node(end);

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

    @Nonnull
    @Override
    public Expression compress() {
        truly = truly.compress();
        fake = fake.compress();
        if (!(determine = determine.compress()).isConstant()) {
            return this;
        } else {
            return ((AnnValInt) determine.asCst().val()).value == 1 ? truly : fake;
        }
    }

    @Override
    public Type type() {
        // todo...
        return truly.type();
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof TripleIf))
            return false;
        TripleIf tripleIf = (TripleIf) left;
        return tripleIf.determine.isEqual(determine) && tripleIf.truly.isEqual(truly) && tripleIf.fake.isEqual(fake);
    }

    @Override
    public String toString() {
        return determine.toString() + " ? " + truly + " : " + fake;
    }
}
