package roj.kscript.parser.expr;

import roj.kscript.Arguments;
import roj.kscript.api.API;
import roj.kscript.api.IObject;
import roj.kscript.api.NativeMethod;
import roj.kscript.ast.ASTree;
import roj.kscript.func.KFunction;
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
    final List<Expression> args;
    final boolean isNew;

    public Method(Expression line, List<Expression> args, boolean isNew) {
        this.func = line;
        this.args = args;
        this.isNew = isNew;
    }

    @Override
    public boolean isEqual(Expression left) {
        if (this == left)
            return true;
        if (!(left instanceof Method))
            return false;
        Method method = (Method) left;
        return method.func.isEqual(func) && method.isNew == isNew && ArrayDefine.arrayEq(args, method.args);
    }

    @Override
    public void write(ASTree tree, boolean noRet) {
        this.func.write(tree, false);
        for (Expression expr : args) {
            expr.write(tree, false);
        }
        if (isNew) {
            tree.New(args.size(), noRet);
        } else {
            tree.Invoke(args.size(), noRet);
        }

    }

    @Nonnull
    @Override
    public Expression compress() {
        func = func.compress();

        final List<Expression> args = this.args;
        for (int i = 0; i < args.size(); i++) {
            args.set(i, args.get(i).compress());
        }

        if (!API.PRECOMPILE_NATIVE)
            return this;

        List<KType> exprs = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            Expression expr = args.get(i);
            if (!expr.isConstant()) {
                return this;
            }
            exprs.add(expr.asCst().val());
        }

        KType res = getNativeFunction(func, exprs);
        return res == null ? this : Constant.valueOf(res);
    }

    private static KType getNativeFunction(Expression func, List<KType> args) {
        if (!API.PRECOMPILE_NATIVE)
            return null;

        if (!(func instanceof Field)) {
            return null;
        }
        Field f = (Field) func;
        List<String> fieldNames = new ArrayList<>();

        fieldNames.add(f.name);
        while (f.parent instanceof Field) {
            f = (Field) f.parent;
            fieldNames.add(f.name);
        }

        if (!(f instanceof Variable)) {
            return null;
        }

        Collections.reverse(fieldNames);
        StringBuilder sb = new StringBuilder(fieldNames.size() * 5);
        for (String s : fieldNames) {
            sb.append(s).append('.');
        }
        sb.deleteCharAt(sb.length() - 1);
        return API.NATIVE_METHODS.getOrDefault(sb, NativeMethod.NULL).handle(args);
    }

    @Override
    public KType compute(Map<String, KType> parameters, IObject thisContext) {
        List<KType> exprs = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            exprs.add(args.get(i).compute(parameters, thisContext));
        }

        KType nf = getNativeFunction(func, exprs);
        if(nf != null)
            return nf;

        try {
            KFunction func = this.func.compute(parameters, thisContext).asFunction();
            return func.invoke(thisContext, new Arguments(exprs));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a simple expression");
        }
    }

    @Override
    public byte type() {
        if (!API.PRECOMPILE_NATIVE)
            return -1;

        List<KType> exprs = new ArrayList<>(args.size());
        for (int i = 0; i < args.size(); i++) {
            Expression expr = args.get(i);
            if (!expr.isConstant()) {
                return -1;
            }
            exprs.add(expr.asCst().val());
        }

        KType res = getNativeFunction(func, exprs);
        return res == null ? -1 : Constant.typeOf(res);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (isNew) {
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
