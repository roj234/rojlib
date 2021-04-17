package roj.kscript.parser.expr;

import roj.concurrent.OperationDone;
import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTCode;
import roj.kscript.ast.ASTree;
import roj.kscript.parser.Marks;
import roj.kscript.parser.Symbol;
import roj.kscript.type.KDouble;
import roj.kscript.type.KInteger;
import roj.kscript.type.KType;
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

    public String validate() {
        switch (operator) {
            case Symbol.inc:
            case Symbol.dec:
                if (this.right.type() != -1) {
                    return ("++/-- constant (Invalid operand)");
                }
                System.out.println("Validate " + this);
        }
        return null;
    }

    @Override
    public void write(ASTree tree) {
        switch (operator) {
            case Symbol.logic_not:
                right.write(tree.Section(Marks.START));
                tree.Std(ASTCode.NOT)
                        .Section(Marks.NEXT);
                break;
            case Symbol.rev:
                tree.Section(Marks.START);
                right.write(tree);
                tree.Std(ASTCode.REVERSE)
                        .Section(Marks.NEXT);
                break;
            case Symbol.inc:
            case Symbol.dec:
                if (right.getClass() == Variable.class) {
                    Variable v = (Variable) right;
                    right.write(tree.Inc(v.name, operator == Symbol.inc ? 1 : -1)
                            .Section(Marks.START));
                } else {
                    Field field = (Field) right;
                    field.writeLoad(tree);
                    tree
                            .Std(ASTCode.DUP2_2)
                            .Std(ASTCode.GET_OBJECT)
                            .Load(KInteger.valueOf(operator == Symbol.inc ? 1 : -1))
                            .Std(ASTCode.ADD)
                            .Std(ASTCode.PUT_OBJECT).Section(Marks.START);
                    field.write(tree);

                }
                tree.Section(Marks.NEXT);
                break;
            case Symbol.sub:
            case Symbol.NEGATIVE:
                if (right.getClass() == Variable.class) {
                    Variable v = (Variable) right;
                    v.writeLoad(tree);
                    tree.Std(ASTCode.NEGATIVE).Set(v.name);
                    right.write(tree.Section(Marks.START));
                } else {
                    Field field = (Field) right;
                    field.writeLoad(tree);
                    tree
                            .Std(ASTCode.DUP2_2)
                            .Std(ASTCode.GET_OBJECT)
                            .Std(ASTCode.NEGATIVE)
                            .Std(ASTCode.PUT_OBJECT).Section(Marks.START);
                    field.write(tree);

                }
                tree.Section(Marks.NEXT);
                break;
        }
    }

    @Override
    public KType compute(Map<String, KType> parameters, IGettable thisContext) {
        if(operator == Symbol.sub || operator == Symbol.NEGATIVE) {
            KType base = right.compute(parameters, thisContext);

            if (base.isInteger()) {
                KInteger i = base.asKInteger();
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
            base = right.compute(parameters, thisContext);
        } else {
            Field field = (Field) right;
            base = field.parent.compute(parameters, thisContext).asObject().get(field.name);
        }

        if (base.isInteger()) {
            KInteger i = base.asKInteger();
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

        if (operator == Symbol.NEGATIVE) { // 双重否定
            return ((UnaryPrefix)right).right.compress();
        }

        switch (operator) {
            case Symbol.inc:
            case Symbol.dec:
                System.out.println(this + validate());
                return this;
        }

        if(!(right = right.compress()).isConstant()) return this;
        final Constant cst = right.asCst();

        switch (right.type()) {
            case -1:
                return this;
            case 0:
                switch (operator) {
                    case Symbol.logic_not:
                        return Constant.valueOf(!cst.asBoolean());
                    case Symbol.rev: {
                        KInteger i = cst.val().asKInteger();
                        i.setValue(~i.getValue());
                        return right;
                    }
                    case Symbol.sub: {
                        KInteger i = cst.val().asKInteger();
                        i.setValue(-i.getValue());
                        return right;
                    }
                }
                break;
            case 1: // support not but cant rev
            case 2:
                switch (operator) {
                    case Symbol.logic_not:
                        return Constant.valueOf(!cst.asBoolean());
                    case Symbol.rev:
                        return Constant.valueOf(~cst.asInteger());
                    case Symbol.sub:
                        if (right.type() == 1) {
                            return Constant.valueOf(-cst.asDouble());
                        }
                        int isDouble = TextUtil.isNumber(cst.asString());
                        return new Constant(isDouble == 1 ? KDouble.valueOf(-cst.asDouble()) : KInteger.valueOf(-cst.asInteger()));
                }
                break;
            case 3:
                boolean operand = cst.asBoolean();
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
                return right.type();
            case Symbol.logic_not:
                return 3;
            case Symbol.rev:
                return 0;
            case Symbol.NEGATIVE:
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

    public Expression getRight() {
        return right;
    }

    public void setRight(Expression right) {
        if(right == null)
            return;

        if(this.right != null && this.right instanceof UnaryPrefix) {
            ((UnaryPrefix) this.right).setRight(right);
            return;
        }

        switch (operator) {
            case Symbol.inc:
            case Symbol.dec:
                right = right.requireWrite();
                break;
        }

        if(right instanceof UnaryPrefix) {
            UnaryPrefix ul = (UnaryPrefix) right;
            if(ul.operator == Symbol.sub) {
                operator = Symbol.NEGATIVE;
            }
        }

        this.right = right;
    }
}
