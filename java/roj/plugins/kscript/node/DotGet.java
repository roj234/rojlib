package roj.plugins.kscript.node;

import org.jetbrains.annotations.NotNull;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.config.data.CMap;
import roj.plugins.kscript.KCompiler;
import roj.text.TextUtil;

import java.util.List;

/**
 * 链式取值赋值
 * @author Roj233
 * @since 2020/10/13 22:17
 */
final class DotGet implements VarNode {
    private ExprNode parent;
    private final List<String> names = new SimpleList<>();
    private boolean delete;

    public DotGet(ExprNode parent, String name) {
        this.parent = parent;
        this.names.add(name);
    }
    void add(String name) {names.add(name);}

    @Override public String toString() {return String.valueOf(parent) + '.' + TextUtil.join(names, ".");}

    @NotNull
    @Override
    public ExprNode resolve() {parent = parent.resolve();return this;}

	public boolean setDeletion() {return delete = true;}

    @Override public CEntry eval(CMap ctx) {
        var map = parent.eval(ctx).asMap();
        for (int i = 0; i < names.size(); i++) {
            var name = names.get(i);
            if (++i == names.size()) {
                return map.get(name);
            }
            map = map.getMap(name);
        }
        return map;
    }
    @Override public void evalStore(CMap ctx, CEntry val) {
        var map = parent.eval(ctx).asMap();
		for (int i = 0; i < names.size(); i++) {
			var name = names.get(i);
            if (++i == names.size()) {
                map.put(name, val);
                return;
            }
			map = map.getMap(name);
		}
    }

    @Override
    public void compile(KCompiler tree, boolean noRet) {
        if(noRet && !delete)
            throw new NotStatementException();

        parent.compile(tree, false);
    }
    @Override
    public void compileLoad(KCompiler tree) {
        parent.compile(tree, false);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        DotGet get = (DotGet) o;

        if (delete != get.delete) return false;
        if (!parent.equals(get.parent)) return false;
		return names.equals(get.names);
	}

    @Override
    public int hashCode() {
        int result = parent.hashCode();
        result = 31 * result + names.hashCode();
        result = 31 * result + (delete ? 1 : 0);
        return result;
    }
}
