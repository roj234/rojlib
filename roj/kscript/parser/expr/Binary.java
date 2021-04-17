package roj.kscript.parser.expr;

import roj.concurrent.OperationDone;
import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.node.IfNode;
import roj.kscript.ast.node.LabelNode;
import roj.kscript.parser.Symbol;
import roj.kscript.type.*;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 操作符 - 二元操作 a + b
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Binary implements Expression {
    final short operator;
    final Expression left, right;
    LabelNode target;

    public Binary(short operator, Expression left, Expression right, LabelNode ifFalse) {
        switch (operator) {
            default:
                throw new IllegalArgumentException("Unsupported operator " + Symbol.byId(operator));
            case Symbol.pow:
            case Symbol.add:
            case Symbol.and:
            case Symbol.divide:
            case Symbol.logic_and:
            case Symbol.logic_or:
            case Symbol.lsh:
            case Symbol.mod:
            case Symbol.mul:
            case Symbol.or:
            case Symbol.rsh:
            case Symbol.rsh_unsigned:
            case Symbol.sub:
            case Symbol.xor:
                break;

            case Symbol.lss:
            case Symbol.gtr:
            case Symbol.geq:
            case Symbol.leq:
            case Symbol.equ:
            case Symbol.neq:
            case Symbol.feq:
                this.target = ifFalse;
        }
        this.operator = operator;
        this.left = left.compress();
        this.right = right.compress();
    }

    @Override
    public void write(ASTree tree) {
        left.write(tree);
        switch (operator) {
            case Symbol.logic_or:
            case Symbol.logic_and:
                break;
            default:
                right.write(tree);
        }

        writeOperator(tree);
    }

    void writeOperator(ASTree tree) {
        switch (operator) {
            case Symbol.lss:
            case Symbol.gtr:
            case Symbol.geq:
            case Symbol.leq:
            case Symbol.equ:
            case Symbol.feq:
            case Symbol.neq: {
                LabelNode end = new LabelNode();
                // todo optimize
                if(target == null || target == LabelNode.TRY_CATCH_ENDPOINT) {
                    LabelNode label = new LabelNode();
                    tree.If(label, (byte) (operator - 500)).Load(KBoolean.TRUE).Goto(end).Node(label).Load(KBoolean.FALSE).node0(end);
                } else {
                    tree.If(target, (byte) (operator - 500));
                }
                break;
            }
            case Symbol.add:
                tree.Std(ASTCode.ADD);
                break;
            case Symbol.and:
                tree.Std(ASTCode.AND);
                break;
            case Symbol.logic_and: {
                LabelNode end = new LabelNode();
                LabelNode end1 = new LabelNode();
                right.write(tree.If(end, IfNode.IS_TRUE));
                tree.If(end, IfNode.IS_TRUE).Load(KBoolean.TRUE).Goto(end1).Node(end).Load(KBoolean.FALSE).node0(end1);
                // if a && b
            }
            break;
            case Symbol.logic_or: {
                LabelNode end = new LabelNode();
                LabelNode end1 = new LabelNode();
                right.write(tree.Std(ASTCode.DUP).If(end, IfNode.IS_TRUE).Goto(end1).Node(end).Std(ASTCode.POP)); // on-stack: left, if left is true => end1, else => end
                tree.node0(end1);
                // if a || b
                // = a ? a : b 而不是true/false ...
            }
            break;
            case Symbol.or:
                tree.Std(ASTCode.OR);
                break;
            case Symbol.divide:
                tree.Std(ASTCode.DIV);
                break;
            case Symbol.lsh:
                tree.Std(ASTCode.SHIFT_L);
                break;
            case Symbol.mod:
                tree.Std(ASTCode.MOD);
                break;
            case Symbol.mul:
                tree.Std(ASTCode.MUL);
                break;
            case Symbol.rsh:
                tree.Std(ASTCode.SHIFT_R);
                break;
            case Symbol.rsh_unsigned:
                tree.Std(ASTCode.U_SHIFT_R);
                break;
            case Symbol.sub:
                tree.Std(ASTCode.SUB);
                break;
            case Symbol.xor:
                tree.Std(ASTCode.XOR);
                break;
        }
    }

    @Nonnull
    @Override
    public Expression compress() {
        if (left.type() == -1 || right.type() == -1)
            return this;

        final boolean d = left.type() == 1 || right.type() == 1;
        final Constant l = left.compress().asCst(), r = right.compress().asCst();

        switch (operator) {
            case Symbol.pow:
                return Constant.valueOf(Math.pow(l.asDouble(), r.asDouble()));
            case Symbol.lss:
                return Constant.valueOf(d ?
                        l.asDouble() < r.asDouble() :
                        l.asInteger() < r.asInteger());
            case Symbol.gtr:
                return Constant.valueOf(d ?
                        l.asDouble() > r.asDouble() :
                        l.asInteger() > r.asInteger());
            case Symbol.geq:
                return Constant.valueOf(d ?
                        l.asDouble() >= r.asDouble() :
                        l.asInteger() >= r.asInteger());
            case Symbol.leq:
                return Constant.valueOf(d ?
                        l.asDouble() <= r.asDouble() :
                        l.asInteger() <= r.asInteger());
            case Symbol.equ:
            case Symbol.neq:
                return Constant.valueOf((operator == Symbol.equ) == l.val().equalsTo(r.val()));
            case Symbol.feq:
                KType lv = l.val();
                KType rv = r.val();
                switch (l.type()) {
                    case 0:
                    case 1:
                        return Constant.valueOf(r.type() < 2 && lv.equalsTo(rv));
                    case 2:
                        return Constant.valueOf(r.type() == 2 && l.asString().equals(r.asString()));
                    case 3:
                        return Constant.valueOf(lv == rv);
                }
                throw OperationDone.NEVER;
            case Symbol.add:
                switch (l.type()) {
                    case 0:
                    case 1:
                        return Constant.valueOf(d ?
                                KDouble.valueOf(l.asDouble() + r.asDouble()) :
                                KInteger.valueOf(l.asInteger() + r.asInteger()));
                    case 2:
                        return Constant.valueOf(l.asString() + r.asString());
                    case 3:
                        return Constant.valueOf(l.asInteger() + r.asInteger());
                }
                throw OperationDone.NEVER;
            case Symbol.sub:
                return Constant.valueOf(d ?
                        KDouble.valueOf(l.asDouble() - r.asDouble()) :
                        KInteger.valueOf(l.asInteger() - r.asInteger()));
            case Symbol.divide:
                return Constant.valueOf(l.asDouble() / r.asDouble());
            case Symbol.mul:
                return Constant.valueOf(d ?
                        KDouble.valueOf(l.asDouble() * r.asDouble()) :
                        KInteger.valueOf(l.asInteger() * r.asInteger()));
            case Symbol.logic_and:
                return Constant.valueOf(l.asBoolean() && r.asBoolean());
            case Symbol.logic_or:
                return l.asBoolean() ? l : r;
            case Symbol.mod:
                return Constant.valueOf(KInteger.valueOf(d ?
                        (int) (l.asDouble() % r.asDouble()) :
                        l.asInteger() % r.asInteger()));
            case Symbol.and:
                return Constant.valueOf(l.asInteger() & r.asInteger());
            case Symbol.lsh:
                return Constant.valueOf(l.asInteger() << r.asInteger());
            case Symbol.or:
                return Constant.valueOf(l.asInteger() | r.asInteger());
            case Symbol.rsh:
                return Constant.valueOf(l.asInteger() >> r.asInteger());
            case Symbol.rsh_unsigned:
                return Constant.valueOf(l.asInteger() >>> r.asInteger());
            case Symbol.xor:
                return Constant.valueOf(l.asInteger() ^ r.asInteger());
        }
        throw OperationDone.NEVER;
    }

    @Override
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        KType l = left.compute(parameters, thisContext);
        KType r = right.compute(parameters, thisContext);
        boolean d = l.getType() == Type.DOUBLE || r.getType() == Type.DOUBLE;

        switch (operator) {
            case Symbol.pow:
                return KDouble.valueOf(Math.pow(l.asDouble(), r.asDouble()));
            case Symbol.lss:
                return KBoolean.valueOf(d ?
                        l.asDouble() < r.asDouble() :
                        l.asInteger() < r.asInteger());
            case Symbol.gtr:
                return KBoolean.valueOf(d ?
                        l.asDouble() > r.asDouble() :
                        l.asInteger() > r.asInteger());
            case Symbol.geq:
                return KBoolean.valueOf(d ?
                        l.asDouble() >= r.asDouble() :
                        l.asInteger() >= r.asInteger());
            case Symbol.leq:
                return KBoolean.valueOf(d ?
                        l.asDouble() <= r.asDouble() :
                        l.asInteger() <= r.asInteger());
            case Symbol.equ:
            case Symbol.neq:
                return KBoolean.valueOf((operator == Symbol.equ) == l.equalsTo(r));
            case Symbol.feq:
                return KBoolean.valueOf(l.getType() == r.getType() && l.equalsTo(r));
            case Symbol.add:
                switch (l.getType()) {
                    case NUMBER:
                    case DOUBLE:
                    case BOOL:
                        return d ?
                                KDouble.valueOf(l.asDouble() + r.asDouble()) :
                                KInteger.valueOf(l.asInteger() + r.asInteger());
                    case STRING:
                        return KString.valueOf(l.asString() + r.asString());
                }
                throw OperationDone.NEVER;
            case Symbol.sub:
                return d ?
                        KDouble.valueOf(l.asDouble() - r.asDouble()) :
                        KInteger.valueOf(l.asInteger() - r.asInteger());
            case Symbol.divide:
                return KDouble.valueOf(l.asDouble() / r.asDouble());
            case Symbol.mul:
                return d ?
                        KDouble.valueOf(l.asDouble() * r.asDouble()) :
                        KInteger.valueOf(l.asInteger() * r.asInteger());
            case Symbol.logic_and:
                return KBoolean.valueOf(l.asBoolean() && r.asBoolean());
            case Symbol.logic_or:
                return l.asBoolean() ? l : r; // js原版就是这么干的
            case Symbol.mod:
                return KInteger.valueOf(d ?
                        (int) (l.asDouble() % r.asDouble()) :
                        l.asInteger() % r.asInteger());
            case Symbol.and:
                return KInteger.valueOf(l.asInteger() & r.asInteger());
            case Symbol.lsh:
                return KInteger.valueOf(l.asInteger() << r.asInteger());
            case Symbol.or:
                return KInteger.valueOf(l.asInteger() | r.asInteger());
            case Symbol.rsh:
                return KInteger.valueOf(l.asInteger() >> r.asInteger());
            case Symbol.rsh_unsigned:
                return KInteger.valueOf(l.asInteger() >>> r.asInteger());
            case Symbol.xor:
                return KInteger.valueOf(l.asInteger() ^ r.asInteger());
        }
        throw OperationDone.NEVER;
    }

    @Override
    public byte type() {
        if (left.type() == -1 || right.type() == -1)
            return -1;

        switch (operator) {
            case Symbol.lss:
            case Symbol.gtr:
            case Symbol.geq:
            case Symbol.leq:
            case Symbol.equ:
            case Symbol.feq:
            case Symbol.neq:
            case Symbol.logic_and:
            case Symbol.logic_or:
                return 3;
            case Symbol.and:
            case Symbol.or:
            case Symbol.xor:
            case Symbol.lsh:
            case Symbol.rsh:
            case Symbol.rsh_unsigned:
            case Symbol.mod:
                return 0;
            case Symbol.add:
            case Symbol.divide:
            case Symbol.mul:
            case Symbol.sub:
            case Symbol.pow:
                return (byte) (right.type() == 1 || left.type() == 1 ? 1 : 0);
        }
        return -1;
    }

    @Override
    public String toString() {
        String l = left.toString();
        if (left.type() == -1)
            l = '(' + l + ')';
        String r = right.toString();
        if (right.type() == -1)
            r = '(' + r + ')';
        return l + Symbol.byId(operator) + r;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Binary))
            return false;
        Binary b = (Binary) left;
        return b.left.isEqual(left) && b.right.isEqual(right) && b.operator == operator;
    }
}
