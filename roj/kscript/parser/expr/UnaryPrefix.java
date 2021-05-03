package roj.kscript.parser.expr;

import roj.concurrent.OperationDone;
import roj.kscript.api.IObject;
import roj.kscript.ast.ASTree;
import roj.kscript.ast.OpCode;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KDouble;
import roj.kscript.type.KInt;
import roj.kscript.type.KType;
import roj.kscript.util.NotStatementException;
import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * 一元运算符
 * <BR>
 * 我的实现方式有点坑... 再说
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class UnaryPrefix implements Expression {
    private short operator;
    private Expression right;

    public UnaryPrefix(short operator) {
        switch (operator) {
            default:
                throw new IllegalArgumentException("Unsupported operator " + operator);
            case Symbol.logic_not:
            case Symbol.rev:
            case Symbol.inc:
            case Symbol.dec:
            case Symbol.sub:
        }
        this.operator = operator;
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        switch (operator) {
            case Symbol.logic_not:
            case Symbol.sub:
            case Symbol.rev:
                if(noRet)
                    throw new NotStatementException();
                // those could only be used inside expr...

                right.write(tree, false);

                OpCode op;
                switch (operator) {
                    case Symbol.logic_not:
                        op = OpCode.NOT;
                        break;
                    case Symbol.sub:
                        op = OpCode.NEGATIVE;
                        break;
                    case Symbol.rev:
                        op = OpCode.REVERSE;
                        break;
                    default:
                        throw OperationDone.NEVER;
                }

                tree.Std(op);
                break;
            case Symbol.inc:
            case Symbol.dec:
                if (right.getClass() == Variable.class) {
                    Variable v = (Variable) right;

                    tree.Inc(v.name, operator == Symbol.inc ? 1 : -1);

                    v._after_write_op();

                    if(!noRet) {
                        tree.Get(v.name);
                    }
                } else {
                    Field f = (Field) right;
                    f.writeLoad(tree);

                    tree.Std(OpCode.DUP2)
                            .Std(OpCode.GET_OBJ)
                            .Load(KInt.valueOf(operator == Symbol.inc ? 1 : -1))
                            .Std(OpCode.ADD);

                    if(noRet) {
                        tree.Std(OpCode.PUT_OBJ);
                    } else {
                                tree.Std(OpCode.DUP)
                                .Std(OpCode.SWAP3)
                                .Std(OpCode.PUT_OBJ);
                    }

                }
                break;
        }
    }

    @Override
    public KType compute(Map<String, KType> param, IObject $this) {
        if(operator == Symbol.sub) {
            KType base = right.compute(param, $this);

            if (base.isInt()) {
                KInt i = base.asKInt();
                i.value = -i.value;
            } else {
                KDouble i = base.asKDouble();
                i.value = -i.value;
            }
            return base;
        }

        int val = operator == Symbol.inc ? 1 : -1;
        KType base;

        if(right instanceof Variable) {
            Variable v = (Variable) right;
            base = right.compute(param, $this);
        } else {
            Field field = (Field) right;
            base = field.parent.compute(param, $this).asObject().get(field.name);
        }

        if (base.isInt()) {
            KInt i = base.asKInt();
            i.value += val;
        } else {
            KDouble i = base.asKDouble();
            i.value += val;
        }

        return base;
    }

    @Override
    public boolean isConstant() {
        return operator != Symbol.inc && operator != Symbol.dec && type() != -1;
    }

    @Nonnull
    @Override
    public Expression compress() {
        if(right == null)
            throw new IllegalArgumentException("Missing right");

        if (operator == Symbol.sub && right instanceof UnaryPrefix) {
            UnaryPrefix p = (UnaryPrefix) right;
            if (p.operator == Symbol.sub) // 双重否定, 不过这里就没有cast to number了
                return p.right.compress();
        }

        // inc/dec不能对常量使用, 所以不用管了
        if(!(right = right.compress()).isConstant()) return this;
        final Constant cst = right.asCst();

        switch (right.type()) {
            case -1:
                return this;
            case 0:
                switch (operator) {
                    case Symbol.logic_not:
                        return Constant.valueOf(!cst.asBool());
                    case Symbol.rev:
                        KInt i = cst.val().asKInt();
                        i.value = ~i.value;
                        return right;
                    case Symbol.sub: {
                        i = cst.val().asKInt();
                        i.value = -i.value;
                        return right;
                    }
                }
                break;
            case 1: // support not but cant rev
            case 2:
                switch (operator) {
                    case Symbol.logic_not:
                        return Constant.valueOf(!cst.asBool());
                    case Symbol.rev:
                        return Constant.valueOf(~cst.asInt());
                    case Symbol.sub:
                        if (right.type() == 1) {
                            return Constant.valueOf(-cst.asDouble());
                        }
                        int isDouble = TextUtil.isNumber(cst.asString());
                        return new Constant(isDouble == 1 ? KDouble.valueOf(-cst.asDouble()) : KInt.valueOf(-cst.asInt()));
                }
                break;
            case 3:
                boolean operand = cst.asBool();
                switch (operator) {
                    case Symbol.logic_not:
                        return Constant.valueOf(!operand);
                    case Symbol.rev:
                        return Constant.valueOf(operand ? ~1 : ~0);
                    case Symbol.sub:
                        return Constant.valueOf(operand ? -1 : -0);
                }
                break;
        }

        throw OperationDone.NEVER;
    }

    @Override
    public byte type() {
        switch (operator) {
            case Symbol.inc:
            case Symbol.dec:
                return (byte) (right.type() == 1 ? 1 : 0);
            case Symbol.logic_not:
                return 3;
            case Symbol.rev:
                return 0;
            case Symbol.sub:
                switch (right.type()) {
                    case 0:
                    case 1:
                        return right.type();
                    case 2:
                        return (byte) TextUtil.isNumber(right.asCst().asString());
                    case 3:
                        return 0;
                }
        }
        return -1;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof UnaryPrefix))
            return false;
        UnaryPrefix left1 = (UnaryPrefix) left;
        return left1.right.isEqual(right) && left1.operator == operator;
    }

    @Override
    public String toString() {
        return Symbol.byId(operator) + ' ' + right;
    }

    public String setRight(Expression right) {
        if(right == null)
            return "upf: right is null";

        if(!(right instanceof Field) && !(right instanceof ArrayGet)) {
            switch (operator) {
                case Symbol.inc:
                case Symbol.dec:
                    return "unary.expecting_variable";
            }
        }

        this.right = right;

        return null;
    }
}
