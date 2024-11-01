package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.collect.MyHashMap;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;
import roj.plugins.kscript.func.Constants;
import roj.plugins.kscript.func.KSObject;

import java.util.Collections;
import java.util.Map;

/**
 * 定义Object{}
 * @author Roj233
 * @since 2020/10/15 22:47
 */
final class NewMap implements ExprNode {
    public static final NewMap EMPTY = new NewMap(Collections.emptyMap());

    private final Map<String, ExprNode> expr;
    private final Map<String, CEntry> constant;

    public NewMap(Map<String, ExprNode> args) {
        this.expr = args;
        this.constant = new MyHashMap<>();
    }

    @Override
    public String toString() {return "Object{" + "expr=" + expr + ", object=" + constant + '}';}

    @Override
    public @NotNull ExprNode resolve() {
        for (var itr = expr.entrySet().iterator(); itr.hasNext(); ) {
            var entry = itr.next();

            var node = entry.getValue().resolve();
            if (node.isConstant()) {
                constant.put(entry.getKey(), node.toConstant());
                itr.remove();
            } else {
                entry.setValue(node);
            }
        }
        return this;
    }

    @Override public boolean isConstant() {return expr.isEmpty();}
    @Override
    public CEntry toConstant() {
        if (!isConstant()) return ExprNode.super.toConstant();

        var map = new KSObject(Constants.OBJECT);
        map.raw().putAll(constant);
        return map;
    }

    @Override
    public CEntry eval(CMap ctx) {
        var map = new MyHashMap<>(constant);
        if(!expr.isEmpty()) {
            for (var entry : expr.entrySet()) {
                ExprNode node = entry.getValue();
                if (!node.evalSpread(ctx, map)) map.put(entry.getKey(), node.eval(ctx));
            }
        }

        // TODO a deep copy for that
        /*var out = new CopyOf();
        map.accept(out);
        return out.get();*/

        return new KSObject(map, Constants.OBJECT);
    }
    @Override
    public void compile(KCompiler tree, boolean noRet) {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        NewMap map = (NewMap) o;

        if (expr != null ? !expr.equals(map.expr) : map.expr != null) return false;
		return constant.equals(map.constant);
	}

    @Override
    public int hashCode() {
        int result = expr != null ? expr.hashCode() : 0;
        result = 31 * result + constant.hashCode();
        return result;
    }
}
