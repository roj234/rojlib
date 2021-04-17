package roj.kscript.type;

import roj.collect.MyHashSet;
import roj.collect.ReuseStack;
import roj.kscript.Arguments;
import roj.kscript.api.IGettable;
import roj.kscript.ast.ASTree;
import roj.kscript.func.KFuncAST;
import roj.kscript.func.KFunction;
import roj.kscript.util.RegionBuilder;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 作用域构建器
 *
 * @author solo6975
 * @since 2020/10/17 23:52
 */
public class ContextPrimer extends Context {
    private RegionBuilder builder = new RegionBuilder();
    private List<Context> children = Collections.emptyList();

    private ReuseStack<Set<String>> created = new ReuseStack<>();

    public ContextPrimer(ContextPrimer parent) {
        super(null);
        this.parent = parent;
    }

    public void enterRegion(int id) {
        this.builder.offset(id);
    }

    public boolean exists(String name) {
        return builder.variableExists(name) || (parent != null && ((ContextPrimer) parent).exists(name));
    }

    public Context init() {
        Context ctx = new Context(this.parent, builder.build());
        if (this.parent != null)
            ((ContextPrimer) this.parent).registerBuildHandler(ctx);
        for (Context primer : children) {
            primer.parent = ctx;
        }
        this.builder = null;
        return ctx;
    }

    public KType getNullable(String keys) {
        return super.getPrototyped(keys, null);
    }

    // region 作用域

    public void define(String key) {
        define(key, null);
    }

    public void define(String key, KType val) {
        map.put(key, val);
        builder.addVariable(key, val);
        if (!created.isEmpty())
            created.last().add(key);
    }

    public void delete(String key) {
        builder.removeVariable(key);
        if (!created.isEmpty())
            if(!created.last().remove(key)) {
                throw new IllegalStateException("此函数未在此处定义");
            }
    }

    private void registerBuildHandler(Context primer) {
        if (children == Collections.EMPTY_LIST) {
            children = new LinkedList<>();
        }
        children.add(primer);
    }

    public void recordCreation() {
        created.push(new MyHashSet<>());
    }

    /**
     * 在begin中添加的所有变量将被删除
     */
    public void restoreCreation(ASTree tree) {
        enterRegion(tree.NextRegion());
        for (String name : created.pop()) {
            builder.removeVariable(name);
        }
        enterRegion(tree.NextRegion());
    }

    // endregion
    // region Closure函数

    public void setFinalCopy(String name) {
        KFunction fun = (KFunction) builder.getValue(name);
        if (fun instanceof KFuncAST) {
            builder.addVariable(name, new Closure((KFuncAST) fun));
        } else {
            throw new IllegalArgumentException("Function is not AST, " + fun);
        }
    }

    private static final class Closure extends KFunction {
        private final KFuncAST orig;

        Closure(KFuncAST func) {
            this.orig = func;
        }

        @Override
        public KType invoke(@Nonnull IGettable $this, Arguments param) {
            return null;
        }

        @Override
        public String getClassName() {
            return null;
        }

        @Override
        public KType copy() {
            return new KFuncAST(orig);
        }
    }
    // endregion
}
