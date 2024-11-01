package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.concurrent.OperationDone;
import roj.config.data.*;
import roj.plugins.kscript.KCompiler;

import static roj.plugins.kscript.token.KSLexer.*;

/**
 * 二元操作 a 操作符 b
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class Binary implements ExprNode {
    final short operator;
    ExprNode left, right;

    public Binary(short operator) {this.operator = operator;}
    public Binary(short operator, ExprNode left, ExprNode right) {
        this(operator);
        this.left = left;
        this.right = right;
    }

    @Override
    public String toString() {
        String l = left.toString();
        if (left.type() == -1)
            l = '(' + l + ')';
        String r = right.toString();
        if (right.type() == -1)
            r = '(' + r + ')';
        return l + repr(operator) + r;
    }

    @Override
    public byte type() {
        if (left.type() == -1 || right.type() == -1)
            return -1;

        return switch (operator) {
            case lss, gtr, geq, leq, equ, feq, fne, neq, logic_and, logic_or -> 3;
            case and, or, xor, lsh, rsh, rsh_unsigned, mod -> 0;
            case add, div, mul, sub, pow -> (byte) (right.type() == 1 || left.type() == 1 ? 1 : 0);
            default -> -1;
        };
    }

    @NotNull
    @Override
    public ExprNode resolve() {
        left = left.resolve();
        right = right.resolve();
        if (left.isConstant()) return switch (operator) {
			case logic_and -> left.toConstant().asBool() ? right : left;
			case logic_or -> left.toConstant().asBool() ? left : right;
            case nullish_consolidating -> left.toConstant() == CNull.NULL ? right : left;
			default -> {
				if (!right.isConstant()) yield this;
				CEntry l = left.resolve().toConstant(), r = right.resolve().toConstant();
				yield Constant.valueOf(doEval(l, r));
			}
		};
        return this;
    }

    @Override
    public CEntry eval(CMap ctx) {
        var l = left.eval(ctx);

		return switch (operator) {
			case logic_and -> l.asBool() ? right.eval(ctx) : l;
			case logic_or -> l.asBool() ? l : right.eval(ctx); // js就是这么干的
            case nullish_consolidating -> l == CNull.NULL ? right.eval(ctx) : l;
			default -> {
				var r = right.eval(ctx);
				yield doEval(l, r);
			}
		};
    }
    @Override
    public void compile(KCompiler tree, boolean noRet) {
        if(noRet) {
            switch (operator) {
                case logic_or:
                case logic_and:
                    break;
                default:
                    throw new NotStatementException();
            }
        }

        switch (operator) {
            case logic_or:
            case logic_and:
                break;
            default:
                left.compile(tree, false);
                right.compile(tree, false);
        }

        writeOperator(tree);
    }

    void writeOperator(KCompiler tree) {
    }

    private CEntry doEval(CEntry l, CEntry r) {
        boolean isDouble = l.getType() == Type.DOUBLE || r.getType() == Type.DOUBLE;
        return switch (operator) {
            case pow -> CDouble.valueOf(Math.pow(l.asDouble(), r.asDouble()));
            case lss -> CBoolean.valueOf(isDouble ? l.asDouble() < r.asDouble() : l.asInt() < r.asInt());
            case gtr -> CBoolean.valueOf(isDouble ? l.asDouble() > r.asDouble() : l.asInt() > r.asInt());
            case geq -> CBoolean.valueOf(isDouble ? l.asDouble() >= r.asDouble() : l.asInt() >= r.asInt());
            case leq -> CBoolean.valueOf(isDouble ? l.asDouble() <= r.asDouble() : l.asInt() <= r.asInt());
            case equ, neq -> CBoolean.valueOf((operator == equ) == l.contentEquals(r));
            case feq, fne -> CBoolean.valueOf((operator == feq) == l.equals(r));
            case add -> {
                if (!l.getType().isNumber() || !r.getType().isNumber()) {
                    yield CString.valueOf(l.asString() + r.asString());
                } else
                    yield isDouble ? CDouble.valueOf(l.asDouble() + r.asDouble()) : CInt.valueOf(l.asInt() + r.asInt());
            }
            case sub -> isDouble ? CDouble.valueOf(l.asDouble() - r.asDouble()) : CInt.valueOf(l.asInt() - r.asInt());
            case div -> CDouble.valueOf(l.asDouble() / r.asDouble());
            case mul -> isDouble ? CDouble.valueOf(l.asDouble() * r.asDouble()) : CInt.valueOf(l.asInt() * r.asInt());
            case mod -> isDouble ? CDouble.valueOf(l.asDouble() % r.asDouble()) : CInt.valueOf(l.asInt() % r.asInt());
            case and -> CInt.valueOf(l.asInt() & r.asInt());
            case lsh -> CInt.valueOf(l.asInt() << r.asInt());
            case or -> CInt.valueOf(l.asInt() | r.asInt());
            case rsh -> CInt.valueOf(l.asInt() >> r.asInt());
            case rsh_unsigned -> CInt.valueOf(l.asInt() >>> r.asInt());
            case xor -> CInt.valueOf(l.asInt() ^ r.asInt());
            default -> throw OperationDone.NEVER;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Binary binary = (Binary) o;

        if (operator != binary.operator) return false;
        if (!left.equals(binary.left)) return false;
		return right.equals(binary.right);
	}

    @Override
    public int hashCode() {
        int result = operator;
        result = 31 * result + left.hashCode();
        result = 31 * result + right.hashCode();
        return result;
    }
}
