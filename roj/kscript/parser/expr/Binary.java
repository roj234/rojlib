package roj.kscript.parser.expr;

import roj.concurrent.OperationDone;
import roj.config.word.NotStatementException;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.IfNode;
import roj.kscript.ast.LabelNode;
import roj.kscript.ast.Opcode;
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
    Expression left, right;
    LabelNode target;

    public Binary(short operator, Expression left, Expression right) {
        switch (operator) {
            default:
                throw new IllegalArgumentException("Unsupported operator " + Symbol.byId(operator));
            case Symbol.pow:
            case Symbol.add:
            case Symbol.and:
            case Symbol.div:
            case Symbol.lsh:
            case Symbol.mod:
            case Symbol.mul:
            case Symbol.or:
            case Symbol.rsh:
            case Symbol.rsh_unsigned:
            case Symbol.sub:
            case Symbol.xor:
            case Symbol.logic_and:
            case Symbol.logic_or:
            case Symbol.lss:
            case Symbol.gtr:
            case Symbol.geq:
            case Symbol.leq:
            case Symbol.equ:
            case Symbol.neq:
            case Symbol.feq:
                break;
        }
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    public int constNum() {
        int a = (left = left.compress()).isConstant() ? 1 : 0;
        if((right = right.compress()).isConstant())
            a++;
        return a;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        if(noRet) {
            switch (operator) {
                case Symbol.logic_or:
                case Symbol.logic_and:
                    break;
                default:
                    throw new NotStatementException();
            }
        }

        switch (operator) {
            case Symbol.logic_or:
            case Symbol.logic_and:
                break;
            default:
                left.write(tree, false);
                right.write(tree, false);
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
            case Symbol.neq:
                if(target == null) {
                    tree.IfLoad(operator);
                } else {
                    tree.If(target, operator);
                }
                break;
            case Symbol.add:
                tree.Std(Opcode.ADD);
                break;
            case Symbol.and:
                tree.Std(Opcode.AND);
                break;
            case Symbol.logic_and: {
                // if a && b

                left.write(tree, false);
                if (target != null) {
                    right.write(tree.If(target, IfNode.TRUE), false);
                    tree.If(target, IfNode.TRUE);
                } else {
                    LabelNode falseTo = new LabelNode();
                    LabelNode fin = new LabelNode();
                    right.write(tree.If(falseTo, IfNode.TRUE), false);
                    tree.If(falseTo, IfNode.TRUE).Load(KBool.TRUE).Goto(fin).Node(falseTo).Load(KBool.FALSE).node0(fin);
                }
            }
            break;
            case Symbol.logic_or: {
                LabelNode falseTo = new LabelNode();
                LabelNode fin = new LabelNode();

                // if a || b
                // = a ? a : b 而不是true/false ...

                left.write(tree, false);
                if (target != null) {
                    right.write(tree.If(falseTo, IfNode.TRUE).Goto(fin).Node(falseTo), false);
                    tree.If(target, IfNode.TRUE).node0(fin);
                } else {
                    right.write(tree
                            .Std(Opcode.DUP)
                            // left, left
                            .If(falseTo, IfNode.TRUE)
                            .Goto(fin)
                            .Node(falseTo)
                            .Std(Opcode.POP), false);
                    tree.node0(fin);
                }
            }
            break;
            case Symbol.or:
                tree.Std(Opcode.OR);
                break;
            case Symbol.div:
                tree.Std(Opcode.DIV);
                break;
            case Symbol.lsh:
                tree.Std(Opcode.SHIFT_L);
                break;
            case Symbol.mod:
                tree.Std(Opcode.MOD);
                break;
            case Symbol.mul:
                tree.Std(Opcode.MUL);
                break;
            case Symbol.rsh:
                tree.Std(Opcode.SHIFT_R);
                break;
            case Symbol.rsh_unsigned:
                tree.Std(Opcode.U_SHIFT_R);
                break;
            case Symbol.sub:
                tree.Std(Opcode.SUB);
                break;
            case Symbol.xor:
                tree.Std(Opcode.XOR);
                break;
            case Symbol.pow:
                tree.Std(Opcode.POW);
                break;
        }
    }

    @Nonnull
    @Override
    public Expression compress() {
        if ((left = left.compress()).isConstant() != (right = right.compress()).isConstant() || !right.isConstant()) {
            switch (operator) {
                case Symbol.logic_and:
                    if(left.isConstant()) {
                        if(left.asCst().asBool()) {
                            // temporary ifload node
                            return new TmpAsBool(right);
                        } else {
                            return Constant.valueOf(false);
                        }
                    }
                    break;
                case Symbol.logic_or:
                    if(left.isConstant()) {
                        if(left.asCst().asBool()) {
                            return left.asCst();
                        } else {
                            return right;
                        }
                    }
            }
            return this;
        }

        final boolean d = left.type() == 1 || right.type() == 1;
        final Constant l = left.compress().asCst(), r = right.compress().asCst();

        switch (operator) {
            case Symbol.pow:
                return Constant.valueOf(Math.pow(l.asDouble(), r.asDouble()));
            case Symbol.lss:
                return Constant.valueOf(d ?
                        l.asDouble() < r.asDouble() :
                        l.asInt() < r.asInt());
            case Symbol.gtr:
                return Constant.valueOf(d ?
                        l.asDouble() > r.asDouble() :
                        l.asInt() > r.asInt());
            case Symbol.geq:
                return Constant.valueOf(d ?
                        l.asDouble() >= r.asDouble() :
                        l.asInt() >= r.asInt());
            case Symbol.leq:
                return Constant.valueOf(d ?
                        l.asDouble() <= r.asDouble() :
                        l.asInt() <= r.asInt());
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
                        return r.type() == 2 ? Constant.valueOf(l.asString() + r.asString()) : Constant.valueOf(d ?
                                KDouble.valueOf(l.asDouble() + r.asDouble()) :
                                KInt.valueOf(l.asInt() + r.asInt()));
                    case 2:
                        return Constant.valueOf(l.asString() + r.asString());
                    case 3:
                        return Constant.valueOf(l.asInt() + r.asInt());
                }
                throw OperationDone.NEVER;
            case Symbol.sub:
                return Constant.valueOf(d ?
                        KDouble.valueOf(l.asDouble() - r.asDouble()) :
                        KInt.valueOf(l.asInt() - r.asInt()));
            case Symbol.div:
                return Constant.valueOf(l.asDouble() / r.asDouble());
            case Symbol.mul:
                return Constant.valueOf(d ?
                        KDouble.valueOf(l.asDouble() * r.asDouble()) :
                        KInt.valueOf(l.asInt() * r.asInt()));
            case Symbol.logic_and:
                return Constant.valueOf(l.asBool() && r.asBool());
            case Symbol.logic_or:
                return l.asBool() ? l : r;
            case Symbol.mod:
                return Constant.valueOf(KInt.valueOf(d ?
                        (int) (l.asDouble() % r.asDouble()) :
                        l.asInt() % r.asInt()));
            case Symbol.and:
                return Constant.valueOf(l.asInt() & r.asInt());
            case Symbol.lsh:
                return Constant.valueOf(l.asInt() << r.asInt());
            case Symbol.or:
                return Constant.valueOf(l.asInt() | r.asInt());
            case Symbol.rsh:
                return Constant.valueOf(l.asInt() >> r.asInt());
            case Symbol.rsh_unsigned:
                return Constant.valueOf(l.asInt() >>> r.asInt());
            case Symbol.xor:
                return Constant.valueOf(l.asInt() ^ r.asInt());
        }
        throw OperationDone.NEVER;
    }

    @Override
    public KType compute(Map<String, KType> param) {
        KType l = left.compute(param);

        switch (operator) {
            case Symbol.logic_and:
                return KBool.valueOf(l.asBool() && right.compute(param).asBool());
            case Symbol.logic_or:
                return l.asBool() ? l : right.compute(param); // js就是这么干的
        }

        KType r = right.compute(param);
        boolean d = l.getType() == Type.DOUBLE || r.getType() == Type.DOUBLE;

        switch (operator) {
            case Symbol.pow:
                return KDouble.valueOf(Math.pow(l.asDouble(), r.asDouble()));
            case Symbol.lss:
                return KBool.valueOf(d ?
                        l.asDouble() < r.asDouble() :
                        l.asInt() < r.asInt());
            case Symbol.gtr:
                return KBool.valueOf(d ?
                        l.asDouble() > r.asDouble() :
                        l.asInt() > r.asInt());
            case Symbol.geq:
                return KBool.valueOf(d ?
                        l.asDouble() >= r.asDouble() :
                        l.asInt() >= r.asInt());
            case Symbol.leq:
                return KBool.valueOf(d ?
                        l.asDouble() <= r.asDouble() :
                        l.asInt() <= r.asInt());
            case Symbol.equ:
            case Symbol.neq:
                return KBool.valueOf((operator == Symbol.equ) == l.equalsTo(r));
            case Symbol.feq:
                return KBool.valueOf(l.getType() == r.getType() && l.equalsTo(r));
            case Symbol.add:
                switch (l.getType()) {
                    case INT:
                    case DOUBLE:
                    case BOOL:
                        return d ?
                                KDouble.valueOf(l.asDouble() + r.asDouble()) :
                                KInt.valueOf(l.asInt() + r.asInt());
                    case STRING:
                        return KString.valueOf(l.asString() + r.asString());
                }
                throw OperationDone.NEVER;
            case Symbol.sub:
                return d ?
                        KDouble.valueOf(l.asDouble() - r.asDouble()) :
                        KInt.valueOf(l.asInt() - r.asInt());
            case Symbol.div:
                return KDouble.valueOf(l.asDouble() / r.asDouble());
            case Symbol.mul:
                return d ?
                        KDouble.valueOf(l.asDouble() * r.asDouble()) :
                        KInt.valueOf(l.asInt() * r.asInt());
            case Symbol.mod:
                return KInt.valueOf(d ?
                        (int) (l.asDouble() % r.asDouble()) :
                        l.asInt() % r.asInt());
            case Symbol.and:
                return KInt.valueOf(l.asInt() & r.asInt());
            case Symbol.lsh:
                return KInt.valueOf(l.asInt() << r.asInt());
            case Symbol.or:
                return KInt.valueOf(l.asInt() | r.asInt());
            case Symbol.rsh:
                return KInt.valueOf(l.asInt() >> r.asInt());
            case Symbol.rsh_unsigned:
                return KInt.valueOf(l.asInt() >>> r.asInt());
            case Symbol.xor:
                return KInt.valueOf(l.asInt() ^ r.asInt());
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
            case Symbol.div:
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

    public void setTarget(LabelNode ifFalse) {
        switch (operator) {
            case Symbol.logic_and: // only those need jump
            case Symbol.logic_or:
            case Symbol.lss:
            case Symbol.gtr:
            case Symbol.geq:
            case Symbol.leq:
            case Symbol.equ:
            case Symbol.neq:
            case Symbol.feq:
                this.target = ifFalse;
                break;
            default:
                if(ifFalse != null)
                    throw new IllegalArgumentException("Operator not support: " + Symbol.byId(operator));
        }
    }
}
