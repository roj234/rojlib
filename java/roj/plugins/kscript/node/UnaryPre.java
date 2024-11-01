package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.config.data.*;
import roj.plugins.kscript.KCompiler;
import roj.plugins.kscript.token.KSLexer;
import roj.text.TextUtil;

/**
 * 一元前缀运算
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class UnaryPre implements UnaryPreNode {
    private final short operator;
    private ExprNode right;
    UnaryPre(short operator) {this.operator = operator;}

    @Override public String toString() {return KSLexer.repr(operator) + ' ' + right;}

    @NotNull
    @Override
    public ExprNode resolve() {
        // inc/dec不能对常量使用, 所以不用管了
        if(!(right = right.resolve()).isConstant()) return this;
        return Constant.valueOf(doEval(null, right.toConstant()));
    }

    @Override
    public byte type() {
        return switch (operator) {
            case KSLexer.inc, KSLexer.dec -> (byte) (right.type() == 1 ? 1 : 0);
            case KSLexer.logic_not -> 3;
            case KSLexer.rev -> 0;
            case KSLexer.sub -> switch (right.type()) {
                case 0, 1 -> right.type();
                case 2 -> (byte) TextUtil.isNumber(right.toConstant().asString());
                default -> 0;
            };
            default -> -1;
        };
    }

    @Override public boolean isConstant() {return operator != KSLexer.inc && operator != KSLexer.dec && right.isConstant();}

    @Override
    public CEntry eval(CMap ctx) {
        CEntry val = right.eval(ctx);
        return doEval(ctx, val);
    }
    @Override
    public void compile(KCompiler tree, boolean noRet) {
    }

    private CEntry doEval(CMap ctx, CEntry val) {
        return switch (operator) {
            case KSLexer.add -> val.getType().isNumber() ? val : val.mayCastTo(Type.DOUBLE) ? CDouble.valueOf(val.asDouble()) : CInt.valueOf(val.asInt());
            case KSLexer.sub -> val.getType() == Type.INTEGER ? CInt.valueOf(-val.asInt()) : CDouble.valueOf(-val.asDouble());
            case KSLexer.logic_not -> CBoolean.valueOf(!val.asBool());
            case KSLexer.rev -> CInt.valueOf(~val.asInt());
            default -> {
                int inc = operator == KSLexer.inc ? 1 : -1;
                var node = (VarNode) right;
                val = val.getType() == Type.INTEGER ? CInt.valueOf(val.asInt() + inc) : CDouble.valueOf(val.asDouble() + inc);
                node.evalStore(ctx, val);
                yield val;
            }
        };
    }

    public String setRight(ExprNode right) {
        if(right == null) return "unary.missing_operand";

        if(!(right instanceof VarNode)) {
            switch (operator) {
                case KSLexer.inc, KSLexer.dec -> {
                    return "unary.expecting_variable";
                }
            }
        }

        this.right = right;

        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UnaryPre pre = (UnaryPre) o;

        if (operator != pre.operator) return false;
		return right.equals(pre.right);
	}

    @Override
    public int hashCode() {
        int result = operator;
        result = 31 * result + right.hashCode();
        return result;
    }
}
