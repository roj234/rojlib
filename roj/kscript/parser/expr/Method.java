package roj.kscript.parser.expr;

import roj.kscript.Arguments;
import roj.kscript.api.MethodsAPI;
import roj.kscript.ast.ASTree;
import roj.kscript.func.KFunction;
import roj.kscript.type.KNull;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 操作符 - 调用方法
 *
 * @author Roj233
 * @since 2020/10/13 22:17
 */
public final class Method implements Expression {
    Expression func;
    public final List<Expression> args;
    public byte flag;

    public Method(Expression line, List<Expression> args, boolean isNew) {
        this.func = line;
        this.args = args;
        this.flag = (byte) (isNew ? 1 : 0);
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Method))
            return false;
        Method method = (Method) left;
        return method.func.isEqual(func) && (method.flag & 1) == (flag & 1) && ArrayDef.arrayEq(args, method.args);
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        this.func.write(tree, false);
        compressArg();
        for (int i = 0; i < args.size(); i++) {
            Expression expr = args.get(i);
            expr.write(tree, false);
        }

        if ((flag & 1) != 0) {
            tree.New(args.size(), noRet);
        } else {
            tree.Invoke(args.size(), noRet);
        }
    }

    private void compressArg() {
        if((flag & 2) == 0) {
            List<Expression> args = this.args;
            for (int i = 0; i < args.size(); i++) {
                Expression cp = args.get(i).compress();
                if(!cp.isConstant())
                    flag |= 4;
                args.set(i, cp);
            }
            flag |= 2;
        }
    }

    @Nonnull
    @Override
    public Expression compress() {
        func = func.compress();
        compressArg();

        CharSequence sb = getFuncPath(func);
        if(sb == null)
            return this;
        if((flag & 4) == 0) {
            KType pc = MethodsAPI.preCompute(sb, getCst());
            if (pc != null) {
                return Constant.valueOf(pc);
            }
        }

        Expression expr = MethodsAPI.getDedicated(sb, this);
        return expr == null ? this : expr;
    }

    private static CharSequence getFuncPath(Expression func) {
        if (!(func instanceof Field)) {
            return null;
        }
        Field f = (Field) func;

        List<String> fieldDot = new ArrayList<>();
        fieldDot.add(f.name);
        while (f.parent instanceof Field) {
            f = (Field) f.parent;
            fieldDot.add(f.name);
        }

        if (!(f instanceof Variable)) {
            return null;
        }

        Collections.reverse(fieldDot);
        StringBuilder sb = new StringBuilder(fieldDot.size() * 5);
        for (String s : fieldDot) {
            sb.append(s).append('.');
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb;
    }

    @Override
    public KType compute(Map<String, KType> param) {
        List<KType> vals = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            vals.add(args.get(i).compute(param));
        }

        CharSequence sb = getFuncPath(func);
        KType nf = MethodsAPI.preCompute(sb, vals);
        if(nf != null)
            return nf;

        try {
            KFunction func = this.func.compute(param).asFunction();
            return func.invoke(KNull.NULL, new Arguments(vals));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a simple expression");
        }
    }

    @Override
    public byte type() {
        if((flag & 4) != 0)
            return -1;

        List<KType> exprs = getCst();

        KType res = MethodsAPI.preCompute(getFuncPath(func), getCst());

        return res == null ? -1 : Constant.typeOf(res);
    }

    private List<KType> getCst() {
        List<KType> exprs = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            exprs.add(args.get(i).asCst().val());
        }
        return exprs;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if ((flag & 1) != 0) {
            sb.append("new ");
        }
        sb.append(func.toString()).append('(');
        for (Expression expr : args) {
            sb.append(expr).append(',');
        }
        if (!args.isEmpty()) {
            sb.deleteCharAt(sb.length() - 1);
        }

        return sb.append(')').toString();
    }
}
